# IronElevators 再実装 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paper 1.21.11 上で動作する、ブロックを縦に積んでジャンプ/スニークで上下移動するエレベータープラグイン「IronElevators」を、コマンドによる即時設定変更機能付きでゼロから実装する。

**Architecture:** `ElevatorService` をBukkit非依存の純粋ロジック層として実装し、JUnitで単体テストする。Bukkit依存部分（`ConfigManager`/`ElevatorListener`/`ElevatorCommand`/`ElevatorPlugin`）はそれを呼び出す薄いラッパーとして実装し、実機での手動テストで検証する。

**Tech Stack:** Java 21, Gradle (Kotlin DSL), Paper API 1.21.11-R0.1-SNAPSHOT, JUnit 5

## Global Constraints

- 対象サーバー: Paper 1.21.11、Java 21
- 外部プラグイン依存なし（WorldGuard等は使用しない）
- パッケージ名: `com.warasugitewara.elevator`
- メインクラス: `com.warasugitewara.elevator.ElevatorPlugin`
- プラグイン名: `IronElevators`、コマンドエイリアス: `/el`
- デフォルトブロック設定: `minecraft:iron_block`(max-gap 15), `minecraft:diamond_block`(max-gap 30), `minecraft:netherite_block`(max-gap 90)
- 権限: `elevator.use`(デフォルトtrue), `elevator.admin`(デフォルトop)
- コマンドによる設定変更は即座に `config.yml` へ保存（reload不要）
- 着地スペースが塞がっている階はスキップし、同ブロック種別の max-gap の範囲内でさらに先を探索する

---

### Task 1: プロジェクト初期設定

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `src/main/resources/plugin.yml`
- Create: `src/main/resources/config.yml`
- Create: `.gitignore`

**Interfaces:**
- Produces: Gradleビルド可能なプロジェクト構造。以降のタスクは `src/main/java/com/warasugitewara/elevator/` 以下にクラスを追加していく。

- [ ] **Step 1: `settings.gradle.kts` を作成**

```kotlin
rootProject.name = "IronElevators"
```

- [ ] **Step 2: `gradle.properties` を作成**

```properties
org.gradle.jvmargs=-Xmx1G
```

- [ ] **Step 3: `build.gradle.kts` を作成**

```kotlin
plugins {
    id("java")
}

group = "com.warasugitewara"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
```

- [ ] **Step 4: `src/main/resources/plugin.yml` を作成**

```yaml
name: IronElevators
version: '${version}'
main: com.warasugitewara.elevator.ElevatorPlugin
api-version: '1.21'
author: warasugitewara
description: Walk onto stacked blocks and jump/sneak to ride elevators.
commands:
  elevator:
    description: Manage IronElevators settings.
    usage: /elevator <block|sound|info|reload>
    aliases: [el]
permissions:
  elevator.use:
    description: Allows using elevators.
    default: true
  elevator.admin:
    description: Allows managing elevator configuration.
    default: op
```

- [ ] **Step 5: `src/main/resources/config.yml` を作成**

```yaml
blocks:
  - material: minecraft:iron_block
    max-gap: 15
  - material: minecraft:diamond_block
    max-gap: 30
  - material: minecraft:netherite_block
    max-gap: 90
sound:
  enabled: true
  ascend: minecraft:block.beacon.activate
  descend: minecraft:block.beacon.deactivate
```

- [ ] **Step 6: `.gitignore` を作成**

```
.gradle/
build/
.idea/
*.iml
out/
```

- [ ] **Step 7: Gradle wrapper を生成**

Run: `gradle wrapper --gradle-version 8.10`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` が生成される。

- [ ] **Step 8: 空のビルドが通ることを確認**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`（mainクラスが存在しないため `build` 自体はコンパイル対象なしで成功する。次タスク以降でクラスを追加する）

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties src/main/resources/plugin.yml src/main/resources/config.yml .gitignore gradlew gradlew.bat gradle
git commit -m "chore: Gradleプロジェクトの初期設定を追加"
```

---

### Task 2: `Direction` enum と `ColumnAccessor` インターフェース

**Files:**
- Create: `src/main/java/com/warasugitewara/elevator/service/Direction.java`
- Create: `src/main/java/com/warasugitewara/elevator/service/ColumnAccessor.java`

**Interfaces:**
- Produces:
  - `enum Direction { UP, DOWN }`
  - `interface ColumnAccessor { Material materialAt(int y); boolean isPassableAt(int y); }`

- [ ] **Step 1: `Direction.java` を作成**

```java
package com.warasugitewara.elevator.service;

