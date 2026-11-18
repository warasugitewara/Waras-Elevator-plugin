package com.warasugitewara.elevator.server;

import com.warasugitewara.elevator.ElevatorPermissions;
import com.warasugitewara.elevator.config.ElevatorConfig;
import com.warasugitewara.elevator.service.Direction;
import com.warasugitewara.elevator.service.ElevatorService;
import com.warasugitewara.elevator.service.LevelColumnAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 昇降の実行(足元判定・次の階の探索・テレポート・サウンド)。サーバースレッドから呼び出す。
 */
public class ElevatorMover {

    private final ElevatorConfig config;
    // 不正なクライアントが入力パケットを連打して1tick内に何階も移動するのを防ぐため、
    // 1プレイヤーあたり1tickに1回だけ移動を許可する。
    private final Map<UUID, Integer> lastMoveTick = new ConcurrentHashMap<>();

    public ElevatorMover(ElevatorConfig config) {
        this.config = config;
    }

    /**
     * @param basePosition 足元のエレベーターブロックを探す基準位置(通常はプレイヤーの現在位置)
     * @return 移動した場合 true
     */
    public boolean tryMove(ServerPlayer player, Direction direction, Vec3 basePosition) {
        if (player.isPassenger() || player.isSpectator() || player.isSleeping()) {
            return false;
        }
        int tick = player.server.getTickCount();
        Integer last = lastMoveTick.get(player.getUUID());
        if (last != null && last == tick) {
            return false;
        }
        if (!ElevatorPermissions.canUse(player)) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos floor = elevatorFloorBelow(level, BlockPos.containing(basePosition));
        if (floor == null) {
            return false;
        }

        // 探索する柱とテレポート先のX/Zを床ブロックの座標に統一する。ブロック中心(+0.5)へ
        // 送ることで、幅0.6のプレイヤー当たり判定が必ず安全チェック済みの1柱内に収まり、
        // 隣接する壁へのめり込み(窒息)が構造的に起きなくなる。
        int bx = floor.getX();
        int bz = floor.getZ();
        OptionalInt result = ElevatorService.findNextFloor(
                config.getBlocks(),
                new LevelColumnAccessor(level, bx, bz),
                floor.getY(),
                level.getMinBuildHeight(),
                level.getMaxBuildHeight() - 1,
                direction);
        if (result.isEmpty()) {
            return false; // 最上階・最下階などでは通常のジャンプ/しゃがみとして扱う
        }

        lastMoveTick.put(player.getUUID(), tick);
        int floorY = result.getAsInt();
        // 絶対座標でのテレポートはクライアント側の速度を0にするため、ジャンプの上昇速度が
        // 移動先に持ち越されて浮き上がることはない。
        player.connection.teleport(bx + 0.5, floorY + 1, bz + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        playSound(player, direction);
        return true;
    }

    public void forget(UUID playerId) {
        lastMoveTick.remove(playerId);
    }

    // 足元のエレベーターブロックを返す。連打中はテレポート直後のジャンプで少し浮いていることが
    // あるため、真下が通過可能(空中)な場合に限り、もう1ブロック下までを同じ床として扱う。
    private BlockPos elevatorFloorBelow(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        if (isElevatorBlock(level, below)) {
            return below;
        }
        if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return null;
        }
        BlockPos twoBelow = below.below();
        return isElevatorBlock(level, twoBelow) ? twoBelow : null;
    }

    private boolean isElevatorBlock(ServerLevel level, BlockPos pos) {
        return config.getBlocks().containsKey(level.getBlockState(pos).getBlock());
    }

    private void playSound(ServerPlayer player, Direction direction) {
        if (!config.isSoundEnabled()) {
            return;
        }
        String key = direction == Direction.UP ? config.getAscendSound() : config.getDescendSound();
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null) {
            return;
        }
        // 未登録のIDでも(リソースパックのサウンド等)クライアントへはIDとして送られる。
        // playNotifySound は本人にだけ聞こえる(Paper版の Player#playSound と同じ挙動)。
        player.playNotifySound(SoundEvent.createVariableRangeEvent(id), SoundSource.MASTER, 1.0f, 1.0f);
    }
}
