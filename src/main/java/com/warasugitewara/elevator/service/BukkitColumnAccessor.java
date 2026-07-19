package com.warasugitewara.elevator.service;

import org.bukkit.Material;
import org.bukkit.World;

public class BukkitColumnAccessor implements ColumnAccessor {

    private final World world;
    private final int x;
    private final int z;

    public BukkitColumnAccessor(World world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    @Override
    public Material materialAt(int y) {
        return world.getBlockAt(x, y, z).getType();
    }

    @Override
    public boolean isPassableAt(int y) {
        return world.getBlockAt(x, y, z).isPassable();
    }

    @Override
    public boolean isLiquidAt(int y) {
        return world.getBlockAt(x, y, z).isLiquid();
    }
}
