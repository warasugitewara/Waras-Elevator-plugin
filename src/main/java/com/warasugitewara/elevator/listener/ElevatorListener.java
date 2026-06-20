package com.warasugitewara.elevator.listener;

import com.warasugitewara.elevator.config.ConfigManager;
import com.warasugitewara.elevator.service.BukkitColumnAccessor;
import com.warasugitewara.elevator.service.ColumnAccessor;
import com.warasugitewara.elevator.service.Direction;
import com.warasugitewara.elevator.service.ElevatorService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

public class ElevatorListener implements Listener {

    private static final long UP_COOLDOWN_MS = 150L;
    private static final long DOWN_COOLDOWN_MS = 150L;

    private final ConfigManager configManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    // Player#getVelocity()はノックバック等の明示的な速度には追従するが、ジャンプ自体の
    // 上昇では信頼できる値を返さないことがあるため、毎ティックのY座標差分で上昇を判定する。
    private final Map<UUID, Double> lastY = new HashMap<>();
    // 上昇中は現在位置の1つ下を見ると、ジャンプの頂点付近でエレベーターブロックから外れた
    // (空気の)位置を見てしまい判定を取りこぼす。直前に接地していたY座標を基準にする。
    private final Map<UUID, Double> lastGroundY = new HashMap<>();

    public ElevatorListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    // PlayerMoveEventは実際に座標/向きが変化した時しか発火せず、立ち止まったままシフトを
    // 押し続けているだけでは判定が走らない。移動イベントに依存せず、毎ティック全プレイヤーの
    // Y座標差分/しゃがみ状態を見ることで、その場待機中でも継続した上昇/下降ができるようにする。
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            double currentY = player.getLocation().getY();
            Double previousY = lastY.put(id, currentY);

            if (player.isOnGround()) {
                lastGroundY.put(id, currentY);
            }

            if (!player.hasPermission("elevator.use")) {
                continue;
            }

            Direction direction;
            Location belowAnchor;
            if (previousY != null && currentY > previousY) {
                Double groundY = lastGroundY.get(id);
                if (groundY == null) {
                    continue;
                }
                direction = Direction.UP;
                belowAnchor = player.getLocation().clone();
                belowAnchor.setY(groundY);
            } else if (player.isSneaking()) {
                direction = Direction.DOWN;
                belowAnchor = player.getLocation();
            } else {
                continue;
            }

            if (isOnCooldown(player, direction)) {
                continue;
            }

            Material below = elevatorBlockBelow(player, belowAnchor);
            if (below == null) {
                continue;
            }

            triggerMove(player, direction, belowAnchor);
            // テレポート後に残ったジャンプの上昇速度で再度跳ね上がってしまうのを防ぐため、
            // 着地直後のY座標差分判定が誤って続けて発火しないよう速度と基準Yをリセットする。
            player.setVelocity(new Vector(0, 0, 0));
            double landedY = player.getLocation().getY();
            lastY.put(id, landedY);
            lastGroundY.put(id, landedY);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        lastY.remove(id);
        lastGroundY.remove(id);
    }

    private Material elevatorBlockBelow(Player player, Location location) {
        Material material = location.clone().subtract(0, 1, 0).getBlock().getType();
        return configManager.getBlocks().containsKey(material) ? material : null;
    }

    private void triggerMove(Player player, Direction direction, Location floorAnchor) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        // 上昇中は現在のY座標ではなく、下のブロック判定と同じ接地時のY座標を基準にしないと、
        // ジャンプの頂点付近で「今いるブロック行」がずれて現在の床を取りこぼしてしまう。
        int currentY = floorAnchor.getBlockY() - 1;

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

    private boolean isOnCooldown(Player player, Direction direction) {
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) {
            return false;
        }
        long threshold = direction == Direction.UP ? UP_COOLDOWN_MS : DOWN_COOLDOWN_MS;
        return System.currentTimeMillis() - last < threshold;
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