public enum Direction {
    UP,
    DOWN
}
```

- [ ] **Step 2: `ColumnAccessor.java` を作成**

```java
package com.warasugitewara.elevator.service;

import org.bukkit.Material;

/**
 * 1つの(x, z)カラムにおける、指定Y座標のブロック情報への読み取り専用アクセス。
 * 実装はBukkitワールド（本番）またはフェイクデータ（テスト）。
 */
public interface ColumnAccessor {
    Material materialAt(int y);

    boolean isPassableAt(int y);
}
```

- [ ] **Step 3: コンパイルを確認**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/warasugitewara/elevator/service/Direction.java src/main/java/com/warasugitewara/elevator/service/ColumnAccessor.java
git commit -m "feat: Direction enumとColumnAccessorインターフェースを追加"
```

---

### Task 3: `ElevatorService` コア探索ロジック（TDD）

**Files:**
- Create: `src/main/java/com/warasugitewara/elevator/service/ElevatorService.java`
- Test: `src/test/java/com/warasugitewara/elevator/service/ElevatorServiceTest.java`

**Interfaces:**
- Consumes: `Direction`（Task 2）, `ColumnAccessor`（Task 2）
- Produces: `ElevatorService.findNextFloor(Map<Material, Integer> blockConfig, ColumnAccessor column, int currentY, int minY, int maxY, Direction direction): OptionalInt`
  - 戻り値は「安全に着地できる次の階のエレベーターブロックのY座標」。見つからなければ`OptionalInt.empty()`。
  - 着地判定: ブロックY座標+1, +2 が `isPassableAt(true)` であること。

- [ ] **Step 1: テストファイルの骨格と1つ目の失敗するテストを書く**

`src/test/java/com/warasugitewara/elevator/service/ElevatorServiceTest.java`:

```java
package com.warasugitewara.elevator.service;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevatorServiceTest {

    private static final Map<Material, Integer> BLOCK_CONFIG = Map.of(
            Material.IRON_BLOCK, 15,
            Material.DIAMOND_BLOCK, 30,
            Material.NETHERITE_BLOCK, 90
    );

    /**
     * テスト用の単一カラム。Y -> Material のマップと、通行可能なYの集合で構成する。
     * 明示的に登録していないYは AIR / 通行可能 とみなす。
     */
    private static final class FakeColumn implements ColumnAccessor {
        private final Map<Integer, Material> materials = new HashMap<>();
        private final Set<Integer> blocked = new HashSet<>();

        FakeColumn fill(int fromYInclusive, int toYInclusive, Material material) {
            for (int y = fromYInclusive; y <= toYInclusive; y++) {
                materials.put(y, material);
            }
            return this;
        }

        FakeColumn block(int y) {
            blocked.add(y);
            return this;
        }

        @Override
        public Material materialAt(int y) {
            return materials.getOrDefault(y, Material.AIR);
        }

        @Override
        public boolean isPassableAt(int y) {
            return !blocked.contains(y) && materialAt(y) != Material.IRON_BLOCK
                    && materialAt(y) != Material.DIAMOND_BLOCK && materialAt(y) != Material.NETHERITE_BLOCK;
        }
    }

    @Test
    void findsAdjacentFloorWhenStandingOnContiguousRun() {
        // y=60-62 鉄ブロック3段、y=70 に次の鉄ブロック1段。間は全て空気で通行可能。
        FakeColumn column = new FakeColumn()
                .fill(60, 62, Material.IRON_BLOCK)
                .fill(70, 70, Material.IRON_BLOCK);

        OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

        assertTrue(result.isPresent());
        assertEquals(70, result.getAsInt());
    }
}
```

