package com.yongsa;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.yongsa.map.WalkMask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Artwork-reviewed floor/obstacle anchors, independent of the polygon replay tool. */
class WalkMaskReviewTest {
    private JsonArray reviewed() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("docs/walk-mask-review.json")))
                .getAsJsonObject().getAsJsonArray("masks");
    }

    @Test void reviewCoversEveryRuntimePaintingAtItsOriginalResolution() throws Exception {
        Map<String, String> expected = new HashMap<>();
        var map = com.yongsa.map.MapDef.parse(Files.readString(Path.of("assets/data/map.json")));
        var scenes = JsonParser.parseString(Files.readString(Path.of("assets/data/natural-room-scenes.json")))
                .getAsJsonObject().getAsJsonArray("scenes");
        for (var value : scenes) {
            var scene = value.getAsJsonObject();
            expected.put(scene.get("walkMask").getAsString(), scene.get("image").getAsString());
        }
        for (var scene : map.passageScenes) expected.put(scene.walkMask, scene.image);
        for (var joint : map.joints) expected.put(joint.walkMask, joint.image);

        for (var value : reviewed()) {
            var item = value.getAsJsonObject();
            String maskPath = item.get("mask").getAsString(), artPath = item.get("image").getAsString();
            assertEquals(artPath, expected.remove(maskPath), "Missing/duplicate runtime entry: " + maskPath);
            byte[] artBytes = Files.readAllBytes(Path.of("assets", artPath));
            assertEquals(item.get("artSha256").getAsString(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artBytes)),
                    "Painting changed; re-review its collision mask: " + artPath);
            var art = ImageIO.read(Path.of("assets", artPath).toFile());
            var mask = ImageIO.read(Path.of("assets", maskPath).toFile());
            assertEquals(art.getWidth(), mask.getWidth(), maskPath);
            assertEquals(art.getHeight(), mask.getHeight(), maskPath);
            var size = item.getAsJsonArray("size");
            assertEquals(size.get(0).getAsInt(), mask.getWidth(), maskPath);
            assertEquals(size.get(1).getAsInt(), mask.getHeight(), maskPath);
            int floor = 0;
            for (int y = 0; y < mask.getHeight(); y++) {
                int[] row = mask.getRGB(0, y, mask.getWidth(), 1, null, 0, mask.getWidth());
                for (int pixel : row) {
                    if (pixel == 0xffffffff) floor++;
                    else if (pixel != 0xff000000) fail("Not opaque pure black/white: " + maskPath);
                }
            }
            assertTrue(floor > 0 && floor < mask.getWidth() * mask.getHeight(), maskPath + " is empty/full");
        }
        assertTrue(expected.isEmpty(), "New artwork needs a mask review: " + expected);
    }

    @Test void visibleFloorAndSolidFootingAnchorsKeepTheirReviewedMeaning() throws Exception {
        int floorSamples = 0, obstacleSamples = 0;
        for (var value : reviewed()) {
            var item = value.getAsJsonObject();
            var mask = WalkMask.get(item.get("mask").getAsString());
            for (var sampleValue : item.getAsJsonArray("samples")) {
                var sample = sampleValue.getAsJsonObject();
                var uv = sample.getAsJsonArray("uv");
                boolean floor = sample.get("floor").getAsBoolean();
                assertEquals(floor, mask.floor(uv.get(0).getAsFloat(), uv.get(1).getAsFloat()),
                        item.get("id").getAsString() + ": " + sample.get("note").getAsString());
                if (floor) floorSamples++; else obstacleSamples++;
            }
        }
        assertTrue(floorSamples >= 20 && obstacleSamples >= 10, "Keep both floor and obstacle review anchors");
    }
}
