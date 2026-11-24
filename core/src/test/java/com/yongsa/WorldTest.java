package com.yongsa;

import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Vein;
import com.yongsa.map.Door;
import com.yongsa.map.Link;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.VeinGenerator;
import com.yongsa.map.WorldMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    @Test
    void mapHasCampAndThreeDungeonsAndFinalRoom() {
        WorldMap map = TestData.map();
        assertNotNull(map.room("CAMP"));
        int boss = 0;
        for (Room r : map.rooms) if (r.type == RoomType.BOSS) boss++;
        assertEquals(3, boss);
        assertEquals(RoomType.FINAL, map.room("F").type);
        for (Door d : map.room("F").doors) {
            assertTrue(d.link.oneWay, "심연 계단은 캠프로 올라가기만 한다 (내려오는 길은 봉인 문양)");
            assertEquals("CAMP", d.partner().room.id);
        }
        assertEquals(RoomType.VILLAGE, map.room("CAMP").doors.stream()
                .filter(d -> d.name.equals("down")).findFirst().get().partner().room.type, "캠프 아래 문은 마을로 나간다");
        // 임시로 다시 쓴 그림(L2, L8, R4)에 남는 출구만 막혀 있다
        java.util.Set<String> spare = java.util.Set.of("L2.west", "L8.east", "R4.south");
        for (Room r : map.rooms)
            for (Door d : r.doors)
                assertEquals(spare.contains(r.id + "." + d.name), d.dead(), "그림 속 출구는 어딘가로 이어진다: " + r.id + "." + d.name);
        Room camp = map.room("CAMP");
        assertTrue(map.room("L1").x1 < camp.x0, "왼쪽 던전은 캠프 왼쪽");
        assertTrue(map.room("R1").x0 > camp.x1, "오른쪽 던전은 캠프 오른쪽");
        assertTrue(map.room("T1").y0 > camp.y1, "위쪽 던전은 캠프 위쪽");
    }

    @Test
    void gatesOpenWithProgress() {
        WorldMap map = TestData.map();
        GameState s = NewGame.create(TestData.balance());
        map.updateGates(s);
        assertFalse(gate(map, "LEFT").gateClosed);
        assertTrue(gate(map, "RIGHT").gateClosed);
        assertTrue(gate(map, "TOP").gateClosed);
        s.bossesDefeated.add("LEFT");
        map.updateGates(s);
        assertFalse(gate(map, "RIGHT").gateClosed);
        s.bossesDefeated.add("RIGHT");
        map.updateGates(s);
        assertFalse(gate(map, "TOP").gateClosed);
    }

    @Test
    void fightLockClosesDoors() {
        WorldMap map = TestData.map();
        Room r = map.room("L3");
        for (Door d : r.doors) {
            assertFalse(d.dead(), "갱도 방의 세 출구는 모두 이어져 있다");
            assertNull(d.link.gate);
            float px = d.footX, py = d.footY;
            assertTrue(map.free(px, py, 9, 6), d.name);
            r.setFightLock(true);
            assertFalse(map.free(px, py, 9, 6), d.name);
            assertTrue(map.free(d.arriveX, d.arriveY, 9, 6), "문 앞 들어온 자리는 막지 않는다");
            r.setFightLock(false);
            assertTrue(map.free(px, py, 9, 6));
        }
    }

    @Test
    void everyLinkedDoorArrivesOnFloorAwayFromItsOwnTrigger() {
        WorldMap map = TestData.map();
        GameState all = NewGame.create(TestData.balance());
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) all.bossesDefeated.add(id);
        all.unsealed = true;
        map.updateGates(all);
        assertEquals(27, map.links.size());
        for (Link l : map.links) {
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                String name = d.room.id + "." + d.name;
                assertSame(d.room, map.roomAt((int) Math.floor(d.arriveX / 32), (int) Math.floor(d.arriveY / 32)), name);
                assertTrue(map.free(d.arriveX, d.arriveY, 12, 12), name + " 들어온 자리가 바닥");
                assertFalse(d.triggers(d.arriveX, d.arriveY), name + " 들어오자마자 되돌아가지 않는다");
                // 들어온 자리에서 출구 쪽으로 걸어가면 넘어갈 수 있어야 한다
                float x = d.arriveX, y = d.arriveY;
                boolean reached = false;
                for (int i = 0; i < 400 && !reached; i++) {
                    float tx = d.footX + d.dirX * 64 - x, ty = d.footY + d.dirY * 64 - y;
                    float len = (float) Math.sqrt(tx * tx + ty * ty);
                    float nx = x + tx / len * 2, ny = y + ty / len * 2;
                    if (map.free(nx, y, 9, 6)) x = nx;
                    if (map.free(x, ny, 9, 6)) y = ny;
                    reached = d.triggers(x, y);
                }
                assertTrue(reached, name + " 출구까지 걸어갈 수 있다");
            }
        }
    }

    @Test
    void everyRoomIsJoinedByAWalkableCorridor() {
        WorldMap map = TestData.map();
        GameState all = NewGame.create(TestData.balance());
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) all.bossesDefeated.add(id);
        all.unsealed = true;
        map.updateGates(all);
        // 심연 계단(한쪽 길)만 빼고 모든 이음에 통로가 있다
        java.util.Set<Room> reached = new java.util.HashSet<>();
        java.util.ArrayDeque<Room> queue = new java.util.ArrayDeque<>();
        queue.add(map.room("CAMP"));
        reached.add(map.room("CAMP"));
        for (Link l : map.links) {
            assertEquals(!l.oneWay && !l.transition, l.corridor != null, l.a.id + "-" + l.b.id);
            if (l.corridor == null) continue;
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float[] pos = {d.arriveX, d.arriveY};
                for (float[] w : TestData.waypoints(d, 32)) {
                    for (int i = 0; i < 400 && Math.hypot(w[0] - pos[0], w[1] - pos[1]) > 3; i++) {
                        float tx = w[0] - pos[0], ty = w[1] - pos[1];
                        float len = (float) Math.sqrt(tx * tx + ty * ty), step = Math.min(2, len);
                        float nx = pos[0] + tx / len * step, ny = pos[1] + ty / len * step;
                        if (map.free(nx, pos[1], 9, 6)) pos[0] = nx;
                        if (map.free(pos[0], ny, 9, 6)) pos[1] = ny;
                    }
                }
                Door to = d.partner();
                assertTrue(Math.hypot(to.arriveX - pos[0], to.arriveY - pos[1]) < 4,
                        d.room.id + "." + d.name + " 에서 통로를 따라 " + to.room.id + " 까지 걸어간다: "
                                + java.util.Arrays.toString(pos) + " → " + to.arriveX + ", " + to.arriveY);
            }
        }
        while (!queue.isEmpty()) {
            Room r = queue.remove();
            for (Link l : map.links) {
                if ((l.corridor == null && !l.transition) || (l.a != r && l.b != r)) continue;
                if (reached.add(l.other(r))) queue.add(l.other(r));
            }
        }
        for (Room r : map.rooms)
            assertEquals(r.type != RoomType.FINAL, reached.contains(r), r.id + " 은 캠프에서 걸어서 갈 수 있다 (심연만 떨어져서 간다)");
        // 통로끼리는 겹치지 않는다 (지름길이 생기지 않게)
        java.util.Map<Long, Link> owner = new java.util.HashMap<>();
        for (Link l : map.links) {
            if (l.corridor == null) continue;
            for (int[] t : l.corridor.tiles) {
                Link prev = owner.put(t[0] * 100000L + t[1], l);
                assertTrue(prev == null || prev == l, "통로가 겹친다: " + t[0] + "," + t[1]);
            }
        }
    }

    @Test
    void roomSizesVary() {
        WorldMap map = TestData.map();
        java.util.Set<Integer> widths = new java.util.HashSet<>();
        int minBoss = Integer.MAX_VALUE, maxMob = 0;
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            widths.add(r.x1 - r.x0);
            int floor = 0;
            for (int y = r.y0; y <= r.y1; y++) for (int x = r.x0; x <= r.x1; x++) if (r.floor(x, y)) floor++;
            if (r.type == RoomType.BOSS) minBoss = Math.min(minBoss, floor);
            if (r.type == RoomType.MOB) maxMob = Math.max(maxMob, floor);
            // Transit-only caves are narrow walkways, not combat arenas.
            assertTrue(floor > (r.type == RoomType.TUNNEL ? 40 : 90), r.id + " 바닥 " + floor);
        }
        assertTrue(widths.size() >= 4, "방 크기가 여러 가지");
        assertTrue(minBoss > maxMob, "보스방이 가장 넓다");
    }

    @Test
    void shiftedRoomsRemainSeparatedAndInsideTheMap() {
        WorldMap map = TestData.map();
        for (Room a : map.rooms) {
            assertTrue(a.x0 >= 0 && a.y0 >= 0 && a.x1 < map.width && a.y1 < map.height, a.id);
            for (Room b : map.rooms) {
                if (a == b) continue;
                assertTrue(a.x1 < b.x0 || b.x1 < a.x0 || a.y1 < b.y0 || b.y1 < a.y0,
                        a.id + " and " + b.id + " paintings must not overwrite each other");
            }
        }
    }

    private Link gate(WorldMap map, String gate) {
        for (Link l : map.links) if (gate.equals(l.gate)) return l;
        throw new AssertionError(gate);
    }

    @Test
    void veinsSitInFrontOfThePaintedOreAndShareTheRoomTotal() {
        WorldMap map = TestData.map();
        for (String id : new String[]{"L2", "L4", "R2", "R4", "T2", "T4"}) {
            Room r = map.room(id);
            assertFalse(r.scene.oreSpots.isEmpty(), id + " 그림 속 광석 자리");
            for (int seed = 0; seed < 100; seed++) {
                Random rnd = new Random(seed);
                int count = 3 + seed % 2, total = 10 + seed % 8;
                List<Vein> veins = VeinGenerator.generateAtSpots(r, rnd, count, OreType.BLACKIRON, total, 5, map.tileSize);
                assertEquals(Math.min(count, r.scene.oreSpots.size()), veins.size(), id + " 광맥 수");
                int sum = 0;
                List<int[]> cells = new ArrayList<>();
                for (Vein v : veins) {
                    assertTrue(v.painted());
                    assertTrue(r.inside(v.tileX, v.tileY), id + " 광맥은 바닥 위");
                    float d = (float) Math.hypot((v.tileX + 0.5f) * map.tileSize - v.spotX, (v.tileY + 0.5f) * map.tileSize - v.spotY);
                    assertTrue(d <= 5.5f * map.tileSize, id + " 광석 덩어리 바로 앞");
                    assertTrue(v.amount >= total / veins.size(), "한 곳에서 많이 나온다");
                    sum += v.amount;
                    for (int[] c : cells) {
                        assertFalse(Math.abs(c[0] - v.tileX) <= 1 && Math.abs(c[1] - v.tileY) <= 1, "광맥끼리 붙지 않는다");
                    }
                    cells.add(new int[]{v.tileX, v.tileY});
                }
                assertEquals(total, sum, id + " 방 합계");
            }
        }
    }

    @Test
    void randomVeinsStillWorkForRoomsWithoutPaintedOre() {
        WorldMap map = TestData.map();
        Room r = map.room("L2");
        for (int seed = 0; seed < 100; seed++) {
            Random rnd = new Random(seed);
            List<Vein> veins = VeinGenerator.generate(r, rnd, 3, OreType.BLACKIRON, 1, 1, 5, 0);
            VeinGenerator.spread(veins, 12, rnd);
            assertEquals(3, veins.size());
            assertEquals(12, veins.stream().mapToInt(v -> v.amount).sum());
            List<int[]> cells = new ArrayList<>();
            for (Vein v : veins) cells.add(new int[]{v.tileX, v.tileY});
            assertTrue(VeinGenerator.reachable(r, cells));
        }
    }
}
