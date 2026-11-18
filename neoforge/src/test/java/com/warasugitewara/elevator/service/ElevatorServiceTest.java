package com.warasugitewara.elevator.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevatorServiceTest {

	private static final Map<String, Integer> BLOCK_CONFIG = Map.of(
		"iron_block", 15,
		"diamond_block", 30,
		"netherite_block", 90
	);

	// ブロック種別を文字列で表すフェイク。探索ロジックはMinecraft本体に依存しないため起動なしでテストできる。
	static class FakeColumn implements ColumnAccessor<String> {

		private final Map<Integer, String> materials = new HashMap<>();
		private final Set<Integer> passable = new HashSet<>();
		private final Set<Integer> liquid = new HashSet<>();

		FakeColumn fill(int minY, int maxY, String material) {
			for (int y = minY; y <= maxY; y++) {
				materials.put(y, material);
			}
			return this;
		}

		FakeColumn setPassable(int minY, int maxY) {
			for (int y = minY; y <= maxY; y++) {
				passable.add(y);
			}
			return this;
		}

		FakeColumn setLiquid(int minY, int maxY) {
			for (int y = minY; y <= maxY; y++) {
				liquid.add(y);
			}
			return this;
		}

		@Override
		public String blockAt(int y) {
			return materials.getOrDefault(y, "air");
		}

		@Override
		public boolean isPassableAt(int y) {
			return passable.contains(y);
		}

		@Override
		public boolean isLiquidAt(int y) {
			return liquid.contains(y);
		}
	}

	@Test
	void returnsEmptyWhenCurrentBlockIsNotInConfig() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "stone");

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void findsSingleFloorAboveWithSafeLanding() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(70, 72, "iron_block")
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}

	@Test
	void returnsEmptyWhenGapExceedsMaxGap() {
		// y=60に鉄ブロック、y=77に次の鉄ブロック。間は61..76の16個＞max-gap(15)なので失敗。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(77, 77, "iron_block")
			.setPassable(78, 79);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void findsFloorExactlyAtMaxGapBoundary() {
		// y=60に鉄ブロック、y=76に次の鉄ブロック。間の空気は61..75の15個＝max-gap(15)と同数なので成功。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(76, 76, "iron_block")
			.setPassable(77, 78);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(76, result.getAsInt());
	}

	@Test
	void skipsBlockedFloorAndFindsFurtherSafeFloor() {
		// y=60に鉄ブロック。y=65に次の鉄ブロックがあるが、その上(66,67)が塞がっている。
		// さらにy=70に鉄ブロックがあり、その上(71,72)は通行可能。70が返るべき。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(65, 65, "iron_block")
			.fill(66, 67, "stone") // blocked landing
			.fill(70, 70, "iron_block")
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}

	@Test
	void searchesDownwardsWhenDirectionIsDown() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(45, 45, "iron_block")
			.setPassable(46, 47);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.DOWN);

		assertTrue(result.isPresent());
		assertEquals(45, result.getAsInt());
	}

	@Test
	void returnsEmptyWhenNoFloorFoundOutOfBounds() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block");

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void stopsSearchWhenReachingBoundaryDuringMultiBlockTraversal() {
		FakeColumn column = new FakeColumn()
			.fill(250, 255, "iron_block"); // starts at max boundary

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 250, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void skipsContiguousRunOfSameMaterialBeforeSearching() {
		// y=60-62 鉄ブロック3段連続。y=61, y=62 の直上(62,63 および 63,64)は通行可能にする。
		// スキップ処理がなければ cursor=61 で IRON_BLOCK かつ isSafeLanding(61)=passable(62)&&passable(63)=true
		// となり 61 が誤って返ってしまう。スキップ処理が正しく機能すれば、連続区間(60-62)全体を
		// 飛び越えてから探索するため、61は候補にならず、70 が返るべき。
		FakeColumn column = new FakeColumn()
			.fill(60, 62, "iron_block")
			.fill(70, 70, "iron_block")
			.setPassable(62, 64)
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}

	@Test
	void skipsLiquidLandingAndFindsFurtherSafeFloor() {
		// y=60に鉄ブロック。y=65に次の鉄ブロックがあるが、その足元(66)が液体(水/溶岩)。
		// 液体はisPassable()=trueだが着地不可なのでスキップし、さらに上のy=70(71,72が安全)を返す。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(65, 65, "iron_block")
			.setPassable(66, 67)
			.setLiquid(66, 66)
			.fill(70, 70, "iron_block")
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}

	@Test
	void returnsEmptyWhenOnlyFloorHasLiquidLanding() {
		// 唯一の着地候補y=70の頭上(72)が液体。他に安全な階がないためemptyを返す。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(70, 70, "iron_block")
			.setPassable(71, 72)
			.setLiquid(72, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void skipsContiguousRunOfMixedMaterialsBeforeSearching() {
		// y=60鉄ブロック、y=61ダイヤブロック(いずれも登録済み)が連続。連続部分は1つの床の厚みとして
		// 一括スキップされるべき。スキップが同種のみだと cursor=61 でダイヤに着地判定が通り(62,63が安全)
		// 61が誤って返る。異種も含めてスキップすれば61は候補にならず、70が返るべき。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, "iron_block")
			.fill(61, 61, "diamond_block")
			.fill(70, 70, "iron_block")
			.setPassable(62, 63)
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}
}
