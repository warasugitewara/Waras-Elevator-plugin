package com.warasugitewara.elevator.service;

import java.util.Map;
import java.util.OptionalInt;

/**
 * 次の階を探索する純粋ロジック。Paper版の ElevatorService と同じアルゴリズム。
 */
public final class ElevatorService {

    private ElevatorService() {
    }

    public static <T> OptionalInt findNextFloor(
            Map<T, Integer> blockConfig,
            ColumnAccessor<T> column,
            int currentY,
            int minY,
            int maxY,
            Direction direction
    ) {
        T base = column.blockAt(currentY);
        Integer maxGap = blockConfig.get(base);
        if (maxGap == null) {
            return OptionalInt.empty();
        }

        int step = direction == Direction.UP ? 1 : -1;

        // 足元から連続する登録ブロックは(同種・異種を問わず)1つの床の厚みとして一括でスキップする。
        // maxGapは足元のブロック(base)の設定値を使う。
        int y = currentY;
        while (inBounds(y + step, minY, maxY) && blockConfig.containsKey(column.blockAt(y + step))) {
            y += step;
        }

        int gap = 0;
        int cursor = y;
        while (true) {
            cursor += step;
            if (!inBounds(cursor, minY, maxY)) {
                return OptionalInt.empty();
            }

            T block = column.blockAt(cursor);
            if (blockConfig.containsKey(block)) {
                if (isSafeLanding(column, cursor, minY, maxY)) {
                    return OptionalInt.of(cursor);
                }
            } else {
                gap++;
                if (gap > maxGap) {
                    return OptionalInt.empty();
                }
            }
        }
    }

    private static boolean isSafeLanding(ColumnAccessor<?> column, int floorY, int minY, int maxY) {
        int feetY = floorY + 1;
        int headY = floorY + 2;
        if (!inBounds(headY, minY, maxY)) {
            return false;
        }
        // 当たり判定が空でも液体(水・溶岩)の中には着地させない。
        if (column.isLiquidAt(feetY) || column.isLiquidAt(headY)) {
            return false;
        }
        return column.isPassableAt(feetY) && column.isPassableAt(headY);
    }

    private static boolean inBounds(int y, int minY, int maxY) {
        return y >= minY && y <= maxY;
    }
}
