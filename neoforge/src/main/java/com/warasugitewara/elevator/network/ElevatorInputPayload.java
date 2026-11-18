package com.warasugitewara.elevator.network;

import com.warasugitewara.elevator.ElevatorMod;
import com.warasugitewara.elevator.service.Direction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * クライアント → サーバー: ジャンプ/しゃがみキーが「押された瞬間」を通知する。
 */
public record ElevatorInputPayload(Direction direction) implements CustomPacketPayload {

    public static final Type<ElevatorInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ElevatorMod.MOD_ID, "input"));

    public static final StreamCodec<ByteBuf, ElevatorInputPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(
            up -> new ElevatorInputPayload(up ? Direction.UP : Direction.DOWN),
            payload -> payload.direction() == Direction.UP);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
