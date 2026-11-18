# Wara's Elevators

指定したブロックを縦に積んだシャフトの上に乗り、ジャンプ/しゃがみで上下の階に移動できる軽量エレベーター。
**Paper / Folia プラグイン版**と **NeoForge Mod 版**があります。

| 版 | 対応環境 | Java | ソース |
| --- | --- | --- | --- |
| Paper プラグイン | Paper 26.2 / Folia | 25 | リポジトリ直下 (`src/`) |
| NeoForge Mod | Minecraft 1.21.1 + NeoForge 21.1.267 以降 | 21 | [`neoforge/`](neoforge/) |

## 特徴

- ジャンプで上昇、しゃがみで下降。キーを1回押すごとに1階移動します(長押しでは連続移動しません。連打で連続移動)。
- 移動キーを押しながらのジャンプ/しゃがみや、段差を登るだけでは反応しないため誤作動しにくい設計です。
- ブロック種別ごとに到達可能な最大ギャップ(`max-gap`)を設定可能。ギャップを超える/対象外ブロックの階は自動でスキップ。
- 移動先の真上2マスが塞がっている階・液体がある階には着地しません(窒息・水没防止)。
- `/elevator` コマンドで再起動なしに設定変更可能。
- 上昇/下降時のサウンドをON/OFF・種類変更可能。

## 使い方

対象ブロック(デフォルトは鉄・ダイヤモンド・ネザライトブロック)を縦に積んだ柱の上に立ち、

- **ジャンプ** → 上の階へ移動
- **しゃがみ** → 下の階へ移動

移動先の階が `max-gap` の範囲内に見つからない場合(最上階・最下階など)は通常のジャンプ/しゃがみとして扱われます。
連続して積んだ対象ブロック(種類が混ざっていても可)は1つの床として扱われます。

## インストール

[Releases](https://github.com/warasugitewara/Waras-Elevator-plugin/releases) から使用する版の jar をダウンロードしてください。

### Paper / Folia

1. `WarasElevators-<version>.jar` をサーバーの `plugins/` フォルダに配置。
2. サーバーを再起動(または起動)。

### NeoForge 1.21.1

1. `WarasElevators-NeoForge-1.21.1-<version>.jar` を `mods/` フォルダに配置。
   - **マルチプレイ**: サーバーへの導入は必須です。クライアントへの導入は任意ですが推奨です(下記参照)。
   - **シングルプレイ**: クライアントの `mods/` に入れるだけで動作します。
2. ゲーム/サーバーを起動。

#### クライアントに Mod を入れない場合の動作

Minecraft 1.21.1 ではキー入力の状態がサーバーへ送られないため、クライアントに Mod が入っていないプレイヤー(バニラクライアントを含む)はサーバー側の推定で検出します。
Mod 未導入のままでもサーバーに接続・利用できますが、以下の違いがあります。

| | クライアント導入あり | クライアント未導入 |
| --- | --- | --- |
| 上昇の検出 | ジャンプキーを押した瞬間 | サーバーがジャンプを検知した瞬間 |
| ジャンプ長押し | 連続移動しない | バニラの自動連続ジャンプにより約0.5秒ごとに連続上昇する |
| 移動中の判定 | 移動キーの押下で判定 | そのtickの水平移動量で推定(氷の上で滑っている間などは反応しないことがある) |

この推定検出は `config/waraselevators.json` の `vanilla-client-support` を `false` にすると無効化できます(その場合、Mod を入れたクライアントだけが利用できます)。

## コマンド

`/elevator` (エイリアス: `/el`)。両版共通です。管理者権限(下記)が必要です。

| コマンド | 説明 |
| --- | --- |
| `/elevator block add <block> <max-gap>` | 対象ブロックを追加/更新 |
| `/elevator block remove <block>` | 対象ブロックを削除 |
| `/elevator block list` | 対象ブロック一覧を表示 |
| `/elevator sound <on\|off>` | 昇降サウンドの有効/無効を切替 |
| `/elevator info` | 現在の設定を表示 |
| `/elevator reload` | 設定ファイルを再読込 |

## 権限

| Paper | NeoForge | デフォルト | 説明 |
| --- | --- | --- | --- |
| `elevator.use` | `waraselevators.use` | 全員 | エレベーターの利用 |
| `elevator.admin` | `waraselevators.admin` | OP (NeoForge は権限レベル2以上) | `/elevator` での設定変更 |

NeoForge 版は NeoForge の PermissionAPI に権限ノードを登録しているため、対応する権限管理 Mod で制御できます。

## 設定

### Paper: `plugins/WarasElevators/config.yml`

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

### NeoForge: `config/waraselevators.json`

初回起動時に自動生成されます。項目は Paper 版と同じで、NeoForge 版のみ `vanilla-client-support` があります。

```json
{
  "blocks": [
    { "material": "minecraft:iron_block", "max-gap": 15 },
    { "material": "minecraft:diamond_block", "max-gap": 30 },
    { "material": "minecraft:netherite_block", "max-gap": 90 }
  ],
  "sound": {
    "enabled": true,
    "ascend": "minecraft:block.beacon.activate",
    "descend": "minecraft:block.beacon.deactivate"
  },
  "vanilla-client-support": true
}
```

| 項目 | 説明 |
| --- | --- |
| `blocks[].material` | 対象ブロックのID |
| `blocks[].max-gap` | その床から次の床までに許容する、対象ブロック以外のマスの最大数 |
| `sound.enabled` | 昇降サウンドの有効/無効 |
| `sound.ascend` / `sound.descend` | 上昇/下降時のサウンドID |
| `vanilla-client-support` | (NeoForge のみ) Mod 未導入クライアントをサーバー側の推定で検出するか |

## ビルド

どちらもリポジトリ直下の Gradle Wrapper でビルドできます。

### Paper 版 (JDK 25 が必要)

```bash
./gradlew build
```

`build/libs/WarasElevators-<version>.jar` が出力されます。

### NeoForge 版 (JDK 21 が必要)

```bash
./gradlew -p neoforge build
```

`neoforge/build/libs/WarasElevators-NeoForge-1.21.1-<version>.jar` が出力されます。
開発用のクライアント/サーバーは `./gradlew -p neoforge runClient` / `./gradlew -p neoforge runServer` で起動できます。

## リリース手順

1. `build.gradle.kts` の `version` と `neoforge/gradle.properties` の `mod_version` を同じ値に更新する。
2. `.github/release-notes/v<version>.md` にリリースノートを書く。
3. master にマージ(push)する。

GitHub Actions(`.github/workflows/build.yml`)が両方の jar をビルドし、`v<version>` のリリースを更新します。

- リリースが無い場合: タグとリリースを作成し、両方の jar を添付します(本文はリリースノート)。
- リリースが既にある場合: まだ添付されていない jar だけを追加し、リリースノートを本文の末尾に追記します。既存の jar は置き換えません。
