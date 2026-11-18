package com.warasugitewara.elevator.service;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class LevelColumnAccessor implements ColumnAccessor<Block> {

    private final Level level;
    private final BlockPos.MutableBlockPos pos;

    public LevelColumnAccessor(Level level, int x, int z) {
        this.level = level;
        this.pos = new BlockPos.MutableBlockPos(x, 0, z);
    }

    private BlockState stateAt(int y) {
        return level.getBlockState(pos.setY(y));
    }

    @Override
    public Block blockAt(int y) {
        return stateAt(y).getBlock();
    }

    // Bukkitの Block#isPassable() と同じく「当たり判定が空」を通過可能とみなす。
    @Override
    public boolean isPassableAt(int y) {
        return stateAt(y).getCollisionShape(level, pos.setY(y)).isEmpty();
    }

    // 水・溶岩(水浸しブロックを含む)を液体とみなす。
    @Override
    public boolean isLiquidAt(int y) {
        return !stateAt(y).getFluidState().isEmpty();
    }
}
