package com.warasugitewara.elevator.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
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
    // 1ティックの水平移動量のしきい値。歩き≒0.22, スプリント≒0.28, 静止≒0 なので、
    // これ未満のジャンプ(静止・微調整)のみを乗降操作として扱う。柱/床の上を走り抜けながらの
    // ジャンプは通常のジャンプと見なし、誤ってテレポートしないようにする。
    private static final double MAX_HORIZONTAL_JUMP_DRIFT = 0.15;
    // テレポート直後はクライアントがテレポート確認を返すまで移動パケットが無効扱いになり、直後の
    // ジャンプがPlayerJumpEventとして検出されないことがある。イベントだけに頼ると連打/長押しでの
    // 連続上昇のテンポが落ちるため、上昇成功後この時間内は柱の中心付近に留まったまま再び上昇し
    // 始めたらイベントを待たずに次の階へ連鎖させる。
    private static final long ASCENT_CHAIN_WINDOW_MS = 600L;
    // 連鎖を許可する柱中心からの水平ずれ。上昇時はブロック中心へテレポートしているため、その場の
    // ジャンプ連打ならこの範囲に収まり、歩いて柱を離れれば連鎖は切れる。
    private static final double ASCENT_CHAIN_MAX_OFFSET = 0.35;

    private static final class AscentChain {
        Location anchor;
        long expiresAt;
        double lastY;

        AscentChain(Location anchor, long expiresAt) {
            this.anchor = anchor;
            this.expiresAt = expiresAt;
            this.lastY = anchor.getY();
        }
    }

    private final ConfigManager configManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, AscentChain> ascentChains = new HashMap<>();

    public ElevatorListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    // 上昇は実際のジャンプでのみ発火するPlayerJumpEventで判定する。毎ティックのY差分監視と違い、
    // ジャンプの継続中に介入し続けないため、最上階でも通常どおりジャンプでき、床を走り抜ける際の
    // 誤発動も避けられる。
    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("elevator.use")) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz >= MAX_HORIZONTAL_JUMP_DRIFT * MAX_HORIZONTAL_JUMP_DRIFT) {
            return; // 移動しながらのジャンプは通常のジャンプとして扱い、乗降と見なさない
        }
        if (isOnCooldown(player, Direction.UP)) {
            return;
        }
        if (elevatorBlockBelow(player, from) == null) {
            return;
        }
        Location destination = triggerMove(player, Direction.UP, from);
        if (destination != null) {
            finishAscent(player, destination);
        }
    }

    // 上昇テレポート成功後の共通処理。残った上昇速度で浮き上がらないよう垂直成分のみリセットし
    // (水平速度は維持。全ゼロ化すると移動中に急停止し拘束感が出る)、連鎖ウィンドウを開始/更新する。
    private void finishAscent(Player player, Location destination) {
        Vector velocity = player.getVelocity();
        player.setVelocity(velocity.setY(0));
        ascentChains.put(player.getUniqueId(),
                new AscentChain(destination, System.currentTimeMillis() + ASCENT_CHAIN_WINDOW_MS));
    }

    // 下降(スニーク)はその場でしゃがみ続けてもPlayerMoveEventが発火しないため、毎ティック全
    // プレイヤーのしゃがみ状態をポーリングする。これにより待機中でも継続した下降ができる。
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("elevator.use")) {
                continue;
            }

            UUID id = player.getUniqueId();
            AscentChain chain = ascentChains.get(id);
            if (chain != null && !continueAscentChain(player, chain)) {
                ascentChains.remove(id);
            }

            if (!player.isSneaking()) {
                continue;
            }
            if (isOnCooldown(player, Direction.DOWN)) {
                continue;
            }
            Location loc = player.getLocation();
            if (elevatorBlockBelow(player, loc) == null) {
                continue;
            }
            triggerMove(player, Direction.DOWN, loc);
        }
    }

    // ウィンドウ内での上昇連鎖を試みる。falseを返したら連鎖終了(状態を破棄する)。
    private boolean continueAscentChain(Player player, AscentChain chain) {
        if (System.currentTimeMillis() > chain.expiresAt) {
            return false;
        }
        Location loc = player.getLocation();
        if (Math.abs(loc.getX() - chain.anchor.getX()) > ASCENT_CHAIN_MAX_OFFSET
                || Math.abs(loc.getZ() - chain.anchor.getZ()) > ASCENT_CHAIN_MAX_OFFSET) {
            return false;
        }
        double previousY = chain.lastY;
        chain.lastY = loc.getY();
        if (loc.getY() <= previousY) {
            return true; // まだ次の上昇が始まっていない(着地待ち等)。ウィンドウ内なので継続監視
        }
        if (isOnCooldown(player, Direction.UP)) {
            return true;
        }
        if (elevatorBlockBelow(player, chain.anchor) == null) {
            return false;
        }
        Location destination = triggerMove(player, Direction.UP, chain.anchor);
        if (destination == null) {
            return false; // これ以上上の階はない
        }
        finishAscent(player, destination);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cooldowns.remove(id);
        ascentChains.remove(id);
    }

    private Material elevatorBlockBelow(Player player, Location location) {
        Material material = location.clone().subtract(0, 1, 0).getBlock().getType();
        return configManager.getBlocks().containsKey(material) ? material : null;
    }

    // テレポートに成功した場合は移動先のLocationを、移動先の階が見つからない場合はnullを返す。
    private Location triggerMove(Player player, Direction direction, Location floorAnchor) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int currentY = floorAnchor.getBlockY() - 1;

        // 探索する柱とテレポート先のX/Zをアンカーのブロック座標に統一する。ブロック中心(+0.5)へ
        // 送ることで、幅0.6のプレイヤー当たり判定が必ず安全チェック済みの1柱内(0.2〜0.8)に収まり、
        // 隣接する壁へのめり込み(窒息)が構造的に起きなくなる。
        int bx = floorAnchor.getBlockX();
        int bz = floorAnchor.getBlockZ();
        ColumnAccessor column = new BukkitColumnAccessor(world, bx, bz);
        OptionalInt result = ElevatorService.findNextFloor(
                configManager.getBlocks(),
                column,
                currentY,
                world.getMinHeight(),
                world.getMaxHeight() - 1,
                direction
        );
        if (result.isEmpty()) {
            return null;
        }

        int floorY = result.getAsInt();
        Location destination = new Location(world, bx + 0.5, floorY + 1, bz + 0.5, loc.getYaw(), loc.getPitch());

        setCooldown(player);
        player.teleport(destination);
        playSound(player, direction);
        return destination;
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
