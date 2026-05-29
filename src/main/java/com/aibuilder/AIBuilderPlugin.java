package com.aibuilder;

import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AIBuilderPlugin extends JavaPlugin {

    // Map to keep track of each player's last build history for the undo function
    private final Map<UUID, List<BlockState>> buildHistory = new ConcurrentHashMap<>();
    // Map to store player's pending designs before placement
    private final Map<UUID, List<BuildTask.BlockPlacement>> pendingBuilds = new ConcurrentHashMap<>();
    private QuotaManager quotaManager;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        // Save default config.yml if not already present
        saveDefaultConfig();

        // Initialize Language Manager
        languageManager = new LanguageManager(this);

        // Initialize Quota Manager
        quotaManager = new QuotaManager(this);

        // Register commands
        AIBuildCommand commandExecutor = new AIBuildCommand(this);
        if (getCommand("aibuild") != null) {
            getCommand("aibuild").setExecutor(commandExecutor);
        }
        if (getCommand("aiundo") != null) {
            getCommand("aiundo").setExecutor(commandExecutor);
        }
        if (getCommand("aireload") != null) {
            getCommand("aireload").setExecutor(commandExecutor);
        }
        if (getCommand("aiquota") != null) {
            getCommand("aiquota").setExecutor(commandExecutor);
        }

        // Register listener
        getServer().getPluginManager().registerEvents(new PlacementListener(this), this);

        getLogger().info("AIBuilder Plugin has been enabled!");
    }

    public QuotaManager getQuotaManager() {
        return quotaManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (languageManager != null) {
            languageManager.load();
        }
    }

    public void setPendingBuild(UUID uuid, List<BuildTask.BlockPlacement> placements) {
        pendingBuilds.put(uuid, placements);
    }

    public List<BuildTask.BlockPlacement> getPendingBuild(UUID uuid) {
        return pendingBuilds.get(uuid);
    }

    public void clearPendingBuild(UUID uuid) {
        pendingBuilds.remove(uuid);
    }

    @Override
    public void onDisable() {
        // Clear history map on plugin disable to avoid references leak
        buildHistory.clear();
        getLogger().info("AIBuilder Plugin has been disabled!");
    }

    /**
     * Store block states for undoing a player's last build.
     */
    public void setUndoHistory(UUID playerId, List<BlockState> states) {
        buildHistory.put(playerId, states);
    }

    /**
     * Retrieve the block states for undoing a player's last build.
     */
    public List<BlockState> getUndoHistory(UUID playerId) {
        return buildHistory.get(playerId);
    }

    /**
     * Clear the undo history of a player.
     */
    public void clearUndoHistory(UUID playerId) {
        buildHistory.remove(playerId);
    }
}
