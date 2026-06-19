package com.warasugitewara.elevator.service;

import org.bukkit.Material;

import java.util.Map;
import java.util.OptionalInt;

public final class ElevatorService {

	private ElevatorService() {
	}

	public static OptionalInt findNextFloor(
		Map<Material, Integer> blockConfig,
		ColumnAccessor column,
		int currentY,
		int minY,
		int maxY,
		Direction direction
	) {
		Material base = column.materialAt(currentY);
		Integer maxGap = blockConfig.get(base);
		if (maxGap == null) {
			return OptionalInt.empty();
		}

		int step = direction == Direction.UP ? 1 : -1;

		int y = currentY;
		while (inBounds(y + step, minY, maxY) && column.materialAt(y + step) == base) {
			y += step;
		}

		int gap = 0;
		int cursor = y;
		while (true) {
			cursor += step;
			if (!inBounds(cursor, minY, maxY))
				return OptionalInt.empty();

			Material material = column.materialAt(cursor);
			if (blockConfig.containsKey(material)) {
				if (isSafeLanding(column, cursor, minY, maxY))
					return OptionalInt.of(cursor);
			} else {
				gap++;
				if (gap > maxGap) {
					return OptionalInt.empty();
				}
			}
		}
	}

	private static boolean isSafeLanding(ColumnAccessor column, int floorY, int minY, int maxY) {
		int feetY = floorY + 1;
		int headY = floorY + 2;
		if (!inBounds(headY, minY, maxY))
			return false;
		return column.isPassableAt(feetY) && column.isPassableAt(headY);
	}

	private static boolean inBounds(int y, int minY, int maxY) {
		return y >= minY && y <= maxY;
	}
}
