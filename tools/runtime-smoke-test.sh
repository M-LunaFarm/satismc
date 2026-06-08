#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${SATIS_SMOKE_DIR:-/tmp/satis-runtime-smoke}"
PAPER_VERSION="${PAPER_VERSION:-1.21.1}"
MIN_READY_SECONDS="${MIN_READY_SECONDS:-2}"
MAX_START_SECONDS="${MAX_START_SECONDS:-180}"

log() {
  printf '[satis-smoke] %s\n' "$*"
}

paper_url() {
  python3 - "$PAPER_VERSION" <<'PY'
import json
import sys
import urllib.request

version = sys.argv[1]
with urllib.request.urlopen(f"https://api.papermc.io/v2/projects/paper/versions/{version}/builds", timeout=30) as response:
    data = json.load(response)
build = data["builds"][-1]["build"]
print(f"https://api.papermc.io/v2/projects/paper/versions/{version}/builds/{build}/downloads/paper-{version}-{build}.jar")
PY
}

wait_for_log() {
  local log_file="$1"
  local pattern="$2"
  local timeout_seconds="$3"
  local start
  start="$(date +%s)"
  while true; do
    if [[ -f "$log_file" ]] && rg -q "$pattern" "$log_file"; then
      return 0
    fi
    if (( "$(date +%s)" - start >= timeout_seconds )); then
      return 1
    fi
    sleep 1
  done
}

stop_server() {
  local session_input="$1"
  if [[ -p "$session_input" ]]; then
    printf 'stop\n' >"$session_input" || true
  fi
}

run_server_case() {
  local case_name="$1"
  local expect_pattern="$2"
  local allow_dependency_error="${3:-false}"
  local server_dir="$WORK_DIR/$case_name/server"
  local log_file="$server_dir/logs/latest.log"
  local input_pipe="$WORK_DIR/$case_name/server.in"

  rm -rf "$WORK_DIR/$case_name"
  mkdir -p "$server_dir/plugins"
  printf 'eula=true\n' >"$server_dir/eula.txt"
  cp "$WORK_DIR/paper.jar" "$server_dir/paper.jar"
  cp "$ROOT_DIR/build/libs/SatisSkyFactory-1.0.0.jar" "$server_dir/plugins/SatisSkyFactory-1.0.0.jar"

  if [[ "$case_name" == "with-skyblock" ]]; then
    cp "$WORK_DIR/SuperiorSkyblock2Stub.jar" "$server_dir/plugins/SuperiorSkyblock2Stub.jar"
  fi

  rm -f "$input_pipe"
  mkfifo "$input_pipe"
  log "starting Paper case: $case_name"
  (cd "$server_dir" && java -Xms512M -Xmx1024M -jar paper.jar nogui <"$input_pipe" >"$server_dir/console.log" 2>&1) &
  local pid=$!

  exec 9>"$input_pipe"
  if ! wait_for_log "$log_file" "$expect_pattern" "$MAX_START_SECONDS"; then
    stop_server "$input_pipe"
    wait "$pid" || true
    log "case failed: $case_name"
    [[ -f "$server_dir/console.log" ]] && tail -200 "$server_dir/console.log"
    [[ -f "$log_file" ]] && tail -200 "$log_file"
    exit 1
  fi

  sleep "$MIN_READY_SECONDS"
  printf 'stop\n' >&9
  wait "$pid"
  exec 9>&-

  local severe_pattern="NoClassDefFoundError|Could not pass event|ERROR|SEVERE"
  if [[ "$allow_dependency_error" == "true" ]]; then
    severe_pattern="NoClassDefFoundError|Could not pass event"
  fi
  if rg -n "$severe_pattern" "$log_file" | rg -v "Detected setBlock in a far chunk"; then
    log "unexpected severe log entries in $case_name"
    exit 1
  fi
  log "case passed: $case_name"
}

