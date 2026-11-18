package com.warasugitewara.elevator.client;

import com.warasugitewara.elevator.ElevatorMod;
import com.warasugitewara.elevator.network.ElevatorInputPayload;
import com.warasugitewara.elevator.service.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * クライアント側エントリポイント(物理クライアントでのみ読み込まれる)。
 * <p>
 * ジャンプ/しゃがみキーを「押した瞬間」(false→true)だけを検出してサーバーへ送る(1押下=1階)。
 * 長押しでは連続移動せず、移動キーを押しながらの操作は乗降と見なさない(Paper版と同じ操作感)。
 */
@Mod(value = ElevatorMod.MOD_ID, dist = Dist.CLIENT)
public class ElevatorClient {

    private boolean previousJump;
    private boolean previousSneak;

    public ElevatorClient() {
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            previousJump = false;
            previousSneak = false;
            return;
        }

        Input input = player.input;
        boolean jumpPressed = input.jumping && !previousJump;
        boolean sneakPressed = input.shiftKeyDown && !previousSneak;
        previousJump = input.jumping;
        previousSneak = input.shiftKeyDown;

        if (jumpPressed == sneakPressed) {
            return; // どちらも押されていない、または同時押しで方向が決まらない
        }
        if (input.up || input.down || input.left || input.right) {
            return; // 移動しながらのジャンプ/スニークは通常の操作として扱う
        }
        if (player.isPassenger() || player.isSpectator()) {
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(ElevatorInputPayload.TYPE)) {
            return; // サーバーに本Modが無い
        }
        PacketDistributor.sendToServer(new ElevatorInputPayload(jumpPressed ? Direction.UP : Direction.DOWN));
    }
}
