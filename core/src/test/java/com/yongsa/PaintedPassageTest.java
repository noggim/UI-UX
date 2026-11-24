package com.yongsa;

import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.map.Corridor;
import com.yongsa.map.WorldMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Artwork geometry, actual movement clearance and saves at sub-tile path edges. */
class PaintedPassageTest {
    private GameState unlocked() {
        GameState s = NewGame.create(TestData.balance());
        s.bossesDefeated.addAll(List.of("LEFT", "RIGHT", "TOP"));
        s.unsealed = true;
        s.mapVersion = WorldMap.VERSION;
        return s;
    }

    @Test
    void everyPaintedCenterlineHasContinuousClearanceAndLockedGatesStayClosed() {
        WorldMap map = TestData.map();
        map.updateGates(unlocked());
        assertEquals(20, map.corridors.size());
        assertEquals(6, map.links.stream().filter(l -> l.transition).count());
        for (Corridor c : map.corridors) {
            assertNotNull(c.scene, c.key());
            assertEquals((float) c.scene.pixelWidth / c.scene.pixelHeight, c.widthPx / c.heightPx, .0001f);
            for (int i = 1; i < c.centerline.size(); i++) {
                float[] a = c.centerline.get(i - 1), b = c.centerline.get(i);
                int steps = (int) Math.ceil(Math.hypot(a[0] - b[0], a[1] - b[1]) / 2);
                for (int k = 0; k <= steps; k++) {
                    float x = a[0] + (b[0] - a[0]) * k / steps;
                    float y = a[1] + (b[1] - a[1]) * k / steps;
                    assertTrue(map.free(x, y, 9, 6), c.key() + " segment " + i + " at " + x + "," + y);
                }
            }
            float[] middle = c.centerline.get(c.centerline.size() / 2);
            c.link.doorA.lockedByFight = true;
            assertFalse(map.free(middle[0], middle[1], 9, 6), c.key());
            assertFalse(map.free(c.link.doorA.footX, c.link.doorA.footY, 9, 6), c.key());
            c.link.doorA.lockedByFight = false;
        }
    }

    @Test
    void anotherPassagePaintingCannotCoverTheWalkableFloor() {
        WorldMap map = TestData.map();
        for (Corridor c : map.corridors) {
            for (Corridor other : map.corridors) {
                if (c == other) continue;
                float x0 = Math.max(c.originX, other.originX), y0 = Math.max(c.originY, other.originY);
                float x1 = Math.min(c.originX + c.widthPx, other.originX + other.widthPx);
                float y1 = Math.min(c.originY + c.heightPx, other.originY + other.heightPx);
                for (float y = y0 + 1; y < y1; y += 4) for (float x = x0 + 1; x < x1; x += 4)
                    assertFalse(c.allowsFoot(x, y, 0), c.key() + " floor hidden by " + other.key());
            }
        }
    }

    @Test
    void savingOnCurvedEdgeResumesThereEvenWhenTileCenterIsOutsideFloor() {
        WorldMap map = TestData.map();
        GameState s = unlocked();
        map.updateGates(s);
        for (Corridor c : map.corridors) {
            for (float y = c.originY; y < c.originY + c.heightPx; y += 4)
                for (float x = c.originX; x < c.originX + c.widthPx; x += 4) {
                    int tx = (int) (x / map.tileSize), ty = (int) (y / map.tileSize);
                    if (map.roomAt(tx, ty) != null || map.corridor(tx, ty) || !map.free(x, y, 9, 6)) continue;
                    assertSame(c, map.corridorAtWorld(x, y));
                    s.resumeX = x; s.resumeY = y;
                    GameSession g = new GameSession(TestData.balance(), map, s, 7);
                    assertEquals(x, g.hero.x, .001f);
                    assertEquals(y, g.hero.y, .001f);
                    return;
                }
        }
        fail("The authored curves must exercise a partial floor tile");
    }
}
