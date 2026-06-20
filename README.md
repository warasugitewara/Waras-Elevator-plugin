# Wara's Elevators

Paper (1.21.x) 向けの軽量エレベータープラグイン。指定したブロックを縦に積んだシャフトの上に乗り、ジャンプ/しゃがみで上下の階に移動できます。

## 特徴

- ジャンプで上昇、しゃがみで下降。連打・長押しのどちらでも反応します。
- ブロック種別ごとに到達可能な最大ギャップ(`max-gap`)を設定可能。ギャップを超える/対象外ブロックの階は自動でスキップ。
- `/elevator` コマンドで `/reload` 不要に設定変更可能。
- 上昇/下降時のサウンドをON/OFF・種類変更可能。

## インストール

1. [Releases](https://github.com/warasugitewara/Waras-Elevator-plugin/releases) から最新の jar をダウンロード。
2. サーバーの `plugins/` フォルダに配置。
3. サーバーを再起動(または起動)。

## 使い方

対象ブロック(デフォルトは鉄・ダイヤモンド・ネザライトブロック)を縦に積んだ柱の上に立ち、

- **ジャンプ** → 上の階へ移動
- **しゃがみ** → 下の階へ移動

移動先の階が `max-gap` の範囲内に見つからない場合(最上階・最下階など)は通常のジャンプ/しゃがみとして扱われます。

## コマンド

`/elevator` (エイリアス: `/el`)。`elevator.admin` 権限(デフォルト: OP)が必要です。

| コマンド | 説明 |
| --- | --- |
| `/elevator block add <material> <max-gap>` | 対象ブロックを追加/更新 |
| `/elevator block remove <material>` | 対象ブロックを削除 |
| `/elevator block list` | 対象ブロック一覧を表示 |
| `/elevator sound <on\|off>` | 昇降サウンドの有効/無効を切替 |
| `/elevator info` | 現在の設定を表示 |
| `/elevator reload` | `config.yml` を再読込 |

## 権限

| 権限 | デフォルト | 説明 |
| --- | --- | --- |
| `elevator.use` | 全員 | エレベーターの利用 |
| `elevator.admin` | OP | `/elevator` での設定変更 |

## 設定例 (`config.yml`)

```yaml
blocks:
  - material: minecraft:iron_block
    max-gap: 15
  - material: minecraft:diamond_block
    max-gap: 30
  - material: minecraft:netherite_block
    max-gap: 90
sound:
  enabled: true
  ascend: minecraft:block.beacon.activate
  descend: minecraft:block.beacon.deactivate
```

## 動作環境

- Paper 1.21.x
- Java 21

## ビルド

```bash
./gradlew build
```

`build/libs/WarasElevators-<version>.jar` が出力されます。
