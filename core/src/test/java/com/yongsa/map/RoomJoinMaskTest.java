package com.yongsa.map;

import com.yongsa.TestData;
import com.yongsa.map.Corridor;
import com.yongsa.map.Door;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomJoinMaskTest {
    @Test
    void separateCaveEntrancesKeepTheirFramesEvenWhenTravelFacesAwayFromTheImageEdge() {
        var map = TestData.map();
        var camp = map.room("CAMP");
        var mask = new RoomJoinMask(camp, map.tileSize);
        for (float[] uv : new float[][]{{.012f,.29f},{.988f,.40f},{.508f,.015f}})
            assertTrue(mask.alpha(camp.sceneX(uv[0]),camp.sceneY(uv[1])) > .95f,
                    "The cave arch must not fade into the surrounding rock");
        assertTrue(mask.alpha(camp.sceneX(.012f),camp.sceneY(.85f)) < .25f,
                "Unrelated foreground rock keeps its normal soft edge");
    }

    @Test
    void mineLintelAndSidePostSurviveAtTheirOriginalPaintedPositions() {
        var map = TestData.map();
        var north = map.room("L5"); // flipped mine-mob-b, the first reported screenshot
        var northMask = new RoomJoinMask(north, map.tileSize);
        assertTrue(northMask.alpha(north.sceneX(.24f), north.sceneY(.02f)) > .95f,
                "The north lintel must not dissolve into the passage floor");
        var side = map.room("L1");
        var sideMask = new RoomJoinMask(side, map.tileSize);
        assertTrue(sideMask.alpha(side.sceneX(.02f), side.sceneY(.40f)) > .95f,
                "The side post must remain attached to the beam");
        assertEquals(1, sideMask.alpha(side.sceneX(.5f), side.sceneY(.5f)), .001f);
    }

    @Test
    void everyRoomEdgeStillFadesToZeroIncludingProtectedDoorways() {
        var map = TestData.map();
        for (var r : map.rooms) {
            var mask = new RoomJoinMask(r, map.tileSize);
            for (int i = 0; i <= 100; i++) {
                float u = i / 100f;
                assertEquals(0, mask.alpha(r.sceneX(u), r.sceneY(0)), .001f, r.id);
                assertEquals(0, mask.alpha(r.sceneX(u), r.sceneY(1)), .001f, r.id);
                assertEquals(0, mask.alpha(r.sceneX(0), r.sceneY(u)), .001f, r.id);
                assertEquals(0, mask.alpha(r.sceneX(1), r.sceneY(u)), .001f, r.id);
            }
        }
    }

    @Test
    void noDoorDirectionCutsToATransparentRectangleAtThePassageBoundary() {
        var map = TestData.map();
        for (Corridor c : map.corridors) for (Door d : new Door[]{c.link.doorA, c.link.doorB}) {
            for (int i = 0; i <= 100; i++) {
                float x = c.originX + c.widthPx * i / 100f;
                float y = c.originY + c.heightPx * i / 100f;
                assertEquals(0, RoomJoinMask.replacement(d, x, c.originY, map.tileSize), .001f, c.key());
                assertEquals(0, RoomJoinMask.replacement(d, x, c.originY + c.heightPx, map.tileSize), .001f, c.key());
                assertEquals(0, RoomJoinMask.replacement(d, c.originX, y, map.tileSize), .001f, c.key());
                assertEquals(0, RoomJoinMask.replacement(d, c.originX + c.widthPx, y, map.tileSize), .001f, c.key());
                assertTrue(RoomJoinMask.replacement(d, x, c.originY + .1f, map.tileSize) < .001f, c.key());
                assertTrue(RoomJoinMask.replacement(d, c.originX + .1f, y, map.tileSize) < .001f, c.key());
            }
            assertEquals(0, RoomJoinMask.replacement(d, d.footX, d.footY, map.tileSize), .001f, c.key());
        }
    }
}
