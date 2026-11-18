package com.warasugitewara.elevator;

import com.mojang.logging.LogUtils;
import com.warasugitewara.elevator.command.ElevatorCommand;
import com.warasugitewara.elevator.config.ElevatorConfig;
import com.warasugitewara.elevator.network.ElevatorInputPayload;
import com.warasugitewara.elevator.server.ElevatorMover;
import com.warasugitewara.elevator.server.VanillaClientInputTracker;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;

@Mod(ElevatorMod.MOD_ID)
public class ElevatorMod {

    public static final String MOD_ID = "waraselevators";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final ElevatorConfig config;
    private final ElevatorMover mover;

    public ElevatorMod(IEventBus modBus) {
        config = new ElevatorConfig(FMLPaths.CONFIGDIR.get().resolve(MOD_ID + ".json"), LOGGER);
        mover = new ElevatorMover(config);
        VanillaClientInputTracker tracker = new VanillaClientInputTracker(config, mover);

        modBus.addListener(this::onRegisterPayloads);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onGatherPermissions);
        tracker.register(NeoForge.EVENT_BUS);
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): クライアントに本Modが無くても(バニラクライアントでも)接続できるようにする。
        // その場合はサーバー側の推定検出(VanillaClientInputTracker)で動作する。
        event.registrar("1")
                .optional()
                .playToServer(ElevatorInputPayload.TYPE, ElevatorInputPayload.STREAM_CODEC, this::handleInput);
    }

    // 既定の HandlerThread.MAIN により、このハンドラはサーバースレッドで実行される。
    private void handleInput(ElevatorInputPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            mover.tryMove(player, payload.direction(), player.position());
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        config.load();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ElevatorCommand.register(event.getDispatcher(), event.getBuildContext(), config);
    }

    private void onGatherPermissions(PermissionGatherEvent.Nodes event) {
        event.addNodes(ElevatorPermissions.USE, ElevatorPermissions.ADMIN);
    }
}