build_skyblock_stub() {
  local home_dir
  home_dir="${HOME:-/root}"
  local paper_api_jar
  paper_api_jar="$(find "$home_dir/.gradle/caches" -name "paper-api-${PAPER_VERSION}-R0.1-SNAPSHOT.jar" | head -1)"
  if [[ -z "$paper_api_jar" ]]; then
    log "Paper API jar was not found in Gradle cache"
    exit 1
  fi

  mkdir -p "$WORK_DIR/stub/src/com/bgsoftware/superiorskyblock/api" \
    "$WORK_DIR/stub/src/kr/seungmin/teststub" \
    "$WORK_DIR/stub/classes"

  cat >"$WORK_DIR/stub/src/kr/seungmin/teststub/SuperiorSkyblock2StubPlugin.java" <<'JAVA'
package kr.seungmin.teststub;

import org.bukkit.plugin.java.JavaPlugin;

public final class SuperiorSkyblock2StubPlugin extends JavaPlugin {
}
JAVA

  cat >"$WORK_DIR/stub/src/com/bgsoftware/superiorskyblock/api/SuperiorSkyblockAPI.java" <<'JAVA'
package com.bgsoftware.superiorskyblock.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class SuperiorSkyblockAPI {
    private SuperiorSkyblockAPI() {
    }

    public static Object getIslandAt(Location location) {
        return null;
    }

    public static Object getIslandByUUID(UUID islandUuid) {
        return null;
    }

    public static Object getPlayer(Player player) {
        return null;
    }

    public static Object getPlayer(UUID playerUuid) {
        return null;
    }
}
JAVA

  cat >"$WORK_DIR/stub/plugin.yml" <<'YAML'
name: SuperiorSkyblock2
main: kr.seungmin.teststub.SuperiorSkyblock2StubPlugin
version: 0.0.1
api-version: "1.21"
YAML

  javac -cp "$paper_api_jar" -d "$WORK_DIR/stub/classes" $(find "$WORK_DIR/stub/src" -name '*.java')
  cp "$WORK_DIR/stub/plugin.yml" "$WORK_DIR/stub/classes/plugin.yml"
  jar cf "$WORK_DIR/SuperiorSkyblock2Stub.jar" -C "$WORK_DIR/stub/classes" .
}

main() {
  cd "$ROOT_DIR"
  ./gradlew build

  rm -rf "$WORK_DIR"
  mkdir -p "$WORK_DIR"
  local url
  url="$(paper_url)"
  log "downloading $url"
  python3 - "$url" "$WORK_DIR/paper.jar" <<'PY'
import sys
import urllib.request

urllib.request.urlretrieve(sys.argv[1], sys.argv[2])
PY

  build_skyblock_stub
  run_server_case "without-skyblock" "UnknownDependencyException|Unknown dependency SuperiorSkyblock2" true
  run_server_case "with-skyblock" "SatisSkyFactory enabled using Fallback economy"

  local data_db="$WORK_DIR/with-skyblock/server/plugins/SatisSkyFactory/data.db"
  if [[ ! -s "$data_db" ]]; then
    log "data.db was not created"
    exit 1
  fi
  python3 - "$data_db" <<'PY'
import sqlite3
import sys

con = sqlite3.connect(sys.argv[1])
tables = [row[0] for row in con.execute("select name from sqlite_master where type='table' order by name")]
version = con.execute("select version from schema_version").fetchone()[0]
con.close()
required = {
    "factory_islands", "machines", "virtual_inventories", "virtual_inventory_items",
    "resource_nodes", "contracts", "island_unlocks", "market_daily",
    "market_personal_daily", "ledger", "schema_version"
}
missing = sorted(required - set(tables))
print("TABLE_COUNT=" + str(len(tables)))
print("SCHEMA_VERSION=" + str(version))
if missing:
    print("MISSING_TABLES=" + ",".join(missing))
    raise SystemExit(1)
PY
  log "runtime smoke test passed"
}

main "$@"
