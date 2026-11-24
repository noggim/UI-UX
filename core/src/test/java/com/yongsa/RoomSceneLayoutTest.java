package com.yongsa;

import com.yongsa.map.RoomSceneLayout;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class RoomSceneLayoutTest {
    private RoomSceneLayout data() throws Exception {
        return RoomSceneLayout.parse(Files.readString(Path.of("assets/data/natural-room-scenes.json")));
    }

    @Test void sourceImagesMatchGeometryAndEveryAnchorFitsFeet() throws Exception {
        RoomSceneLayout data = data();
        assertEquals(29, data.scenes.size());
        for (var s : data.scenes) {
            var image = ImageIO.read(Path.of("assets", s.image).toFile());
            assertEquals(s.pixelWidth, image.getWidth(), s.id);
            assertEquals(s.pixelHeight, image.getHeight(), s.id);
            float w = s.suggestedWidthTiles * data.referenceTileSize;
            assertFits(s, s.playerSpawn, w, data.footRadiusWorld, s.id + " player");
            if (s.returnDoor != null) assertFits(s, s.returnDoor, w, data.footRadiusWorld, s.id + " return door");
            for (var p : s.portals) assertFits(s, p.foot, w, data.footRadiusWorld, s.id + " portal " + p.id);
            for (var p : s.enemySpawns) assertFits(s, p, w, data.footRadiusWorld, s.id + " enemy");
            assertFalse(s.allowsFoot(0, 0, data.footRadiusWorld, 0, 0, w), s.id);
            assertFalse(s.allowsFoot(w / 2, s.height(w), data.footRadiusWorld, 0, 0, w), s.id);
        }
    }

    private void assertFits(RoomSceneLayout.Scene s, float[] p, float w, float radius, String message) {
        assertTrue(s.allowsFoot(s.x(p[0], 0, w), s.y(p[1], 0, w), radius, 0, 0, w), message);
    }

    @Test void imageCoordinatesRoundTripAtTranslatedWorldOrigins() throws Exception {
        for (var s : data().scenes) {
            float w = 960, ox = 2048, oy = 4096;
            assertEquals(ox, s.x(0, ox, w));
            assertEquals(oy, s.y(1, oy, w));
            assertEquals(oy + s.height(w), s.y(0, oy, w));
            assertEquals(.372f, s.u(s.x(.372f, ox, w), ox, w), .00001f);
            assertEquals(.641f, s.v(s.y(.641f, oy, w), oy, w), .00001f);
            float[] spawn = s.playerSpawn;
            assertTrue(s.allowsFoot(s.x(spawn[0], ox, w), s.y(spawn[1], oy, w), 10, ox, oy, w));
        }
    }

    @Test void feetCannotClipIntoStraightOrConcaveBoundaries() {
        var s = new RoomSceneLayout.Scene();
        s.pixelWidth = s.pixelHeight = 100;
        s.walkable = new float[]{.1f,.1f, .9f,.1f, .9f,.4f, .5f,.4f, .5f,.9f, .1f,.9f};
        assertTrue(s.allowsFoot(30, 30, 8, 0, 0, 100));
        assertFalse(s.allowsFoot(15, 30, 8, 0, 0, 100));
        assertFalse(s.allowsFoot(47, 57, 8, 0, 0, 100));
        assertFalse(s.allowsFoot(70, 30, 0, 0, 0, 100));
        assertFalse(s.allowsFoot(Float.NaN, 30, 8, 0, 0, 100));
    }

    @Test void allProposedPortalsAndEnemySpawnsAreReachableFromPlayer() throws Exception {
        var data = data();
        float step = 4;
        for (var s : data.scenes) {
            float w = s.suggestedWidthTiles * data.referenceTileSize;
            int nx = (int) (w / step) + 1, ny = (int) (s.height(w) / step) + 1;
            boolean[] seen = new boolean[nx * ny];
            var queue = new ArrayDeque<Integer>();
            int startX = Math.round(s.x(s.playerSpawn[0], 0, w) / step);
            int startY = Math.round(s.y(s.playerSpawn[1], 0, w) / step);
            int start = startX + startY * nx;
            queue.add(start); seen[start] = true;
            int[] dx = {-1,1,0,0}, dy = {0,0,-1,1};
            while (!queue.isEmpty()) {
                int n = queue.removeFirst(), x = n % nx, y = n / nx;
                for (int k = 0; k < 4; k++) {
                    int xx = x + dx[k], yy = y + dy[k];
                    if (xx < 0 || yy < 0 || xx >= nx || yy >= ny) continue;
                    int next = xx + yy * nx;
                    if (!seen[next] && s.allowsFoot(xx * step, yy * step, data.footRadiusWorld, 0, 0, w)) {
                        seen[next] = true; queue.add(next);
                    }
                }
            }
            if (s.returnDoor != null) assertReachable(s, s.returnDoor, w, step, nx, seen, "return door");
            for (var p : s.portals) assertReachable(s, p.foot, w, step, nx, seen, p.id);
            for (var p : s.enemySpawns) assertReachable(s, p, w, step, nx, seen, "enemy");
            // These visible floors/stairs were excluded by the earlier conservative outlines.
            float[] openFloor = switch (s.id) {
                case "mine" -> new float[]{.32f, .675f};
                case "citadel" -> new float[]{.5f, .13f};
                case "tower" -> new float[]{.10f, .28f};
                case "seal-hall" -> new float[]{.5f, .21f};
                default -> null;
            };
            if (openFloor != null) assertReachable(s, openFloor, w, step, nx, seen, "visible floor/stair");
        }
    }

    @Test void solidFootprintsBlockFeetWithoutBlockingTheSurroundingFloor() {
        var s = new RoomSceneLayout.Scene();
        s.pixelWidth = s.pixelHeight = 100;
        s.walkable = new float[]{0,0, 1,0, 1,1, 0,1};
        s.blocked.add(new float[]{.4f,.4f, .6f,.4f, .6f,.6f, .4f,.6f});
        assertFalse(s.allowsFoot(50, 50, 5, 0, 0, 100));
        assertFalse(s.allowsFoot(37, 50, 5, 0, 0, 100));
        assertTrue(s.allowsFoot(34, 50, 5, 0, 0, 100));
        assertFalse(s.allowsFoot(137, 250, 5, 100, 200, 100));
        assertTrue(s.allowsFoot(134, 250, 5, 100, 200, 100));
    }

    @Test void mineCartHasASolidFootprint() throws Exception {
        var data = data();
        var mine = data.scenes.stream().filter(s -> s.id.equals("mine")).findFirst().orElseThrow();
        float w = mine.suggestedWidthTiles * data.referenceTileSize;
        assertFalse(mine.allowsFoot(mine.x(.216f, 0, w), mine.y(.59f, 0, w), data.footRadiusWorld, 0, 0, w));
        assertFits(mine, new float[]{.29f,.59f}, w, data.footRadiusWorld, "floor beside cart");
    }

    private void assertReachable(RoomSceneLayout.Scene s, float[] p, float w, float step, int nx, boolean[] seen, String name) {
        int x = Math.round(s.x(p[0], 0, w) / step), y = Math.round(s.y(p[1], 0, w) / step);
        assertTrue(seen[x + y * nx], s.id + " disconnected " + name);
    }
}
