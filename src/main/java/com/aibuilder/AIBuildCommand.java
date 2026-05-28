package com.aibuilder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;

public class AIBuildCommand implements CommandExecutor {

    private final AIBuilderPlugin plugin;
    private final Gson gson = new Gson();

    public AIBuildCommand(AIBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if ("aireload".equalsIgnoreCase(command.getName())) {
            if (!sender.hasPermission("aibuilder.command.reload")) {
                sender.sendMessage("§cYou do not have permission to execute this command.");
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage("§a[AI Builder] Configuration reloaded successfully.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can execute this command.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if ("aiquota".equalsIgnoreCase(command.getName())) {
            if (!player.hasPermission("aibuilder.command.quota")) {
                player.sendMessage("§cYou do not have permission to execute this command.");
                return true;
            }
            int used = plugin.getQuotaManager().getUsedQuota(uuid);
            int limit = plugin.getConfig().getInt("daily-block-quota", 10000);

            player.sendMessage("§e=== [AI Builder Daily Quota] ===");
            if (limit == -1) {
                player.sendMessage("§7- Daily Limit: §aUnlimited");
            } else {
                player.sendMessage("§7- Daily Limit: §a" + String.format("%,d", limit) + " blocks");
            }
            player.sendMessage("§7- Used Today: §f" + String.format("%,d", used) + " blocks");
            if (limit != -1) {
                int remaining = Math.max(0, limit - used);
                player.sendMessage("§7- Remaining: §e" + String.format("%,d", remaining) + " blocks");
            }
            return true;
        }

        if ("aiundo".equalsIgnoreCase(command.getName())) {
            if (!player.hasPermission("aibuilder.command.undo")) {
                player.sendMessage("§cYou do not have permission to execute this command.");
                return true;
            }

            List<BlockState> history = plugin.getUndoHistory(uuid);
            if (history == null || history.isEmpty()) {
                player.sendMessage("§c[AI Builder] You do not have any recent build to undo.");
                return true;
            }

            // Restore blocks in the main thread
            int count = 0;
            for (BlockState state : history) {
                state.update(true, false);
                count++;
            }
            plugin.clearUndoHistory(uuid);

            // Refund daily quota
            plugin.getQuotaManager().addUsedQuota(uuid, -count);

            player.sendMessage("§a[AI Builder] Undo completed! Restored " + count + " blocks. Daily quota refunded.");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            return true;
        }

        if ("aibuild".equalsIgnoreCase(command.getName())) {
            if (!player.hasPermission("aibuilder.command.build")) {
                player.sendMessage("§cYou do not have permission to execute this command.");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage("§cUsage: /aibuild [small/medium/large] <prompt of structure to build>");
                return true;
            }

            String scale;
            String prompt;
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("small") || firstArg.equals("medium") || firstArg.equals("large")) {
                scale = firstArg;
                if (args.length == 1) {
                    player.sendMessage("§cUsage: /aibuild [" + scale + "] <prompt of structure to build>");
                    return true;
                }
                String[] promptArgs = new String[args.length - 1];
                System.arraycopy(args, 1, promptArgs, 0, args.length - 1);
                prompt = String.join(" ", promptArgs);
            } else {
                // No scale given — show interactive clickable size selector
                String rawPrompt = String.join(" ", args);
                sendSizeMenu(player, rawPrompt);
                return true;
            }

            // Fetch configuration values
            String provider = plugin.getConfig().getString("provider", "gemini");
            String apiKey = plugin.getConfig().getString("api-key", "");
            String model = plugin.getConfig().getString("model", "gemini-1.5-flash");
            String apiUrl = plugin.getConfig().getString("api-url", "");
            int delayBetweenBatches = plugin.getConfig().getInt("delay-between-batches", 1);
            int blocksPerBatch = plugin.getConfig().getInt("blocks-per-batch", 5);
            int maxDimension = plugin.getConfig().getInt("scales." + scale + ".max-dimension", 30);
            int maxBlocks = plugin.getConfig().getInt("scales." + scale + ".max-blocks", 3000);
            boolean effectsEnabled = plugin.getConfig().getBoolean("effects-enabled", true);
            int dailyQuota = plugin.getConfig().getInt("daily-block-quota", 10000);

            // Pre-check daily quota (bypassed for OP players)
            int usedBlocks = plugin.getQuotaManager().getUsedQuota(uuid);
            if (!player.isOp() && dailyQuota != -1 && usedBlocks >= dailyQuota) {
                player.sendMessage("§c[AI Builder] You have exceeded your daily quota of "
                        + String.format("%,d", dailyQuota) + " blocks today.");
                return true;
            }

            // Fallback to environment variables if API key is blank
            if (apiKey == null || apiKey.isBlank()) {
                if ("openai".equalsIgnoreCase(provider)) {
                    apiKey = System.getenv("OPENAI_API_KEY");
                } else {
                    apiKey = System.getenv("GEMINI_API_KEY");
                    if (apiKey == null || apiKey.isBlank()) {
                        apiKey = System.getenv("GEMINI_API_SECRET"); // alternate variable
                    }
                }
            }

            if (apiKey == null || apiKey.isBlank()) {
                player.sendMessage(
                        "§c[AI Builder] API Key is not configured! Please configure 'api-key' in config.yml or set the corresponding environment variable (GEMINI_API_KEY / OPENAI_API_KEY).");
                return true;
            }

            // Get base location at player's current block
            Location baseLocation = player.getLocation().getBlock().getLocation();

            player.sendMessage("§e[AI Builder] Requesting the AI to design a §a" + scale + "§e structure (max "
                    + maxDimension + "x" + maxDimension + "x" + maxDimension + "): §f" + prompt + "§e... Please wait.");

            // Start animated loading action bar while asking
            org.bukkit.scheduler.BukkitTask loadingTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                int tick = 0;
                final String[] frames = {
                        "§eAI is designing §a[=   ]",
                        "§eAI is designing §a[ =  ]",
                        "§eAI is designing §a[  = ]",
                        "§eAI is designing §a[   =]",
                        "§eAI is designing §a[  = ]",
                        "§eAI is designing §a[ =  ]"
                };

                @Override
                public void run() {
                    player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize(frames[tick % frames.length]));
                    tick++;
                }
            }, 0L, 5L);

