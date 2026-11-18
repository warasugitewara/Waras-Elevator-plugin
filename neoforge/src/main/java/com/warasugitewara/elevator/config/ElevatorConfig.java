package com.warasugitewara.elevator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * config/waraselevators.json の読み書き。構造はPaper版の config.yml と同じ。
 * <p>
 * 読み書きはすべてサーバースレッドから行われるが、念のため参照は不変スナップショットの
 * 差し替え(copy-on-write)にしている。
 */
public class ElevatorConfig {

    public static final String DEFAULT_ASCEND_SOUND = "minecraft:block.beacon.activate";
    public static final String DEFAULT_DESCEND_SOUND = "minecraft:block.beacon.deactivate";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path path;
    private final Logger logger;

    private volatile Map<Block, Integer> blocks = defaultBlocks();
    private volatile boolean soundEnabled = true;
    private volatile String ascendSound = DEFAULT_ASCEND_SOUND;
    private volatile String descendSound = DEFAULT_DESCEND_SOUND;
    private volatile boolean vanillaClientSupport = true;

    public ElevatorConfig(Path path, Logger logger) {
        this.path = path;
        this.logger = logger;
    }

    private static Map<Block, Integer> defaultBlocks() {
        Map<Block, Integer> map = new LinkedHashMap<>();
        map.put(Blocks.IRON_BLOCK, 15);
        map.put(Blocks.DIAMOND_BLOCK, 30);
        map.put(Blocks.NETHERITE_BLOCK, 90);
        return Collections.unmodifiableMap(map);
    }

    /**
     * 設定ファイルを読み込む。ファイルが無ければ既定値で作成する。
     * JSONとして壊れている場合はファイルを上書きせず、現在の値を維持する。
     *
     * @return 読み込みに成功した(または新規作成した)場合 true
     */
    public synchronized boolean load() {
        if (!Files.exists(path)) {
            blocks = defaultBlocks();
            soundEnabled = true;
            ascendSound = DEFAULT_ASCEND_SOUND;
            descendSound = DEFAULT_DESCEND_SOUND;
            vanillaClientSupport = true;
            save();
            return true;
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                logger.error("{} の形式が不正です(ルートがJSONオブジェクトではありません)。現在の設定を維持します。", path);
                return false;
            }
            root = parsed.getAsJsonObject();
        } catch (IOException | JsonParseException e) {
            logger.error("{} の読み込みに失敗しました。現在の設定を維持します。", path, e);
            return false;
        }

        Map<Block, Integer> loaded = new LinkedHashMap<>();
        if (root.has("blocks") && root.get("blocks").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("blocks")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                JsonElement materialKey = entry.get("material");
                JsonElement maxGapKey = entry.get("max-gap");
                if (materialKey == null || maxGapKey == null
                        || !materialKey.isJsonPrimitive() || !maxGapKey.isJsonPrimitive()) {
                    continue;
                }
                Optional<Block> block = parseBlock(materialKey.getAsString());
                if (block.isEmpty()) {
                    logger.warn("不明なブロックのためスキップします: {}", materialKey.getAsString());
                    continue;
                }
                int maxGap;
                try {
                    maxGap = Integer.parseInt(maxGapKey.getAsString());
                } catch (NumberFormatException e) {
                    logger.warn("max-gapが整数ではないためスキップします: {}", maxGapKey.getAsString());
                    continue;
                }
                if (maxGap < 0) {
                    logger.warn("max-gapが負の値のためスキップします: {}", maxGap);
                    continue;
                }
                loaded.put(block.get(), maxGap);
            }
        }
        blocks = Collections.unmodifiableMap(loaded);

        JsonObject sound = root.has("sound") && root.get("sound").isJsonObject()
                ? root.getAsJsonObject("sound") : new JsonObject();
        soundEnabled = getBoolean(sound, "enabled", true);
        ascendSound = getString(sound, "ascend", DEFAULT_ASCEND_SOUND);
        descendSound = getString(sound, "descend", DEFAULT_DESCEND_SOUND);
        vanillaClientSupport = getBoolean(root, "vanilla-client-support", true);
        return true;
    }

    public synchronized void save() {
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (Map.Entry<Block, Integer> entry : blocks.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("material", blockId(entry.getKey()));
            obj.addProperty("max-gap", entry.getValue());
            array.add(obj);
        }
        root.add("blocks", array);

        JsonObject sound = new JsonObject();
        sound.addProperty("enabled", soundEnabled);
        sound.addProperty("ascend", ascendSound);
        sound.addProperty("descend", descendSound);
        root.add("sound", sound);
        root.addProperty("vanilla-client-support", vanillaClientSupport);

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("{} の保存に失敗しました。", path, e);
        }
    }

    /**
     * "iron_block" / "minecraft:iron_block" / "IRON_BLOCK" のいずれの表記も受け付ける。空気は対象外。
     */
    public static Optional<Block> parseBlock(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(id).filter(block -> block != Blocks.AIR);
    }

    public static String blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        JsonElement element = obj.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    public Path getPath() {
        return path;
    }

    public Map<Block, Integer> getBlocks() {
        return blocks;
    }

    public synchronized void addOrUpdateBlock(Block block, int maxGap) {
        Map<Block, Integer> updated = new LinkedHashMap<>(blocks);
        updated.put(block, maxGap);
        blocks = Collections.unmodifiableMap(updated);
        save();
    }

    public synchronized boolean removeBlock(Block block) {
        Map<Block, Integer> updated = new LinkedHashMap<>(blocks);
        boolean removed = updated.remove(block) != null;
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

    public boolean isVanillaClientSupport() {
        return vanillaClientSupport;
    }
}
