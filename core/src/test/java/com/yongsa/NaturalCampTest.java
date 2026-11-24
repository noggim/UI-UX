package com.yongsa;

import com.yongsa.model.*;
import com.yongsa.map.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NaturalCampTest {
    private GameSession session(GameState state) {
        return new GameSession(TestData.balance(), TestData.map(), state, 42);
    }

    private List<GameEvent> interact(GameSession g, float u, float v) {
        Room camp = g.map.room("CAMP");
        g.hero.x = camp.sceneX(u); g.hero.y = camp.sceneY(v);
        assertTrue(g.map.free(g.hero.x, g.hero.y, g.hero.hw, g.hero.hh), "Approach must fit actual hero feet");
        g.pollEvents();
        Controls c = new Controls(); c.interact = true;
        g.update(1 / 60f, c);
        return g.pollEvents();
    }

    private void assertEvent(List<GameEvent> events, GameEvent.Type type) {
        assertTrue(events.stream().anyMatch(e -> e.type == type), "Expected " + type);
    }

    @Test void bakedFacilitiesStillOpenForgeHealAndEnterAbyss() {
        GameSession g = session(NewGame.create(TestData.balance()));
        assertEquals(4, g.map.props.size());
        assertEquals(3, g.map.props.stream().filter(p -> p.baked).count());
        assertEvent(interact(g, .306f, .434f), GameEvent.Type.OPEN_FORGE);
        g.state.hp = 10;
        interact(g, .745f, .440f);
        assertTrue(g.sleeping(), "침대에서 잠든다");
        for (int i = 0; i < 240; i++) g.update(1 / 60f, new Controls());
        assertEquals(g.rules.maxHp(g.state), g.state.hp);
        interact(g, .524f, .582f);
        assertEquals("E  모닥불", g.prompt);
        g.interactKey = "G"; // 조작 키를 바꾸면 안내도 그 키로
        interact(g, .524f, .582f);
        assertEquals("G  모닥불", g.prompt);
        g.state.unsealed = true;
        assertEvent(interact(g, .506f, .776f), GameEvent.Type.FALL);
    }

    @Test void visibleFloorStairsAndAllFacilityApproachesConnectToSpawn() {
        GameSession g = session(NewGame.create(TestData.balance()));
        Room camp = g.map.room("CAMP");
        assertNotNull(camp.scene);
        int step = 4, nx = (int) (camp.widthPx / step) + 1, ny = (int) (camp.heightPx / step) + 1;
        boolean[] seen = new boolean[nx * ny];
        var queue = new ArrayDeque<Integer>();
        int start = Math.round((g.hero.x - camp.originX) / step) + nx * Math.round((g.hero.y - camp.originY) / step);
        seen[start] = true; queue.add(start);
        int[] dx = {-1,1,0,0}, dy = {0,0,-1,1};
        while (!queue.isEmpty()) {
            int n = queue.remove(), x = n % nx, y = n / nx;
            for (int k = 0; k < 4; k++) {
                int xx = x + dx[k], yy = y + dy[k];
                if (xx < 0 || yy < 0 || xx >= nx || yy >= ny || seen[xx + yy * nx]) continue;
                if (!g.map.free(camp.originX + xx * step, camp.originY + yy * step, g.hero.hw, g.hero.hh)) continue;
                seen[xx + yy * nx] = true; queue.add(xx + yy * nx);
            }
        }
        for (float[] point : new float[][]{{.306f,.434f},{.745f,.440f},{.524f,.582f},{.506f,.776f},{.507f,.886f},{.27f,.52f},{.78f,.53f}}) {
            int x = Math.round((camp.sceneX(point[0]) - camp.originX) / step);
            int y = Math.round((camp.sceneY(point[1]) - camp.originY) / step);
            assertTrue(seen[x + y * nx], "Unreachable floor " + java.util.Arrays.toString(point));
        }
        for (float[] point : new float[][]{{.306f,.375f},{.745f,.38f},{.524f,.51f},{.21f,.425f}})
            assertFalse(g.map.free(camp.sceneX(point[0]), camp.sceneY(point[1]), g.hero.hw, g.hero.hh), "Visible solid base");
        // 아래 계단 밖은 마을로 가는 통로
        assertTrue(g.map.free(camp.sceneX(.505f), camp.sceneY(.96f), g.hero.hw, g.hero.hh), "village corridor");
        assertEquals(4, camp.doors.size()); // 던전 입구 셋 + 심연으로 내려가는 아래 계단
    }

    @Test void invalidOldCampResumeRelocatesWithoutResettingDungeonProgress() {
        var state = NewGame.create(TestData.balance());
        state.mapVersion = WorldMap.VERSION;
        state.bossesDefeated.add("LEFT");
        state.room("L1").cleared = true;
        var map = TestData.map();
        Room camp = map.room("CAMP");
        state.resumeX = camp.sceneX(.524f); state.resumeY = camp.sceneY(.51f); // Inside new fire pit
        GameSession g = session(state);
        assertEquals(camp.spawnX(TestData.balance().tileSize), g.hero.x, .01f);
        assertEquals(camp.spawnY(TestData.balance().tileSize), g.hero.y, .01f);
        assertTrue(state.bossDefeated("LEFT"));
        assertTrue(state.room("L1").cleared);
        g.prepareSave();
        GameSession resumed = session(state);
        assertEquals(g.hero.x, resumed.hero.x, .01f);
        assertEquals(g.hero.y, resumed.hero.y, .01f);
        assertTrue(resumed.inCamp());
    }
}
