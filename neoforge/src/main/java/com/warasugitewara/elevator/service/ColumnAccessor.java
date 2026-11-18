package com.warasugitewara.elevator.service;

/**
 * 1つの(x, z)カラムにおける、指定Y座標のブロック情報への読み取り専用アクセス。
 * 実装はワールド（本番）またはフェイクデータ（テスト）。ブロック種別の型はTで抽象化し、
 * 探索ロジックをMinecraft本体に依存させずに単体テストできるようにしている。
 */
public interface ColumnAccessor<T> {
    T blockAt(int y);

    boolean isPassableAt(int y);

    boolean isLiquidAt(int y);
}