- [ ] **Step 2: テストが失敗することを確認**

Run: `./gradlew test --tests com.warasugitewara.elevator.service.ElevatorServiceTest`
Expected: コンパイルエラー（`ElevatorService` クラスが存在しない）でFAIL

- [ ] **Step 3: `ElevatorService` の最小実装を書く**

```java
package com.warasugitewara.elevator.service;

import org.bukkit.Material;

import java.util.Map;
import java.util.OptionalInt;

public final class ElevatorService {

    private ElevatorService() {
    }

    public static OptionalInt findNextFloor(
            Map<Material, Integer> blockConfig,
            ColumnAccessor column,
            int currentY,
            int minY,
            int maxY,
            Direction direction
    ) {
        Material base = column.materialAt(currentY);
        Integer maxGap = blockConfig.get(base);
        if (maxGap == null) {
            return OptionalInt.empty();
        }

        int step = direction == Direction.UP ? 1 : -1;

        int y = currentY;
        while (inBounds(y + step, minY, maxY) && column.materialAt(y + step) == base) {
            y += step;
        }

        int gap = 0;
        int cursor = y;
        while (true) {
            cursor += step;
            if (!inBounds(cursor, minY, maxY)) {
                return OptionalInt.empty();
            }

            Material material = column.materialAt(cursor);
            if (blockConfig.containsKey(material)) {
                if (isSafeLanding(column, cursor, minY, maxY)) {
                    return OptionalInt.of(cursor);
                }
                continue;
            }

            gap++;
            if (gap > maxGap) {
                return OptionalInt.empty();
            }
        }
    }

    private static boolean isSafeLanding(ColumnAccessor column, int floorY, int minY, int maxY) {
        int feetY = floorY + 1;
        int headY = floorY + 2;
        if (!inBounds(headY, minY, maxY)) {
            return false;
        }
        return column.isPassableAt(feetY) && column.isPassableAt(headY);
    }

    private static boolean inBounds(int y, int minY, int maxY) {
        return y >= minY && y <= maxY;
    }
}
```

- [ ] **Step 4: テストが通ることを確認**

Run: `./gradlew test --tests com.warasugitewara.elevator.service.ElevatorServiceTest`
Expected: `BUILD SUCCESSFUL`、1テストPASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/warasugitewara/elevator/service/ElevatorService.java src/test/java/com/warasugitewara/elevator/service/ElevatorServiceTest.java
git commit -m "feat: ElevatorServiceの基本探索ロジックを実装"
```

- [ ] **Step 6: ガップが max-gap を超えると見つからないテストを追加**

`ElevatorServiceTest.java` に追記:

```java
    @Test
    void returnsEmptyWhenGapExceedsMaxGap() {
        // y=60 鉄ブロック1段。次の鉄ブロックはy=76（gap=15）だが鉄のmax-gapは15なので
        // 60→76間の空気ブロック数は15個（61..75）であり、これはmax-gapと等しいので成功するはず。
        // gapを16にして失敗させる: 次の鉄ブロックをy=77に置く。
        FakeColumn column = new FakeColumn()
                .fill(60, 60, Material.IRON_BLOCK)
                .fill(77, 77, Material.IRON_BLOCK);

        OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

        assertTrue(result.isEmpty());
    }

    @Test
    void findsFloorExactlyAtMaxGapBoundary() {
        // y=60に鉄ブロック、y=76に次の鉄ブロック。間の空気は61..75の15個 = max-gap(15)と同数なので成功。
        FakeColumn column = new FakeColumn()
                .fill(60, 60, Material.IRON_BLOCK)
                .fill(76, 76, Material.IRON_BLOCK);

        OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

        assertTrue(result.isPresent());
        assertEquals(76, result.getAsInt());
    }
