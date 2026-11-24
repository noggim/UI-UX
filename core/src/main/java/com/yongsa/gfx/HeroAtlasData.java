package com.yongsa.gfx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.yongsa.combat.Facing;
import java.util.LinkedHashMap;
import java.util.Map;

/** Measured native-pixel rectangles and anchors; display scaling never resamples the source files. */
public final class HeroAtlasData {
    public record Source(String path, int width, int height, String sha256) {}
    public record Frame(String source, int x, int y, int width, int height,
                        float footX, float footY, float pixelsPerWorld,
                        float handX, float handY, boolean handVisible) {
        public float handOffsetX(boolean mirrored, float squash) {
            return (mirrored ? -1 : 1) * (handX-footX)/pixelsPerWorld * (2-squash);
        }
        public float handOffsetY(float squash) { return (footY-handY)/pixelsPerWorld * squash; }
        /** Placement inside the renderer's common 64x72 quad, in top-left coordinates. */
        public float[] placement() {
            return new float[]{.5f + (x-footX)/pixelsPerWorld/HeroMotion.WIDTH,
                    1-HeroMotion.FOOT_OFFSET/HeroMotion.HEIGHT + (y-footY)/pixelsPerWorld/HeroMotion.HEIGHT,
                    width/pixelsPerWorld/HeroMotion.WIDTH, height/pixelsPerWorld/HeroMotion.HEIGHT};
        }
    }
    public record Sword(String path, int width, int height, float gripX, float gripY,
                        float pixelsPerWorld, float angleDegrees) {
        public float worldWidth() { return width/pixelsPerWorld; }
        public float worldHeight() { return height/pixelsPerWorld; }
        public float originX() { return gripX/pixelsPerWorld; }
        public float originY() { return (height-gripY)/pixelsPerWorld; }
    }
    public final Map<String, Source> sources = new LinkedHashMap<>();
    public final Map<String, Frame[][]> clips = new LinkedHashMap<>();
    public final Map<String, Sword> swords = new LinkedHashMap<>();
    /** Per-row walk order (indices into the six poses). Without "walkCycle" all six play in order. */
    public final int[][] walkCycle = new int[5][];

    public static String swordKey(String defId, boolean awakened) {
        return switch (defId) {
            case "holy_sealed" -> "sword_holy";
            case "holy_awakened" -> "sword_holy_awakened";
            default -> "sword_" + defId + (awakened ? "_awakened" : "");
        };
    }

    public static int row(Facing facing) {
        if (facing.diagonal()) return facing.sy < 0 ? 3 : 4;
        return HeroMotion.row(facing);
    }

    public HeroAtlasData(String json) {
        JsonValue root = new JsonReader().parse(json);
        require(root.getInt("version") == 1, "Unsupported hero atlas version");
        for (JsonValue s : root.get("sources")) {
            Source source = new Source(s.getString("path"), s.getInt("width"), s.getInt("height"), s.getString("sha256"));
            require(source.width > 0 && source.height > 0, "Invalid source dimensions");
            sources.put(s.name, source);
        }
        for (String action : new String[]{"walk", "roll"}) {
            JsonValue rows = root.get(action);
            require(rows != null && rows.size == 5, "Five directions required for " + action);
            Frame[][] clip = new Frame[5][6];
            for (int r=0; r<5; r++) {
                require(rows.get(r).size == 6, "Six poses required for " + action + " row " + r);
                for (int c=0; c<6; c++) {
                    JsonValue f = rows.get(r).get(c);
                    int[] rect = f.get("rect").asIntArray();
                    float[] foot = f.get("foot").asFloatArray();
                    float[] hand = f.get("hand").asFloatArray();
                    require(rect.length == 4 && foot.length == 2 && hand.length == 2, "Invalid frame coordinates");
                    Frame frame = new Frame(f.getString("source"), rect[0], rect[1], rect[2], rect[3],
                            foot[0], foot[1], f.getFloat("pixelsPerWorld"), hand[0], hand[1], f.getBoolean("handVisible"));
                    validate(frame);
                    clip[r][c] = frame;
                }
            }
            clips.put(action, clip);
        }
        JsonValue cycles = root.get("walkCycle");
        require(cycles == null || cycles.size == 5, "walkCycle needs five rows");
        for (int r=0; r<5; r++) {
            int[] cycle = cycles == null ? HeroMotion.DEFAULT_WALK_CYCLE.clone() : cycles.get(r).asIntArray();
            require(cycle.length >= 2 && cycle.length <= 12, "walkCycle row " + r + " needs 2-12 poses");
            for (int c : cycle) require(c >= 0 && c < 6, "walkCycle pose out of range in row " + r);
            walkCycle[r] = cycle;
        }
        for (JsonValue s : root.get("swords")) {
            float[] grip = s.get("grip").asFloatArray();
            require(grip.length == 2, "Invalid sword grip");
            Sword sword = new Sword(s.getString("path"), s.getInt("width"), s.getInt("height"),
                    grip[0], grip[1], s.getFloat("pixelsPerWorld"), s.getFloat("angleDegrees"));
            require(sword.width > 0 && sword.height > 0 && Float.isFinite(sword.pixelsPerWorld)
                    && sword.pixelsPerWorld > 0 && Float.isFinite(sword.angleDegrees), "Invalid sword scale");
            require(sword.gripX >= 0 && sword.gripX < sword.width && sword.gripY >= 0 && sword.gripY < sword.height,
                    "Sword grip outside image");
            swords.put(s.name, sword);
        }
    }

    private void validate(Frame f) {
        Source s = sources.get(f.source);
        require(s != null, "Missing source " + f.source);
        require(f.x >= 0 && f.y >= 0 && f.width > 0 && f.height > 0
                && (long)f.x+f.width <= s.width && (long)f.y+f.height <= s.height, "Frame outside " + f.source);
        require(Float.isFinite(f.pixelsPerWorld) && f.pixelsPerWorld > 0
                && Float.isFinite(f.footX) && Float.isFinite(f.footY), "Invalid ground anchor or scale");
        require(f.footX >= f.x && f.footX <= f.x+f.width && f.footY >= f.y && f.footY <= f.y+f.height,
                "Ground anchor outside frame");
        require(f.handX >= f.x && f.handX < f.x+f.width && f.handY >= f.y && f.handY < f.y+f.height,
                "Hand anchor outside frame");
        float[] p = f.placement();
        require(p[0] >= 0 && p[1] >= 0 && p[0]+p[2] <= 1 && p[1]+p[3] <= 1,
                "Pose would be clipped by the hero quad: " + f.source);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
