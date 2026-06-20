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

    // PlayerMoveEventは実際に座標/向きが変化した時しか発火せず、立ち止まったままシフトを
    // 押し続けているだけでは判定が走らない。移動イベントに依存せず、毎ティック全プレイヤーの
    // 速度/しゃがみ状態を見ることで、その場待機中でも継続した上昇/下降ができるようにする。
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("elevator.use") || isOnCooldown(player)) {
                continue;
            }

            Direction direction;
            if (player.getVelocity().getY() > 0) {
                direction = Direction.UP;
            } else if (player.isSneaking()) {
                direction = Direction.DOWN;
            } else {
                continue;
            }

            Material below = elevatorBlockBelow(player, player.getLocation());
            if (below == null) {
                continue;
            }

            triggerMove(player, direction);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
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