```

- [ ] **Step 7: テストを実行し、両方がPASSすることを確認**

Run: `./gradlew test --tests com.warasugitewara.elevator.service.ElevatorServiceTest`
Expected: `BUILD SUCCESSFUL`、3テストPASS（実装は既にこのケースを満たしているため、追加実装は不要）

- [ ] **Step 8: Commit**

```bash
git add src/test/java/com/warasugitewara/elevator/service/ElevatorServiceTest.java
git commit -m "test: max-gap境界値のテストを追加"
```

- [ ] **Step 9: 塞がった階をスキップして先を探すテストを追加**

`ElevatorServiceTest.java` に追記:

```java
    @Test
    void skipsBlockedFloorAndFindsFurtherSafeFloor() {
        // y=60に鉄ブロック。y=65に次の鉄ブロックがあるが、その上(66,67)が塞がっている。
        // さらにy=70に鉄ブロックがあり、その上(71,72)は通行可能。70が返るべき。
        FakeColumn column = new FakeColumn()
                .fill(60, 60, Material.IRON_BLOCK)
                .fill(65, 65, Material.IRON_BLOCK)
                .fill(70, 70, Material.IRON_BLOCK)
                .block(66)
                .block(67);

        OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

        assertTrue(result.isPresent());
        assertEquals(70, result.getAsInt());
    }

    @Test
    void returnsEmptyWhenNotStandingOnElevatorBlock() {
        FakeColumn column = new FakeColumn().fill(60, 60, Material.STONE);

        OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

        assertTrue(result.isEmpty());
    }

    @Test
    void worksForDownwardDirection() {
        FakeColumn column = new FakeColumn()
                .fill(60, 60, Material.DIAMOND_BLOCK)
                .fill(50, 50, Material.DIAMOND_BLOCK);

        OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.DOWN);

        assertTrue(result.isPresent());
        assertEquals(50, result.getAsInt());
    }
