package com.warasugitewara.elevator.server;

import com.warasugitewara.elevator.config.ElevatorConfig;
import com.warasugitewara.elevator.network.ElevatorInputPayload;
import com.warasugitewara.elevator.service.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 本Modを導入していないクライアント(バニラクライアント等)向けの、サーバー側だけでの昇降検出。
 * <p>
 * Minecraft 1.21.1 ではキー入力の状態がサーバーへ送られないため、次の情報から推定する。
 * <ul>
 *     <li>上昇: サーバーがジャンプを検知した時に発火する {@link LivingEvent.LivingJumpEvent}</li>
 *     <li>下降: スニーク状態({@link ServerPlayer#isShiftKeyDown()})が false→true に変わった瞬間</li>
 *     <li>移動キー: そのtickの水平移動量が閾値を超えていれば「移動中」とみなして無視</li>
 * </ul>
 * 本Mod導入済みのクライアントは正確なキー押下を {@link ElevatorInputPayload} で送るため、
 * 二重に反応しないようここでは処理しない。
 */
public class VanillaClientInputTracker {

    // 1tickあたりの水平移動量(ブロック)。歩行は約0.2、スニーク歩行は約0.06。
    // 静止状態からのジャンプ/スニークは0なので、これを超えたら移動中とみなす。
    private static final double MOVING_THRESHOLD = 0.05;

    private static final class State {
        boolean previousShift;
        double lastX;
        double lastZ;
        Vec3 pendingJumpPosition;
    }

    private final ElevatorConfig config;
    private final ElevatorMover mover;
    // すべてサーバースレッドからのみアクセスされる
    private final Map<UUID, State> states = new HashMap<>();

    public VanillaClientInputTracker(ElevatorConfig config, ElevatorMover mover) {
        this.config = config;
        this.mover = mover;
    }

    public void register(IEventBus bus) {
        bus.addListener(this::onJump);
        bus.addListener(this::onServerTick);
        bus.addListener(this::onLogout);
    }

    // ジャンプ検知は移動パケット処理(handleMovePlayer)の途中で発火するため、ここでテレポートすると
    // 後続の移動処理と競合する。位置だけ記録し、tick終了時にまとめて処理する。
    private void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (!isTarget(player)) {
            return;
        }
        // この時点の座標は移動前(接地していた位置)
        stateOf(player).pendingJumpPosition = player.position();
    }

    private void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            State state = stateOf(player);
            boolean shift = player.isShiftKeyDown();

            if (isTarget(player)) {
                boolean jumpPressed = state.pendingJumpPosition != null;
                boolean sneakPressed = shift && !state.previousShift;
                // 同時押しは方向が決まらないため無視(Paper版と同じ)
                if (jumpPressed != sneakPressed) {
                    if (jumpPressed) {
                        Vec3 from = state.pendingJumpPosition;
                        if (horizontalDistance(player, from.x, from.z) <= MOVING_THRESHOLD) {
                            mover.tryMove(player, Direction.UP, from);
                        }
                    } else if (horizontalDistance(player, state.lastX, state.lastZ) <= MOVING_THRESHOLD) {
                        mover.tryMove(player, Direction.DOWN, player.position());
                    }
                }
            }

            // テレポートした場合も新しい座標を基準にする
            state.previousShift = shift;
            state.lastX = player.getX();
            state.lastZ = player.getZ();
            state.pendingJumpPosition = null;
        }
    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        states.remove(event.getEntity().getUUID());
        mover.forget(event.getEntity().getUUID());
    }

    private boolean isTarget(ServerPlayer player) {
        return config.isVanillaClientSupport() && !player.connection.hasChannel(ElevatorInputPayload.TYPE);
    }

    private State stateOf(ServerPlayer player) {
        return states.computeIfAbsent(player.getUUID(), id -> {
            State state = new State();
            state.previousShift = player.isShiftKeyDown();
            state.lastX = player.getX();
            state.lastZ = player.getZ();
            return state;
        });
    }

    private static double horizontalDistance(ServerPlayer player, double x, double z) {
        double dx = player.getX() - x;
        double dz = player.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
