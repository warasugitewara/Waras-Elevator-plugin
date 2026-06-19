package com.warasugitewara.elevator.service;

import org.bukkit.Material;

/**
 * 1つの(x, z)カラムにおける、指定Y座標のブロック情報への読み取り専用アクセス。
 * 実装はBukkitワールド（本番）またはフェイクデータ（テスト）。
 */
public interface ColumnAccessor {
    Material materialAt(int y);

    boolean isPassableAt(int y);
}
