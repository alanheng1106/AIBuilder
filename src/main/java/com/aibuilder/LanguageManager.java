package com.aibuilder;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.MessageFormat;

public class LanguageManager {

    private final AIBuilderPlugin plugin;
    private FileConfiguration messages;
    private FileConfiguration defaultMessages;

    public LanguageManager(AIBuilderPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        // Ensure parent directory exists
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        // Extract default resources if they do not exist
        try {
            plugin.saveResource("lang/messages_en.yml", false);
        } catch (Exception ignored) {}
        try {
            plugin.saveResource("lang/messages_zh.yml", false);
        } catch (Exception ignored) {}

        String lang = plugin.getConfig().getString("lang", "en").toLowerCase();
        File langFile = new File(plugin.getDataFolder(), "lang/messages_" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file lang/messages_" + lang + ".yml not found! Defaulting to English.");
            langFile = new File(plugin.getDataFolder(), "lang/messages_en.yml");
        }

        messages = YamlConfiguration.loadConfiguration(langFile);

        // Load fallback (English)
        File defaultFile = new File(plugin.getDataFolder(), "lang/messages_en.yml");
        if (defaultFile.exists()) {
            defaultMessages = YamlConfiguration.loadConfiguration(defaultFile);
        } else {
            defaultMessages = new YamlConfiguration();
        }
    }

    public String getRawMessage(String key, Object... args) {
        String msg = messages.getString(key);
        if (msg == null) {
            msg = defaultMessages.getString(key, "Missing key: " + key);
        }

        if (args != null && args.length > 0) {
            try {
                msg = MessageFormat.format(msg, args);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to format message key '" + key + "': " + e.getMessage());
            }
        }
        return msg;
    }

    public String getMessage(String key, Object... args) {
        return ChatColor.translateAlternateColorCodes('&', getRawMessage(key, args));
    }
}
