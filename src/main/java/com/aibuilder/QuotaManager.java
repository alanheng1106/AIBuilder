package com.aibuilder;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Level;

public class QuotaManager {
    private final AIBuilderPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public QuotaManager(AIBuilderPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quota.yml");
        load();
    }

    /**
     * Loads the quota data from quota.yml. Creates the file if it does not exist.
     */
    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create quota.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Saves the quota data to disk.
     */
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save quota.yml", e);
        }
    }

    private String getTodayString() {
        return LocalDate.now().toString();
    }

    /**
     * Get the number of blocks placed by a player today. Resets automatically on a new day.
     */
    public int getUsedQuota(UUID uuid) {
        String path = "players." + uuid.toString();
        if (!config.contains(path)) {
            return 0;
        }
        String date = config.getString(path + ".date", "");
        if (!getTodayString().equals(date)) {
            // Reset count if the day has changed
            return 0;
        }
        return config.getInt(path + ".placed", 0);
    }

    /**
     * Increments the daily placement count for a player.
     */
    public void addUsedQuota(UUID uuid, int amount) {
        String path = "players." + uuid.toString();
        int current = getUsedQuota(uuid);
        config.set(path + ".date", getTodayString());
        config.set(path + ".placed", Math.max(0, current + amount));
        save();
    }
}
