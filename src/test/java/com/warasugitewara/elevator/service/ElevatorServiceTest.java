package com.warasugitewara.elevator.service;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevatorServiceTest {

	private static final Map<Material, Integer> BLOCK_CONFIG = Map.of(
		Material.IRON_BLOCK, 15,
		Material.DIAMOND_BLOCK, 30,
		Material.NETHERITE_BLOCK, 90
	);

	static class FakeColumn implements ColumnAccessor {

		private final Map<Integer, Material> materials = new HashMap<>();
		private final Set<Integer> passable = new HashSet<>();

		FakeColumn fill(int minY, int maxY, Material material) {
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

		@Override
		public Material materialAt(int y) {
			return materials.getOrDefault(y, Material.AIR);
		}

		@Override
		public boolean isPassableAt(int y) {
			return passable.contains(y);
		}
	}

	@Test
	void returnsEmptyWhenCurrentBlockIsNotInConfig() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, Material.STONE);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void findsSingleFloorAboveWithSafeLanding() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, Material.IRON_BLOCK)
			.fill(70, 72, Material.IRON_BLOCK)
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}

	@Test
	void returnsEmptyWhenGapExceedsMaxGap() {
		// y=60に鉄ブロック、y=77に次の鉄ブロック。間は61..76の16個＞max-gap(15)なので失敗。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, Material.IRON_BLOCK)
			.fill(77, 77, Material.IRON_BLOCK)
			.setPassable(78, 79);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void findsFloorExactlyAtMaxGapBoundary() {
		// y=60に鉄ブロック、y=76に次の鉄ブロック。間の空気は61..75の15個＝max-gap(15)と同数なので成功。
		FakeColumn column = new FakeColumn()
			.fill(60, 60, Material.IRON_BLOCK)
			.fill(76, 76, Material.IRON_BLOCK)
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
			.fill(60, 60, Material.IRON_BLOCK)
			.fill(65, 65, Material.IRON_BLOCK)
			.fill(66, 67, Material.STONE) // blocked landing
			.fill(70, 70, Material.IRON_BLOCK)
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}

	@Test
	void searchesDownwardsWhenDirectionIsDown() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, Material.IRON_BLOCK)
			.fill(45, 45, Material.IRON_BLOCK)
			.setPassable(46, 47);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.DOWN);

		assertTrue(result.isPresent());
		assertEquals(45, result.getAsInt());
	}

	@Test
	void returnsEmptyWhenNoFloorFoundOutOfBounds() {
		FakeColumn column = new FakeColumn()
			.fill(60, 60, Material.IRON_BLOCK);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void stopsSearchWhenReachingBoundaryDuringMultiBlockTraversal() {
		FakeColumn column = new FakeColumn()
			.fill(250, 255, Material.IRON_BLOCK); // starts at max boundary

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 250, 0, 255, Direction.UP);

		assertTrue(result.isEmpty());
	}

	@Test
	void skipsContiguousRunOfSameMaterialBeforeSearching() {
		// y=60-62 鉄ブロック3段連続。y=70 に次の鉄ブロック1段。間は空気で通行可能。
		// currentYは連続区間の最下段(60)とする。連続区間全体をスキップしてから
		// 探索を始め、70が見つかることを検証する。
		FakeColumn column = new FakeColumn()
			.fill(60, 62, Material.IRON_BLOCK)
			.fill(70, 70, Material.IRON_BLOCK)
			.setPassable(71, 72);

		OptionalInt result = ElevatorService.findNextFloor(BLOCK_CONFIG, column, 60, 0, 255, Direction.UP);

		assertTrue(result.isPresent());
		assertEquals(70, result.getAsInt());
	}
}
