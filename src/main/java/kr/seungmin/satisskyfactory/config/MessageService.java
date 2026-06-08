package kr.seungmin.satisskyfactory.config;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public final class MessageService {
    private final ConfigService configs;

    public MessageService(ConfigService configs) {
        this.configs = configs;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(text(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String text = text(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        sender.sendMessage(text);
    }

    public String text(String key) {
        FileConfiguration messages = configs.file("messages.yml");
        String prefix = messages.getString("prefix", "");
        String body = messages.getString("messages." + key, key);
        return color(prefix + body);
    }

    public String raw(String key) {
        return color(configs.file("messages.yml").getString("messages." + key, key));
    }

    @SuppressWarnings("deprecation")
    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
