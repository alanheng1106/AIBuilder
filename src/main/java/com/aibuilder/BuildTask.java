package com.aibuilder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BuildTask extends BukkitRunnable {

    public static class BlockPlacement {
        public Integer x;
        public Integer y;
        public Integer z;
        public Integer x1;
        public Integer y1;
        public Integer z1;
        public Integer x2;
        public Integer y2;
        public Integer z2;
        public String type;
    }

    public static class SingleBlockPlacement {
        public int x;
        public int y;
        public int z;
        public String type;
    }

    private final AIBuilderPlugin plugin;
    private final UUID playerUUID;
    private final Location baseLocation;
    private final List<SingleBlockPlacement> placements;
    private final int blocksPerBatch;
    private final boolean effectsEnabled;

    private final List<BlockState> originalStates = new ArrayList<>();
    private int currentIndex = 0;
    private int placedCount = 0;

    public BuildTask(AIBuilderPlugin plugin, UUID playerUUID, Location baseLocation,
                     List<BlockPlacement> rawPlacements, int blocksPerBatch, boolean effectsEnabled) {
        this.plugin = plugin;
        this.playerUUID = playerUUID;
        this.baseLocation = baseLocation;
        this.placements = flattenPlacements(rawPlacements);
        this.blocksPerBatch = blocksPerBatch;
        this.effectsEnabled = effectsEnabled;
    }

    private List<SingleBlockPlacement> flattenPlacements(List<BlockPlacement> raw) {
        List<SingleBlockPlacement> flattened = new ArrayList<>();
        if (raw == null) return flattened;
        for (BlockPlacement p : raw) {
            int x1 = p.x1 != null ? p.x1 : (p.x != null ? p.x : 0);
            int y1 = p.y1 != null ? p.y1 : (p.y != null ? p.y : 0);
            int z1 = p.z1 != null ? p.z1 : (p.z != null ? p.z : 0);
            int x2 = p.x2 != null ? p.x2 : x1;
            int y2 = p.y2 != null ? p.y2 : y1;
            int z2 = p.z2 != null ? p.z2 : z1;

            int startX = Math.min(x1, x2);
            int endX = Math.max(x1, x2);
            int startY = Math.min(y1, y2);
            int endY = Math.max(y1, y2);
            int startZ = Math.min(z1, z2);
            int endZ = Math.max(z1, z2);

            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    for (int z = startZ; z <= endZ; z++) {
                        SingleBlockPlacement sp = new SingleBlockPlacement();
                        sp.x = x;
                        sp.y = y;
                        sp.z = z;
                        sp.type = p.type;
                        flattened.add(sp);
                    }
                }
            }
        }
        return flattened;
    }

    /**
     * Executes all placements instantly in a single tick.
     */
    public void executeInstant() {
        World world = baseLocation.getWorld();
        if (world == null) return;

        for (SingleBlockPlacement placement : placements) {
            placeBlock(world, placement);
        }

        plugin.setUndoHistory(playerUUID, originalStates);
        plugin.getQuotaManager().addUsedQuota(playerUUID, placedCount);
        
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            sendProgressBar(player, placements.size(), placements.size());
            player.sendMessage(plugin.getLanguageManager().getMessage("build-completed-instant", placedCount));
        }
    }

    @Override
    public void run() {
        World world = baseLocation.getWorld();
        if (world == null) {
            cancel();
            return;
        }

        int limit = Math.min(currentIndex + blocksPerBatch, placements.size());
        for (int i = currentIndex; i < limit; i++) {
            SingleBlockPlacement placement = placements.get(i);
            placeBlock(world, placement);
            currentIndex++;
        }

        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            sendProgressBar(player, currentIndex, placements.size());
        }

        // If finished
        if (currentIndex >= placements.size()) {
            plugin.setUndoHistory(playerUUID, originalStates);
            plugin.getQuotaManager().addUsedQuota(playerUUID, placedCount);
            if (player != null && player.isOnline()) {
                sendProgressBar(player, placements.size(), placements.size());
                player.sendMessage(plugin.getLanguageManager().getMessage("build-completed", placedCount));
            }
            cancel();
        }
    }

    private void sendProgressBar(Player player, int current, int total) {
        if (total <= 0) return;
        int percent = (current * 100) / total;
        int barLength = 20;
        int filledLength = (current * barLength) / total;

        StringBuilder bar = new StringBuilder(plugin.getLanguageManager().getMessage("progress-bar-title"));
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("█");
            } else {
                bar.append("§8░");
            }
        }
        bar.append("§a] §f").append(percent).append("% §7(").append(current).append("/").append(total).append(")");

        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(bar.toString()));
    }

    private void placeBlock(World world, SingleBlockPlacement placement) {
        Location loc = baseLocation.clone().add(placement.x, placement.y, placement.z);
        Block block = loc.getBlock();

        // Resolve material
        Material material = Material.matchMaterial(placement.type);
        if (material == null) {
            // Check for lowercase or standard clean up
            material = Material.matchMaterial(placement.type.toUpperCase());
        }

        if (material == null) {
            // Fallback: log and skip
            plugin.getLogger().warning("Skipping unknown material type: " + placement.type);
            return;
        }

        // Save original block state if we haven't saved this location yet (avoid duplicates in undo history)
        boolean alreadySaved = false;
        for (BlockState state : originalStates) {
            if (state.getLocation().equals(loc)) {
                alreadySaved = true;
                break;
            }
        }
        if (!alreadySaved) {
            originalStates.add(block.getState());
        }

        // Update block type
        block.setType(material, false);
        placedCount++;

        // Effects
        if (effectsEnabled) {
            loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0.5, 0.5, 0.5), 3, 0.1, 0.1, 0.1, 0.02);
            // Default place sound
            loc.getWorld().playSound(loc, Sound.BLOCK_STONE_PLACE, 0.3f, 1.0f);
        }
    }
}