            AIClient client = new AIClient(plugin.getLogger(), provider, apiKey, model, apiUrl, maxBlocks,
                    maxDimension);

            client.generateStructure(prompt, scale).handle((jsonResult, throwable) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loadingTask.cancel();
                    // Clear the action bar
                    player.sendActionBar(net.kyori.adventure.text.Component.empty());
                });

                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c[AI Builder] Error calling AI API: " + cause.getMessage());
                    });
                    return null;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Type type = new TypeToken<List<BuildTask.BlockPlacement>>() {
                        }.getType();
                        List<BuildTask.BlockPlacement> placements = gson.fromJson(jsonResult, type);

                        if (placements == null || placements.isEmpty()) {
                            player.sendMessage("§c[AI Builder] AI returned an empty structural design.");
                            return;
                        }

                        if (placements.size() > maxBlocks) {
                            player.sendMessage("§c[AI Builder] AI design contains too many blocks (" + placements.size()
                                    + " blocks). Limit is " + maxBlocks + ".");
                            return;
                        }

                        // Post-check quota with parsed block size (bypassed for OP players)
                        int currentUsed = plugin.getQuotaManager().getUsedQuota(uuid);
                        if (!player.isOp() && dailyQuota != -1 && currentUsed + placements.size() > dailyQuota) {
                            player.sendMessage("§c[AI Builder] This build requires " + placements.size()
                                    + " blocks, but you only have " + Math.max(0, dailyQuota - currentUsed)
                                    + " blocks remaining in your daily quota today.");
                            return;
                        }

                        // Store pending build
                        plugin.setPendingBuild(uuid, placements);

                        // Give placement stick placer item
                        org.bukkit.inventory.ItemStack placer = new org.bukkit.inventory.ItemStack(
                                org.bukkit.Material.STICK);
                        org.bukkit.inventory.meta.ItemMeta meta = placer.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName("§a§lAI Build Placer");
                            java.util.List<String> lore = new java.util.ArrayList<>();
                            lore.add("§7Right-click a block to place");
                            lore.add("§7your designed structure!");
                            meta.setLore(lore);
                            placer.setItemMeta(meta);
                        }

                        // Drop if full
                        if (!player.getInventory().addItem(placer).isEmpty()) {
                            player.getWorld().dropItem(player.getLocation(), placer);
                        }

                        player.sendMessage(
                                "§a[AI Builder] Design received! §eRight-click a block with the §a§lAI Build Placer §eto choose where to place the structure.");

                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to parse block placements JSON: " + jsonResult);
                        player.sendMessage("§c[AI Builder] Failed to parse AI design structure: " + e.getMessage());
                    }
                });
                return null;
            });

            return true;
        }

        return false;
    }

    private void sendSizeMenu(Player player, String prompt) {
        // Header
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .color(TextColor.color(0x5c5c5c)));
        player.sendMessage(Component.text(" ✦ AI Builder — Choose a Size")
                .color(TextColor.color(0xFFD700))
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text(" Building: ")
                .color(TextColor.color(0xAAAAAA))
                .append(Component.text("\"" + prompt + "\"")
                        .color(TextColor.color(0xFFFFFF))
                        .decorate(TextDecoration.ITALIC)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .color(TextColor.color(0x5c5c5c)));
        player.sendMessage(Component.empty());

        // Size buttons row
        TextComponent smallBtn = Component.text(" [ 🪵 Small ] ")
                .color(TextColor.color(0x55FF55))
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/aibuild small " + prompt))
                .hoverEvent(
                        HoverEvent.showText(Component.text("Small build (15x15x15)\nFastest generation, ~10 elements")
                                .color(TextColor.color(0x55FF55))));

        TextComponent mediumBtn = Component.text(" [ 🏠 Medium ] ")
                .color(TextColor.color(0xFFAA00))
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/aibuild medium " + prompt))
                .hoverEvent(HoverEvent
                        .showText(Component.text("Medium build (30x30x30)\nBalanced speed & detail, ~20 elements")
                                .color(TextColor.color(0xFFAA00))));

        TextComponent largeBtn = Component.text(" [ 🏰 Large ] ")
                .color(TextColor.color(0xFF5555))
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/aibuild large " + prompt))
                .hoverEvent(HoverEvent.showText(Component.text("Large build (50x50x50)\nMost detailed, ~40 elements")
                        .color(TextColor.color(0xFF5555))));

        player.sendMessage(smallBtn.append(mediumBtn).append(largeBtn));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .color(TextColor.color(0x5c5c5c)));
        player.sendMessage(Component.empty());
    }
}
