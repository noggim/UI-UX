package com.yongsa;

import static org.junit.jupiter.api.Assertions.*;

import com.yongsa.map.MapDef;
import com.yongsa.map.RoomSceneLayout;
import com.yongsa.map.WalkMask;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WalkMaskTest {
    @Test void everyPaintingHasAMaskWithTheSameShape() throws Exception {
        var map = MapDef.parse(Files.readString(Path.of("assets/data/map.json")));
        var scenes = RoomSceneLayout.parse(Files.readString(Path.of("assets/data/natural-room-scenes.json")));
        for (var s : scenes.scenes) check(s.id, s.walkMask, s.pixelWidth, s.pixelHeight);
        for (var p : map.passageScenes) check(p.id, p.walkMask, p.pixelWidth, p.pixelHeight);
        for (var j : map.joints) {
            BufferedImage art = ImageIO.read(Path.of("assets", j.image).toFile());
            check(j.id, j.walkMask, art.getWidth(), art.getHeight());
        }
    }

    private void check(String id, String path, int width, int height) {
        assertNotNull(path, id + " has no walkMask");
        WalkMask mask = WalkMask.get(path);
        assertEquals((float) width / height, (float) mask.width / mask.height, .01f, id + " mask aspect");
    }

    @Test void whiteIsFloorAndTheFootMustFitInside() throws Exception {
        BufferedImage img = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        for (int y = 10; y < 40; y++) for (int x = 20; x < 80; x++) img.setRGB(x, y, 0xffffffff);
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        var old = WalkMask.reader;
        try {
            WalkMask.reader = p -> out.toByteArray();
            WalkMask.clearCache();
            WalkMask m = WalkMask.get("test.png");
            assertTrue(m.floor(.5f, .5f));
            assertFalse(m.floor(.1f, .5f), "transparent is blocked");
            assertFalse(m.floor(1.2f, .5f), "outside the painting is blocked");
            assertTrue(m.fits(.5f, .5f, .1f, .1f));
            assertFalse(m.fits(.25f, .5f, .1f, .1f), "foot reaches past the left edge");
        } finally {
            WalkMask.reader = old;
            WalkMask.clearCache();
        }
    }
}
