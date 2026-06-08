package kr.seungmin.satisskyfactory.model;

import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;

public record FactoryContext(SuperiorSkyblockHook.IslandRef islandRef, FactoryIsland factoryIsland) {
}
