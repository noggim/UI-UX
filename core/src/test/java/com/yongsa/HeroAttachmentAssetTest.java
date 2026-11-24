package com.yongsa;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.yongsa.combat.Facing;
import com.yongsa.gfx.HeroAtlasData;
import com.yongsa.gfx.HeroMotion;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

/** Asset handoff contract consumed by the follow-up weapon renderer. */
class HeroAttachmentAssetTest {
    private JsonValue manifest() throws Exception {
        return new JsonReader().parse(Files.readString(Path.of("assets/data/hero-motion.json")));
    }

    @Test void allSixtyPosesHaveSourceSpaceRightHandAnchors() throws Exception {
        var root = manifest();
        int count = 0;
        for (String action : new String[]{"walk", "roll"}) for (var row : root.get(action)) for (var frame : row) {
            float[] hand = frame.get("hand").asFloatArray();
            int[] rect = frame.get("rect").asIntArray();
            assertEquals(2, hand.length);
            assertTrue(Float.isFinite(hand[0]) && Float.isFinite(hand[1]));
            assertTrue(hand[0] >= rect[0] && hand[0] < rect[0]+rect[2]);
            assertTrue(hand[1] >= rect[1] && hand[1] < rect[1]+rect[3]);
            assertEquals("right", frame.getString("handSide"));
            if (frame.getBoolean("handVisible")) {
                var source = root.get("sources").get(frame.getString("source"));
                var im = ImageIO.read(Path.of("assets",source.getString("path")).toFile());
                assertTrue((im.getRGB(Math.round(hand[0]),Math.round(hand[1])) >>> 24) >= 128,
                        action + " hand is outside painted glove: " + hand[0] + "," + hand[1]);
            }
            count++;
        }
        assertEquals(60, count);
    }

    @Test void eightSwordsHaveTransparentPaddingAndMeasuredGrip() throws Exception {
        var swords = manifest().get("swords");
        assertEquals(8, swords.size);
        for (String family : new String[]{"blackiron","bloodmoon","skyfall","holy"})
            for (String suffix : new String[]{"","_awakened"}) {
                var s = swords.get("sword_" + family + suffix);
                assertNotNull(s);
                var path = Path.of("assets",s.getString("path"));
                var im = ImageIO.read(path.toFile());
                assertEquals(256, im.getWidth()); assertEquals(256, im.getHeight());
                assertTrue(im.getColorModel().hasAlpha());
                assertEquals(s.getString("sha256"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
                float[] grip = s.get("grip").asFloatArray();
                assertEquals(2, grip.length);
                assertTrue((im.getRGB(Math.round(grip[0]), Math.round(grip[1])) >>> 24) >= 128, "Grip misses handle");
                for (int i=0;i<256;i++) {
                    assertEquals(0, im.getRGB(i,0)>>>24); assertEquals(0, im.getRGB(i,255)>>>24);
                    assertEquals(0, im.getRGB(0,i)>>>24); assertEquals(0, im.getRGB(255,i)>>>24);
                }
                assertEquals(31.9f, s.getFloat("visibleLengthPixels")/s.getFloat("pixelsPerWorld"), .15f);
            }
    }

    @Test void handFollowsTheRenderedCropAcrossMirroringAndAttackSquash() throws Exception {
        var data = new HeroAtlasData(Files.readString(Path.of("assets/data/hero-motion.json")));
        for (Facing facing : Facing.values()) for (var clip : data.clips.values())
            for (var f : clip[HeroAtlasData.row(facing)]) for (float squash : new float[]{1f,.965f}) {
                float[] p = f.placement();
                // Project the pixel through the sprite crop and virtual canvas, independently of the foot formula.
                float u = p[0] + (f.handX()-f.x())/f.width()*p[2];
                float v = p[1] + (f.handY()-f.y())/f.height()*p[3];
                float x = (u-.5f)*HeroMotion.WIDTH*(2-squash);
                float y = ((1-v)*HeroMotion.HEIGHT-HeroMotion.FOOT_OFFSET)*squash;
                assertEquals(facing.leftward() ? -x : x, f.handOffsetX(facing.leftward(),squash), .00002f);
                assertEquals(y, f.handOffsetY(squash), .00002f);
            }
    }

    @Test void equipmentSelectsAllEightMeasuredSwordsIncludingHolyForms() throws Exception {
        var data = new HeroAtlasData(Files.readString(Path.of("assets/data/hero-motion.json")));
        var selected = new java.util.HashSet<String>();
        for (String id : new String[]{"blackiron","bloodmoon","skyfall","holy_sealed","holy_awakened"})
            for (boolean awakened : new boolean[]{false,true}) {
                String key = HeroAtlasData.swordKey(id,awakened);
                selected.add(key);
                var sword = data.swords.get(key);
                assertNotNull(sword,id);
                // Pixel Y is top-down but the SpriteBatch pivot is bottom-up.
                assertEquals(sword.gripX(), sword.originX()*sword.pixelsPerWorld(), .00002f);
                assertEquals(sword.height()-sword.gripY(), sword.originY()*sword.pixelsPerWorld(), .00002f);
            }
        assertEquals(8,selected.size());
        assertEquals("sword_holy",HeroAtlasData.swordKey("holy_sealed",true));
        assertEquals("sword_holy_awakened",HeroAtlasData.swordKey("holy_awakened",false));
    }

    @Test void badAttachmentCoordinatesAreRejectedBeforeRendering() throws Exception {
        var root = manifest();
        root.get("walk").get(0).get(0).get("hand").get(0).set(new JsonValue(-100));
        assertThrows(IllegalArgumentException.class,()->new HeroAtlasData(root.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json)));
        var other = manifest();
        other.get("swords").get("sword_holy").get("pixelsPerWorld").set(new JsonValue(0));
        assertThrows(IllegalArgumentException.class,()->new HeroAtlasData(other.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json)));
    }
}
