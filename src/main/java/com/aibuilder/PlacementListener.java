package com.aibuilder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class PlacementListener implements Listener {

    private final AIBuilderPlugin plugin;

    public PlacementListener(AIBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !"§a§lAI Build Placer".equals(meta.getDisplayName())) return;

        // Cancel standard block interaction
        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        List<BuildTask.BlockPlacement> placements = plugin.getPendingBuild(uuid);
        if (placements == null || placements.isEmpty()) {
            player.sendMessage("§c[AI Builder] You do not have any pending designs. Run /aibuild <prompt> first!");
            return;
        }

        // Consume the placer item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        // Clear the pending build from the map
        plugin.clearPendingBuild(uuid);

        // Get placement target location (exactly on top of clicked block)
        if (event.getClickedBlock() == null) return;
        Location targetLoc = event.getClickedBlock().getLocation().add(0, 1, 0);

        // Play feedback sound
        player.playSound(targetLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);

        plugin.getLogger().info(player.getName() + " placed an AI build (" + placements.size() + " blocks) at " + targetLoc.getBlockX() + ", " + targetLoc.getBlockY() + ", " + targetLoc.getBlockZ());

        // Load configuration and execute build task
        int delayBetweenBatches = plugin.getConfig().getInt("delay-between-batches", 1);
        int blocksPerBatch = plugin.getConfig().getInt("blocks-per-batch", 5);
        boolean effectsEnabled = plugin.getConfig().getBoolean("effects-enabled", true);

        BuildTask buildTask = new BuildTask(plugin, uuid, targetLoc, placements, blocksPerBatch, effectsEnabled);
        
        if (delayBetweenBatches <= 0) {
            buildTask.executeInstant();
        } else {
            player.sendMessage("§a[AI Builder] Placement confirmed! Commencing build (" + placements.size() + " blocks)...");
            buildTask.runTaskTimer(plugin, 0L, delayBetweenBatches);
        }
    }
}
