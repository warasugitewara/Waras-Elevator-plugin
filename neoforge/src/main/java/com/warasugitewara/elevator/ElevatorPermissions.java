package com.warasugitewara.elevator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * NeoForge の PermissionAPI に登録する権限ノード。権限管理Modを入れていない場合は既定値が使われる。
 */
public final class ElevatorPermissions {

    /** waraselevators.use: エレベーターの利用(既定: 全員)。 */
    public static final PermissionNode<Boolean> USE = new PermissionNode<>(
            ElevatorMod.MOD_ID, "use", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> true);

    /** waraselevators.admin: /elevator での設定変更(既定: OP権限レベル2以上)。 */
    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(
            ElevatorMod.MOD_ID, "admin", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(2));

    private ElevatorPermissions() {
    }

    public static boolean canUse(ServerPlayer player) {
        return PermissionAPI.getPermission(player, USE);
    }

    public static boolean canAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            return PermissionAPI.getPermission(player, ADMIN);
        }
        // コンソール・コマンドブロック等
        return source.hasPermission(2);
    }
}