```

- [ ] **Step 10: 全テストを実行しPASSを確認**

Run: `./gradlew test --tests com.warasugitewara.elevator.service.ElevatorServiceTest`
Expected: `BUILD SUCCESSFUL`、6テストPASS

- [ ] **Step 11: Commit**

```bash
git add src/test/java/com/warasugitewara/elevator/service/ElevatorServiceTest.java
git commit -m "test: 塞がった階のスキップと下降方向のテストを追加"
```

---

### Task 4: `ConfigManager`（設定の読み込み・保存）

**Files:**
- Create: `src/main/java/com/warasugitewara/elevator/config/ConfigManager.java`

**Interfaces:**
- Consumes: なし（`org.bukkit.plugin.java.JavaPlugin` のインスタンスのみ）
- Produces:
  - `ConfigManager(JavaPlugin plugin)`
  - `void load()`
  - `Map<Material, Integer> getBlocks()`
  - `void addOrUpdateBlock(Material material, int maxGap)`
  - `boolean removeBlock(Material material)`
  - `boolean isSoundEnabled()`
  - `void setSoundEnabled(boolean enabled)`
  - `String getAscendSound()`
  - `String getDescendSound()`
  - `void save()`

- [ ] **Step 1: `ConfigManager.java` を作成**

```java
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
    private final Map<Material, Integer> blocks = new LinkedHashMap<>();
    private boolean soundEnabled;
    private String ascendSound;
    private String descendSound;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        blocks.clear();
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
                int maxGap = Integer.parseInt(maxGapKey.toString());
                blocks.put(material, maxGap);
            }
        }

        soundEnabled = config.getBoolean("sound.enabled", true);
        ascendSound = config.getString("sound.ascend", "minecraft:block.beacon.activate");
        descendSound = config.getString("sound.descend", "minecraft:block.beacon.deactivate");
    }

    public Map<Material, Integer> getBlocks() {
        return Collections.unmodifiableMap(blocks);
    }

    public void addOrUpdateBlock(Material material, int maxGap) {
        blocks.put(material, maxGap);
        save();
    }

    public boolean removeBlock(Material material) {
        boolean removed = blocks.remove(material) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        save();
    }

    public String getAscendSound() {
        return ascendSound;
    }

    public String getDescendSound() {
        return descendSound;
    }

    public void save() {
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
```

- [ ] **Step 2: コンパイルを確認**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/warasugitewara/elevator/config/ConfigManager.java
git commit -m "feat: ConfigManagerを実装し設定の読み書きを即時保存できるようにする"
```

---

### Task 5: `BukkitColumnAccessor` と `ElevatorListener`

**Files:**
- Create: `src/main/java/com/warasugitewara/elevator/service/BukkitColumnAccessor.java`
- Create: `src/main/java/com/warasugitewara/elevator/listener/ElevatorListener.java`

**Interfaces:**
- Consumes:
  - `ElevatorService.findNextFloor(...)`（Task 3）
  - `ConfigManager.getBlocks()`, `isSoundEnabled()`, `getAscendSound()`, `getDescendSound()`（Task 4）
- Produces: `ElevatorListener(ConfigManager configManager)` — `Listener` 実装。`PlayerMoveEvent`（ジャンプ検知→上昇）と `PlayerToggleSneakEvent`（スニーク開始→下降）を処理する。

- [ ] **Step 1: `BukkitColumnAccessor.java` を作成**

```java
package com.warasugitewara.elevator.service;

import org.bukkit.Material;
import org.bukkit.World;

public class BukkitColumnAccessor implements ColumnAccessor {

    private final World world;
    private final int x;
    private final int z;

    public BukkitColumnAccessor(World world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    @Override
    public Material materialAt(int y) {
        return world.getBlockAt(x, y, z).getType();
    }

    @Override
    public boolean isPassableAt(int y) {
        return world.getBlockAt(x, y, z).isPassable();
    }
}
```

- [ ] **Step 2: `ElevatorListener.java` を作成**

```java
package com.warasugitewara.elevator.listener;

import com.warasugitewara.elevator.config.ConfigManager;
import com.warasugitewara.elevator.service.BukkitColumnAccessor;
import com.warasugitewara.elevator.service.ColumnAccessor;
import com.warasugitewara.elevator.service.Direction;
import com.warasugitewara.elevator.service.ElevatorService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

public class ElevatorListener implements Listener {

    private static final long COOLDOWN_MS = 300L;

    private final ConfigManager configManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ElevatorListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getY() <= from.getY()) {
            return;
        }

        Player player = event.getPlayer();
        Material below = elevatorBlockBelow(player, from);
        if (below == null) {
            return;
        }
        if (!player.hasPermission("elevator.use") || isOnCooldown(player)) {
            return;
        }

        triggerMove(player, Direction.UP);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        Material below = elevatorBlockBelow(player, player.getLocation());
        if (below == null) {
            return;
        }
        if (!player.hasPermission("elevator.use") || isOnCooldown(player)) {
            return;
        }

        triggerMove(player, Direction.DOWN);
    }

    private Material elevatorBlockBelow(Player player, Location location) {
        Material material = location.clone().subtract(0, 1, 0).getBlock().getType();
        return configManager.getBlocks().containsKey(material) ? material : null;
    }

    private void triggerMove(Player player, Direction direction) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int currentY = loc.getBlockY() - 1;

        ColumnAccessor column = new BukkitColumnAccessor(world, loc.getBlockX(), loc.getBlockZ());
        OptionalInt result = ElevatorService.findNextFloor(
                configManager.getBlocks(),
                column,
                currentY,
                world.getMinHeight(),
                world.getMaxHeight() - 1,
                direction
        );
        if (result.isEmpty()) {
            return;
        }

        int floorY = result.getAsInt();
        Location destination = new Location(world, loc.getX(), floorY + 1, loc.getZ(), loc.getYaw(), loc.getPitch());

        setCooldown(player);
        player.teleport(destination);
        playSound(player, direction);
    }

    private boolean isOnCooldown(Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < COOLDOWN_MS;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void playSound(Player player, Direction direction) {
        if (!configManager.isSoundEnabled()) {
            return;
        }
        String soundKey = direction == Direction.UP
                ? configManager.getAscendSound()
                : configManager.getDescendSound();
        player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
    }
}
```

- [ ] **Step 3: コンパイルを確認**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/warasugitewara/elevator/service/BukkitColumnAccessor.java src/main/java/com/warasugitewara/elevator/listener/ElevatorListener.java
git commit -m "feat: BukkitColumnAccessorとElevatorListenerを実装"
```

---

### Task 6: `ElevatorCommand`（設定変更コマンド）

**Files:**
- Create: `src/main/java/com/warasugitewara/elevator/command/ElevatorCommand.java`

**Interfaces:**
- Consumes: `ConfigManager`（Task 4）のフルAPI
- Produces: `ElevatorCommand(ConfigManager configManager)` — `CommandExecutor`, `TabCompleter` 実装。サブコマンド: `block add|remove|list`, `sound on|off`, `info`, `reload`

- [ ] **Step 1: `ElevatorCommand.java` を作成**

```java
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
```

- [ ] **Step 2: コンパイルを確認**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/warasugitewara/elevator/command/ElevatorCommand.java
git commit -m "feat: ElevatorCommandで設定の即時変更コマンドを実装"
```

---

### Task 7: `ElevatorPlugin` メインクラスと結合・手動テスト

**Files:**
- Create: `src/main/java/com/warasugitewara/elevator/ElevatorPlugin.java`

**Interfaces:**
- Consumes: `ConfigManager`（Task 4）, `ElevatorListener`（Task 5）, `ElevatorCommand`（Task 6）
- Produces: `JavaPlugin` のエントリポイント。サーバー起動時に全コンポーネントを初期化する。

- [ ] **Step 1: `ElevatorPlugin.java` を作成**

```java
package com.warasugitewara.elevator;

import com.warasugitewara.elevator.command.ElevatorCommand;
import com.warasugitewara.elevator.config.ConfigManager;
import com.warasugitewara.elevator.listener.ElevatorListener;
import org.bukkit.plugin.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class ElevatorPlugin extends JavaPlugin {

    private ConfigManager configManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        getServer().getPluginManager().registerEvents(new ElevatorListener(configManager), this);

        ElevatorCommand command = new ElevatorCommand(configManager);
        PluginCommand pluginCommand = getCommand("elevator");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("IronElevators enabled.");
    }
}
```

- [ ] **Step 2: フルビルドを確認**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`、全テストPASS、`build/libs/IronElevators-1.0.0.jar` が生成される

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/warasugitewara/elevator/ElevatorPlugin.java
git commit -m "feat: ElevatorPluginメインクラスで全コンポーネントを結合"
```

- [ ] **Step 4: 実機での手動テスト手順を実施**

ローカルのPaper 1.21.11テストサーバーの `plugins/` に `build/libs/IronElevators-1.0.0.jar` を配置して起動し、以下を確認する:

1. サーバー起動ログに `IronElevators enabled.` が出力される（プラグインロード失敗がないこと）
2. 鉄ブロックを3段以上積んだ柱の上に乗り、ジャンプすると何も起きない（同じ柱内なので次の階がない）こと
3. 1つ目の鉄ブロック柱から15ブロック以内に2つ目の鉄ブロック柱を建て、1つ目の上でジャンプすると2つ目の柱の上にテレポートし、上昇音が再生されること
4. 2つ目の柱の上でスニークすると1つ目の柱の上に戻り、下降音が再生されること
5. 2つ目の柱の直上を別のブロックで完全に塞ぎ、さらにその先（16〜30ブロック以内）にダイヤモンドブロック柱を置いた状態で1つ目の柱からジャンプすると、塞がっている2つ目をスキップしてダイヤモンドブロック柱の上にテレポートすること
6. `/elevator block list` で登録ブロックとmax-gapの一覧が表示されること
7. `/elevator block add minecraft:gold_block 20` を実行後、`/elevator block list` に反映され、`config.yml` にも保存されていること（サーバー再起動なしで反映を確認）
8. `/elevator block remove minecraft:gold_block` で削除できること
9. `/elevator sound off` でサウンドが鳴らなくなること
10. OP権限を持たないプレイヤーで `/elevator info` を実行すると権限エラーになること

- [ ] **Step 5: 手動テストの結果をユーザーに報告し、問題があれば修正後に再テストする**
