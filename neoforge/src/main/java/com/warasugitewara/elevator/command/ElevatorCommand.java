package com.warasugitewara.elevator.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.warasugitewara.elevator.ElevatorPermissions;
import com.warasugitewara.elevator.config.ElevatorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.Optional;

/**
 * /elevator (エイリアス: /el)。Paper版と同じサブコマンド構成。
 */
public final class ElevatorCommand {

    private ElevatorCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext,
                                ElevatorConfig config) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("elevator")
                .requires(ElevatorPermissions::canAdmin)
                .executes(ctx -> usage(ctx.getSource()))
                .then(Commands.literal("block")
                        .executes(ctx -> fail(ctx.getSource(), "使い方: /elevator block <add|remove|list> ..."))
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                        .then(Commands.argument("max-gap", IntegerArgumentType.integer(0))
                                                .executes(ctx -> addBlock(ctx, config)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                config.getBlocks().keySet().stream().map(BuiltInRegistries.BLOCK::getKey),
                                                builder))
                                        .executes(ctx -> removeBlock(ctx, config))))
                        .then(Commands.literal("list")
                                .executes(ctx -> listBlocks(ctx.getSource(), config))))
                .then(Commands.literal("sound")
                        .executes(ctx -> fail(ctx.getSource(), "使い方: /elevator sound <on|off>"))
                        .then(Commands.literal("on").executes(ctx -> setSound(ctx.getSource(), config, true)))
                        .then(Commands.literal("off").executes(ctx -> setSound(ctx.getSource(), config, false))))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource(), config)))
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource(), config))));

        // 引数なしの /el でも使い方を表示できるよう executes も付ける
        dispatcher.register(Commands.literal("el")
                .requires(ElevatorPermissions::canAdmin)
                .executes(ctx -> usage(ctx.getSource()))
                .redirect(root));
    }

    private static int addBlock(CommandContext<CommandSourceStack> ctx, ElevatorConfig config) throws CommandSyntaxException {
        Block block = ResourceArgument.getResource(ctx, "block", Registries.BLOCK).value();
        int maxGap = IntegerArgumentType.getInteger(ctx, "max-gap");
        if (block == Blocks.AIR) {
            return fail(ctx.getSource(), "空気は登録できません。");
        }
        config.addOrUpdateBlock(block, maxGap);
        return success(ctx.getSource(), ElevatorConfig.blockId(block) + " をmax-gap=" + maxGap + "で登録しました。");
    }

    private static int removeBlock(CommandContext<CommandSourceStack> ctx, ElevatorConfig config) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "block");
        Optional<Block> block = ElevatorConfig.parseBlock(id.toString());
        if (block.isEmpty()) {
            return fail(ctx.getSource(), "ブロック種類が不正です: " + id);
        }
        String name = ElevatorConfig.blockId(block.get());
        return config.removeBlock(block.get())
                ? success(ctx.getSource(), name + " を削除しました。")
                : fail(ctx.getSource(), name + " は登録されていません。");
    }

    private static int listBlocks(CommandSourceStack source, ElevatorConfig config) {
        send(source, "エレベーター対象ブロック一覧:", ChatFormatting.YELLOW);
        sendBlockLines(source, config);
        return 1;
    }

    private static int setSound(CommandSourceStack source, ElevatorConfig config, boolean enabled) {
        config.setSoundEnabled(enabled);
        return success(source, "サウンドを" + (enabled ? "有効" : "無効") + "にしました。");
    }

    private static int info(CommandSourceStack source, ElevatorConfig config) {
        send(source, "=== Wara's Elevators 設定 ===", ChatFormatting.YELLOW);
        send(source, "サウンド: " + (config.isSoundEnabled() ? "有効" : "無効"), ChatFormatting.GRAY);
        send(source, "上昇音: " + config.getAscendSound(), ChatFormatting.GRAY);
        send(source, "下降音: " + config.getDescendSound(), ChatFormatting.GRAY);
        send(source, "Mod未導入クライアント対応: " + (config.isVanillaClientSupport() ? "有効" : "無効"), ChatFormatting.GRAY);
        sendBlockLines(source, config);
        return 1;
    }

    private static int reload(CommandSourceStack source, ElevatorConfig config) {
        return config.load()
                ? success(source, config.getPath().getFileName() + " を再読込しました。")
                : fail(source, config.getPath().getFileName() + " の読み込みに失敗しました。ログを確認してください。");
    }

    private static int usage(CommandSourceStack source) {
        send(source, "/elevator block <add|remove|list>", ChatFormatting.YELLOW);
        send(source, "/elevator sound <on|off>", ChatFormatting.YELLOW);
        send(source, "/elevator info", ChatFormatting.YELLOW);
        send(source, "/elevator reload", ChatFormatting.YELLOW);
        return 1;
    }

    private static void sendBlockLines(CommandSourceStack source, ElevatorConfig config) {
        for (Map.Entry<Block, Integer> entry : config.getBlocks().entrySet()) {
            send(source, "- " + ElevatorConfig.blockId(entry.getKey()) + ": max-gap=" + entry.getValue(), ChatFormatting.GRAY);
        }
    }

    private static void send(CommandSourceStack source, String message, ChatFormatting color) {
        source.sendSystemMessage(Component.literal(message).withStyle(color));
    }

    private static int success(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
