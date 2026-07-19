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

		// 足元から連続する登録ブロックは(同種・異種を問わず)1つの床の厚みとして一括でスキップする。
		// maxGapは足元のブロック(base)の設定値を使う。
		int y = currentY;
		while (inBounds(y + step, minY, maxY) && blockConfig.containsKey(column.materialAt(y + step))) {
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
		// isPassable()は水・溶岩でもtrueを返すため、液体への着地を別途拒否する。
		if (column.isLiquidAt(feetY) || column.isLiquidAt(headY))
			return false;
		return column.isPassableAt(feetY) && column.isPassableAt(headY);
	}

	private static boolean inBounds(int y, int minY, int maxY) {
		return y >= minY && y <= maxY;
	}
}
