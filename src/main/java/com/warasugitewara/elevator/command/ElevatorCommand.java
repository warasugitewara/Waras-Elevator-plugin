package com.warasugitewara.elevator.command;

import com.warasugitewara.elevator.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ElevatorCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;

    public ElevatorCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elevator.admin")) {
            sender.sendMessage("§cこのコマンドを使う権限がありません。");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "block" -> handleBlock(sender, args);
            case "sound" -> handleSound(sender, args);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleBlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c使い方: /elevator block <add|remove|list> ...");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c使い方: /elevator block add <material> <max-gap>");
                    return;
                }
                Material material = Material.matchMaterial(args[2]);
                if (material == null) {
                    sender.sendMessage("§cブロック種類が不正です: " + args[2]);
                    return;
                }
                int maxGap;
                try {
                    maxGap = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cmax-gapは整数で指定してください: " + args[3]);
                    return;
                }
                configManager.addOrUpdateBlock(material, maxGap);
                sender.sendMessage("§a" + material.getKey() + " をmax-gap=" + maxGap + "で登録しました。");
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c使い方: /elevator block remove <material>");
                    return;
                }
                Material material = Material.matchMaterial(args[2]);
                if (material == null) {
                    sender.sendMessage("§cブロック種類が不正です: " + args[2]);
                    return;
                }
                boolean removed = configManager.removeBlock(material);
                sender.sendMessage(removed
                        ? "§a" + material.getKey() + " を削除しました。"
                        : "§c" + material.getKey() + " は登録されていません。");
            }
            case "list" -> {
                sender.sendMessage("§eエレベーター対象ブロック一覧:");
                for (Map.Entry<Material, Integer> entry : configManager.getBlocks().entrySet()) {
                    sender.sendMessage("§7- " + entry.getKey().getKey() + ": max-gap=" + entry.getValue());
                }
            }
            default -> sender.sendMessage("§c使い方: /elevator block <add|remove|list> ...");
        }
    }

    private void handleSound(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c使い方: /elevator sound <on|off>");
            return;
        }
        boolean enabled = switch (args[1].toLowerCase()) {
            case "on" -> true;
            case "off" -> false;
            default -> {
                sender.sendMessage("§con/offで指定してください。");
                yield configManager.isSoundEnabled();
            }
        };
        configManager.setSoundEnabled(enabled);
        sender.sendMessage("§aサウンドを" + (enabled ? "有効" : "無効") + "にしました。");
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§e=== IronElevators 設定 ===");
        sender.sendMessage("§7サウンド: " + (configManager.isSoundEnabled() ? "有効" : "無効"));
        sender.sendMessage("§7上昇音: " + configManager.getAscendSound());
        sender.sendMessage("§7下降音: " + configManager.getDescendSound());
        for (Map.Entry<Material, Integer> entry : configManager.getBlocks().entrySet()) {
            sender.sendMessage("§7- " + entry.getKey().getKey() + ": max-gap=" + entry.getValue());
        }
    }

    private void handleReload(CommandSender sender) {
        configManager.load();
        sender.sendMessage("§aconfig.ymlを再読込しました。");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/elevator block <add|remove|list>");
        sender.sendMessage("§e/elevator sound <on|off>");
        sender.sendMessage("§e/elevator info");
        sender.sendMessage("§e/elevator reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("block", "sound", "info", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("block")) {
            return filter(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sound")) {
            return filter(List.of("on", "off"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}
