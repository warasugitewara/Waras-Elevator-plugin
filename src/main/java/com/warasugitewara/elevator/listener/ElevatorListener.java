package com.warasugitewara.elevator.listener;

import com.warasugitewara.elevator.config.ConfigManager;
import com.warasugitewara.elevator.service.BukkitColumnAccessor;
import com.warasugitewara.elevator.service.ColumnAccessor;
import com.warasugitewara.elevator.service.Direction;
import com.warasugitewara.elevator.service.ElevatorService;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ElevatorListener implements Listener {

  private record KeyState(boolean jump, boolean sneak) {
  }

  private final Plugin plugin;
  private final ConfigManager configManager;
  // 直前に受信したジャンプ/スニークキーの押下状態。押した瞬間(false→true)だけを検出するために保持する。
  // Foliaではプレイヤーごとに別のリージョンスレッドからイベントが来るためスレッドセーフなMapにする。
  private final Map<UUID, KeyState> keyStates = new ConcurrentHashMap<>();

  public ElevatorListener(Plugin plugin, ConfigManager configManager) {
    this.plugin = plugin;
    this.configManager = configManager;
  }

  // 昇降はキーを「押した瞬間」だけで判定する(1押下=1階)。押下状態やY座標の変化を監視する方式と
  // 違い、長押しや残った上昇速度で中間階を飛ばすことがなく、クールダウンなしで連打に即応できる。
  // 段差(ハーフブロック/階段)を登ってYが上がるだけではジャンプキーが押されないため反応しない。
  @EventHandler
  public void onInput(PlayerInputEvent event) {
    Player player = event.getPlayer();
    Input input = event.getInput();
    KeyState previous = keyStates.put(player.getUniqueId(), new KeyState(input.isJump(), input.isSneak()));
    boolean jumpPressed = input.isJump() && (previous == null || !previous.jump());
    boolean sneakPressed = input.isSneak() && (previous == null || !previous.sneak());
    if (jumpPressed == sneakPressed) {
      return; // どちらも押されていない、または同時押しで方向が決まらない
    }
    if (input.isForward() || input.isBackward() || input.isLeft() || input.isRight()) {
      return; // 移動しながらのジャンプ/スニークは通常の操作として扱い、乗降と見なさない
    }
    if (!player.hasPermission("elevator.use")) {
      return;
    }
    Block floor = elevatorFloorBelow(player.getLocation());
    if (floor == null) {
      return;
    }
    Direction direction = jumpPressed ? Direction.UP : Direction.DOWN;
    triggerMove(player, direction, floor);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    keyStates.remove(event.getPlayer().getUniqueId());
  }

  // 足元のエレベーターブロックを返す。連打中はテレポート直後のジャンプで少し浮いていることが
  // あるため、真下が通過可能(空中)な場合に限り、もう1ブロック下までを同じ床として扱う。
  private Block elevatorFloorBelow(Location location) {
    Block below = location.getBlock().getRelative(0, -1, 0);
    if (isElevatorBlock(below)) {
      return below;
    }
    if (!below.isPassable()) {
      return null;
    }
    Block twoBelow = below.getRelative(0, -1, 0);
    return isElevatorBlock(twoBelow) ? twoBelow : null;
  }

  private boolean isElevatorBlock(Block block) {
    return configManager.getBlocks().containsKey(block.getType());
  }

  // 移動先の階が見つからない場合は何もしない。
  private void triggerMove(Player player, Direction direction, Block floor) {
    World world = player.getWorld();
    Location loc = player.getLocation();

    // 探索する柱とテレポート先のX/Zを床ブロックの座標に統一する。ブロック中心(+0.5)へ
    // 送ることで、幅0.6のプレイヤー当たり判定が必ず安全チェック済みの1柱内(0.2〜0.8)に収まり、
    // 隣接する壁へのめり込み(窒息)が構造的に起きなくなる。
    int bx = floor.getX();
    int bz = floor.getZ();
    ColumnAccessor column = new BukkitColumnAccessor(world, bx, bz);
    OptionalInt result = ElevatorService.findNextFloor(
        configManager.getBlocks(),
        column,
        floor.getY(),
        world.getMinHeight(),
        world.getMaxHeight() - 1,
        direction);
    if (result.isEmpty()) {
      return;
    }

    int floorY = result.getAsInt();
    Location destination = new Location(world, bx + 0.5, floorY + 1, bz + 0.5, loc.getYaw(), loc.getPitch());

    // Foliaでは同期teleportが使えないためteleportAsyncを使う。Paperでは移動先チャンクが
    // ロード済みなら即時完了するので、完了後の処理もそのままこのスレッドで実行される。
    // Foliaで完了スレッドがプレイヤーを所有していない場合のみエンティティスケジューラへ回す。
    player.teleportAsync(destination).thenAccept(success -> {
      if (!success) {
        return;
      }
      if (Bukkit.isOwnedByCurrentRegion(player)) {
        afterTeleport(player, direction);
      } else {
        player.getScheduler().run(plugin, task -> afterTeleport(player, direction), null);
      }
    });
  }

  private void afterTeleport(Player player, Direction direction) {
    if (direction == Direction.UP) {
      // ジャンプの上昇速度が残ると移動先で浮き上がるため垂直成分のみリセットする
      // (水平速度は維持。全ゼロ化すると急停止し拘束感が出る)。
      Vector velocity = player.getVelocity();
      player.setVelocity(velocity.setY(0));
    }
    playSound(player, direction);
  }

  private void playSound(Player player, Direction direction) {
    if (!configManager.isSoundEnabled()) {
      return;
    }
    String soundKey = direction == Direction.UP
        ? configManager.getAscendSound()
        : configManager.getDescendSound();
    player.playSound(player.getLocation(), soundKey, 1.0f, 1.0f);
  }
}
