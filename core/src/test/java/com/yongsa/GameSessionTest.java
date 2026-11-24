package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.map.Door;
import com.yongsa.map.Prop;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {
    private final Balance b = TestData.balance();

    private GameSession session(GameState s) {
        return new GameSession(b, TestData.map(), s, 42);
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private boolean has(List<GameEvent> events, GameEvent.Type type) {
        for (GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    /** 캠프 왼쪽 문(왼쪽 던전 입구) 바로 안쪽에 세운다. */
    private Door standAtCampLeftDoor(GameSession g) {
        Door d = door(g.map.room("CAMP"), "LEFT");
        g.hero.x = d.arriveX;
        g.hero.y = d.arriveY;
        return d;
    }

    /** 들어온 방 가운데 쪽으로 걸어 싸움이 시작되게 한다. */
    private List<GameEvent> walkToCenter(GameSession g) {
        Room r = g.heroRoom();
        List<GameEvent> all = new ArrayList<>();
        Controls c = new Controls();
        for (int i = 0; i < 240 && !g.fighting(); i++) {
            float dx = (r.centerTileX() + 0.5f) * b.tileSize - g.hero.x, dy = (r.centerTileY() + 0.5f) * b.tileSize - g.hero.y;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            c.moveX = l > 1 ? dx / l : 0;
            c.moveY = l > 1 ? dy / l : 0;
            all.addAll(run(g, c, 1 / 60f));
        }
        return all;
    }

    private static Door door(Room r, String name) {
        for (Door x : r.doors) if (x.name.equals(name)) return x;
        throw new AssertionError(r.id + "." + name);
    }

    @Test
    void enteringDarknessWalksThroughASeparateCaveBeforeTheOriginalDungeon() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Door d = standAtCampLeftDoor(g);
        assertNull(d.link.corridor, "캠프 밖을 억지로 연결하는 길을 그리지 않는다");
        g.deferDoors=true;
        boolean[] inCorridor = {false};
        boolean arrived = TestData.walkThrough(g, d, new ArrayList<>(), () -> {
            if (g.heroRoom() != null && g.heroRoom().type == RoomType.TUNNEL) {
                inCorridor[0] = true;
                assertFalse(g.fighting(), "짧은 동굴은 전투 없는 연결 공간");
            }
            return false;
        });
        assertTrue(arrived, "통로를 따라 걸어서 간다");
        assertTrue(inCorridor[0], "방과 방 사이에 걸어 지나는 통로가 있다");
        assertEquals("L1", g.heroRoom().id, "캠프 왼쪽 통로 끝은 왼쪽 던전 첫 방");
        assertTrue(s.room("L1").visited);
    }

    @Test
    void abyssStairsStillWarpBackToCampAfterTheScreenDarkens() {
        GameState s = NewGame.create(b);
        s.unsealed = true;
        GameSession g = session(s);
        g.deferDoors = true;
        Door stairs = door(g.map.room("F"), "south");
        assertNull(stairs.link.corridor, "심연 계단은 통로 없이 캠프로 올라간다");
        g.hero.x = stairs.arriveX;
        g.hero.y = stairs.arriveY;
        run(g, new Controls(), 1 / 60f);
        Controls down = new Controls();
        down.moveY = -1;
        List<GameEvent> ev = new ArrayList<>();
        for (int i = 0; i < 120 && !g.doorPending(); i++) ev.addAll(run(g, down, 1 / 60f));
        assertTrue(has(ev, GameEvent.Type.DOOR_ENTER));
        assertEquals(RoomType.FINAL, g.heroRoom().type, "화면이 어두워질 때까지 아직 심연");
        g.passPendingDoor();
        run(g, new Controls(), 1 / 60f);
        assertTrue(g.inCamp());
    }

    @Test
    void closedGatesBlockTheWayUntilProgressOpensThem() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Door right = door(g.map.room("CAMP"), "RIGHT");
        g.hero.x = right.arriveX;
        g.hero.y = right.arriveY;
        Controls go = new Controls();
        go.moveX = right.dirX;
        go.moveY = right.dirY;
        run(g, go, 1.0f);
        assertTrue(g.inCamp(), "오른쪽 던전은 왼쪽 보스를 잡아야 열린다");

        // 어두운 입구에서도 기존 진행 조건을 지켜야 한다.
        float[] mid = {right.footX,right.footY};
        assertFalse(g.map.free(mid[0],mid[1], g.hero.hw, g.hero.hh));
        s.bossesDefeated.add("LEFT");
        g.map.updateGates(s);
        assertTrue(g.map.free(mid[0],mid[1], g.hero.hw, g.hero.hh));

        // 캠프 아래 문은 마을로 나간다 (잠기지 않지만 용사가 처음엔 나가려 하지 않는다)
        Door down = door(g.map.room("CAMP"), "down");
        assertEquals("V", down.partner().room.id);
        assertTrue(g.map.free(down.footX, down.footY, g.hero.hw, g.hero.hh));
    }

    @Test
    void devWarpPutsHeroAtTheBossRoomEntrance() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        for (String id : new String[]{"LEFT", "RIGHT", "TOP", "FINAL"}) {
            assertTrue(g.devWarpToBoss(id), id);
            run(g, new Controls(), 1 / 60f);
            RoomType want = "FINAL".equals(id) ? RoomType.FINAL : RoomType.BOSS;
            assertEquals(want, g.heroRoom().type, id);
            assertFalse(g.fighting(), "입구에 서기만 해서는 싸움이 시작되지 않는다");
            if (!"FINAL".equals(id)) assertEquals(id, g.heroRoom().dungeonId);
        }
    }

    @Test
    void enteringMobRoomLocksDoorsAndClearingOpensThem() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        assertTrue(TestData.walkThrough(g, standAtCampLeftDoor(g)));
        List<GameEvent> ev = walkToCenter(g);
        assertTrue(has(ev, GameEvent.Type.ROOM_LOCKED));
        assertTrue(g.fighting());
        assertEquals(b.dungeon("LEFT").mobsPerRoom, g.monsters.size());
        assertFalse(g.canExitDungeon(), "잠긴 방에서는 던전을 나갈 수 없다");

        g.debugKillAll();
        ev = run(g, new Controls(), 0.1f);
        assertTrue(has(ev, GameEvent.Type.ROOM_CLEARED));
        assertFalse(g.fighting());
        assertTrue(s.room("L1").cleared);
        assertTrue(g.canExitDungeon());
    }

    @Test
    void savingDuringFightResumesOutsideTheDoor() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Door d = standAtCampLeftDoor(g);
        assertTrue(TestData.walkThrough(g, d));
        walkToCenter(g);
        assertTrue(g.fighting());
        g.prepareSave();
        Door safe=door(g.map.room("CAVE_LEFT"),"exit");
        assertEquals(safe.arriveX, s.resumeX, 1e-3, "잠긴 방 바깥의 동굴 입구에서 이어 한다");
        assertEquals(safe.arriveY, s.resumeY, 1e-3);
        assertFalse(s.room("L1").cleared);
    }

    @Test
    void bedResetsRoomsAndRespawnsBossUnlessChestIsUnopened() {
        GameState s = NewGame.create(b);
        s.room("L1").cleared = true;
        s.room("L6").cleared = true;
        s.room("R6").cleared = true;
        s.bossesDefeated.add("LEFT");
        s.bossesDefeated.add("RIGHT");
        s.chests.add("R6");
        s.hp = 10;
        GameSession g = session(s);
        g.useBed();
        assertFalse(s.room("L1").cleared);
        assertFalse(s.room("L6").cleared, "잡은 보스도 침대 뒤 다시 나온다 (선우 버전)");
        assertTrue(s.room("R6").cleared, "열지 않은 상자가 있는 보스방은 그대로");
        assertTrue(s.bossDefeated("LEFT"), "흡수한 힘은 그대로");
        assertEquals(450, s.hp);
        assertTrue(has(g.pollEvents(), GameEvent.Type.AUTOSAVE));
    }

    @Test
    void firstOreRoomIsGeneratedOnEntryWithGuarantee() {
        GameState s = NewGame.create(b);
        s.room("L1").cleared = true;
        GameSession g = session(s);
        Door west = door(g.map.room("L1"), "west");
        assertNull(g.veinsOf(g.map.room("L2")));
        assertTrue(TestData.walkThrough(g, west)); // L1 왼쪽 통로를 지나 L2 로
        assertEquals("L2", g.heroRoom().id);
        assertNotNull(g.veinsOf(g.map.room("L2")));
        int total = g.veinsOf(g.map.room("L2")).stream().mapToInt(v -> v.amount).sum();
        assertTrue(total >= 8);
        for (var v : g.veinsOf(g.map.room("L2"))) {
            assertTrue(v.painted(), "그림 속 광석 덩어리 앞에 놓인다");
            assertFalse(g.map.isBlockedOverlay(v.tileX, v.tileY), "그림이 광석이라 바닥 칸은 막지 않는다");
        }
    }

    @Test
    void oldSaveFromBeforeTheRoomPaintingsResumesInCamp() {
        GameState s = NewGame.create(b);
        s.mapVersion = 0; // 그림 방 이전 저장
        s.resumeX = 30 * b.tileSize;
        s.resumeY = 40 * b.tileSize;
        s.room("L2").veins = new ArrayList<>(List.of(new com.yongsa.model.Vein(3, 3, com.yongsa.model.OreType.BLACKIRON, 1, 3)));
        GameSession g = session(s);
        assertTrue(g.inCamp(), "예전 위치는 새 맵에 맞지 않아 캠프에서 시작");
        assertNull(s.room("L2").veins, "예전 광맥 자리는 버린다");
        assertEquals(com.yongsa.map.WorldMap.VERSION, s.mapVersion);
    }

    @Test
    void campRegeneratesHp() {
        GameState s = NewGame.create(b);
        s.hp = 10;
        GameSession g = session(s);
        run(g, new Controls(), 2f);
        assertTrue(s.hp > 10);
    }

    @Test
    void previousMapSaveKeepsProgressButDropsStaleWorldCoordinates() {
        GameState s = NewGame.create(b);
        s.mapVersion = com.yongsa.map.WorldMap.VERSION - 1;
        s.resumeX = 1000; s.resumeY = 1000;
        s.potionDrops.add(new float[]{1000, 1000});
        s.bossesDefeated.add("LEFT");
        s.addOre(com.yongsa.model.OreType.BLACKIRON, 7);
        int ore = s.ore(com.yongsa.model.OreType.BLACKIRON), weapons = s.weapons.size();
        s.room("L1").cleared = true;
        GameSession g = session(s);
        assertTrue(g.inCamp());
        assertTrue(s.potionDrops.isEmpty());
        assertEquals(ore, s.ore(com.yongsa.model.OreType.BLACKIRON));
        assertEquals(weapons, s.weapons.size());
        assertTrue(s.bossDefeated("LEFT"));
        assertTrue(s.room("L1").cleared);
    }

    @Test
    void rollMovesFasterThanWalkingAndGivesInvulnerability() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        float x0 = g.hero.x;
        Controls c = new Controls();
        c.moveX = -1;
        c.roll = true;
        g.update(1 / 60f, c);
        assertTrue(g.hero.rolling());
        assertTrue(g.hero.invuln > 0, "구르는 동안은 맞지 않는다");
        c.roll = false;
        run(g, c, b.attack.rollTime);
        float rolled = x0 - g.hero.x;
        float walked = b.hero.moveSpeedTiles * b.tileSize * b.attack.rollTime;
        assertTrue(rolled > walked * 1.5f, "구르기는 같은 시간 걷는 것보다 멀리 간다: " + rolled + " vs " + walked);
        c.roll = true;
        g.update(1 / 60f, c);
        assertFalse(g.hero.rolling(), "쿨타임 중에는 다시 구를 수 없다");
    }

    @Test
    void defeatedBossRoomHasReturnPortalToCamp() {
        GameState s = NewGame.create(b);
        s.bossesDefeated.add("LEFT");
        s.room("L6").cleared = true;
        GameSession g = session(s);
        Room boss = g.map.room("L6");
        assertTrue(g.hasPortal(boss));
        assertFalse(g.hasPortal(g.map.room("R6")));
        g.hero.x = g.portalX(boss);
        g.hero.y = g.portalY(boss);
        Controls c = new Controls();
        c.interact = true;
        List<GameEvent> ev = run(g, c, 1 / 60f);
        assertTrue(has(ev, GameEvent.Type.WARP));
        g.warpToCamp();
        assertTrue(g.inCamp());
    }

    @Test
    void allNorthernReturnDoorsRequireVictoryAndCloseOnRevival() {
        for (String id : new String[]{"L6", "R6", "T6"}) {
            GameState state = NewGame.create(b);
            GameSession g = session(state);
            Room room = g.map.room(id);
            assertNotNull(room.scene.returnDoor);
            assertTrue(room.scene.returnDoor[1] < .4f, "Door belongs to the north wall");
            assertTrue(g.map.free(g.portalX(room), g.portalY(room), 9, 6), id + " doorway approach");
            assertFalse(g.hasPortal(room));
            assertFalse(g.map.free(g.portalX(room), g.portalY(room)+14, 9, 6), "Closed barrier blocks feet");
            state.bossesDefeated.add(room.dungeonId);
            state.room(id).cleared = true;
            assertTrue(g.hasPortal(room));
            assertTrue(g.map.free(g.portalX(room), g.portalY(room)+14, 9, 6), "Open doorway clears the barrier");
            g.hero.x = g.portalX(room); g.hero.y = g.portalY(room);
            Controls input = new Controls(); input.interact = true;
            assertTrue(has(run(g, input, 1 / 60f), GameEvent.Type.WARP), id);
            g.devReviveBoss(room.dungeonId);
            assertFalse(g.hasPortal(room));
        }
    }

    @Test
    void sleepingInTheBedRestsHalfwayAndWakesUp() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Prop bed = g.map.props.stream().filter(p -> p.kind == Prop.Kind.BED).findFirst().orElseThrow();
        Room camp = g.map.room("CAMP");
        g.hero.x = camp.sceneX(0.745f);
        g.hero.y = camp.sceneY(0.44f);
        s.hp = 10;
        run(g, new Controls(), 0.05f);
        assertNotNull(g.prompt, "침대 앞에서 E 안내가 뜬다");
        Controls e = new Controls();
        e.interact = true;
        g.update(1 / 60f, e);
        assertTrue(g.sleeping());
        assertEquals(bed, g.sleepBed());
        Controls go = new Controls();
        go.moveX = 1;
        float x0 = g.hero.x;
        List<GameEvent> ev = run(g, go, 1.0f);
        assertEquals(x0, g.hero.x, 0.01f, "자는 동안은 움직이지 않는다");
        assertFalse(has(ev, GameEvent.Type.BED_USED));
        ev = run(g, new Controls(), 1.0f);
        assertTrue(has(ev, GameEvent.Type.BED_USED), "중간쯤 푹 쉰다");
        assertEquals(g.rules.maxHp(s), s.hp);
        run(g, new Controls(), 1.3f);
        assertFalse(g.sleeping(), "끝나면 일어난다");
    }
}
