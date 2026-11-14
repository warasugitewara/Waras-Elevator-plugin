package com.warasugitewara.elevator.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    // Foliaではリスナー(各プレイヤーのリージョンスレッド)とコマンドが別スレッドで動くため、
    // 不変スナップショットを差し替える方式(copy-on-write)にして読み取り側をロック不要にする。
    private volatile Map<Material, Integer> blocks = Map.of();
    private volatile boolean soundEnabled;
    private volatile String ascendSound;
    private volatile String descendSound;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        Map<Material, Integer> loaded = new LinkedHashMap<>();
        List<?> rawBlocks = config.getList("blocks");
        if (rawBlocks != null) {
            for (Object entry : rawBlocks) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Object materialKey = map.get("material");
                Object maxGapKey = map.get("max-gap");
                if (materialKey == null || maxGapKey == null) {
                    continue;
                }
                Material material = Material.matchMaterial(materialKey.toString());
                if (material == null) {
                    continue;
                }
                int maxGap;
                try {
                    maxGap = Integer.parseInt(maxGapKey.toString());
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("max-gapが整数ではないためスキップします: " + maxGapKey);
                    continue;
                }
                if (maxGap < 0) {
                    plugin.getLogger().warning("max-gapが負の値のためスキップします: " + maxGapKey);
                    continue;
                }
                loaded.put(material, maxGap);
            }
        }
        blocks = Collections.unmodifiableMap(loaded);

        soundEnabled = config.getBoolean("sound.enabled", true);
        ascendSound = config.getString("sound.ascend", "minecraft:block.beacon.activate");
        descendSound = config.getString("sound.descend", "minecraft:block.beacon.deactivate");
    }

    public Map<Material, Integer> getBlocks() {
        return blocks;
    }

    public synchronized void addOrUpdateBlock(Material material, int maxGap) {
        Map<Material, Integer> updated = new LinkedHashMap<>(blocks);
        updated.put(material, maxGap);
        blocks = Collections.unmodifiableMap(updated);
        save();
    }

    public synchronized boolean removeBlock(Material material) {
        Map<Material, Integer> updated = new LinkedHashMap<>(blocks);
        boolean removed = updated.remove(material) != null;
        if (removed) {
            blocks = Collections.unmodifiableMap(updated);
            save();
        }
        return removed;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public synchronized void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        save();
    }

    public String getAscendSound() {
        return ascendSound;
    }

    public String getDescendSound() {
        return descendSound;
    }

    public synchronized void save() {
        FileConfiguration config = plugin.getConfig();

        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : blocks.entrySet()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("material", entry.getKey().getKey().toString());
            map.put("max-gap", entry.getValue());
            serialized.add(map);
        }
        config.set("blocks", serialized);
        config.set("sound.enabled", soundEnabled);
        config.set("sound.ascend", ascendSound);
        config.set("sound.descend", descendSound);

        plugin.saveConfig();
    }
}
