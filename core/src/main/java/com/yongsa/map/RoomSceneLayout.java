package com.yongsa.map;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/**
 * Room artwork coordinate contract. WorldMap places these scenes as dungeon rooms.
 * Source points are normalized image coordinates (top left, y down).
 * All world coordinates use the game's existing bottom-left/y-up convention.
 */
public final class RoomSceneLayout {
    public int version;
    public String status, coordinateSystem;
    public float referenceTileSize, footRadiusWorld;
    public List<Scene> scenes;

    public static RoomSceneLayout parse(String json) {
        RoomSceneLayout data = new Gson().fromJson(json, RoomSceneLayout.class);
        if (data == null || data.version != 1 || data.scenes == null || data.scenes.isEmpty()
                || !"normalized-image-top-left-x-right-y-down".equals(data.coordinateSystem))
            throw new IllegalArgumentException("Unsupported room scene manifest");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Scene scene : data.scenes) {
            scene.validate();
            if (!ids.add(scene.id)) throw new IllegalArgumentException("Duplicate scene: " + scene.id);
        }
        return data;
    }

    /** id 로 장면을 찾는다 (없으면 null). */
    public Scene scene(String id) {
        for (Scene s : scenes) if (s.id.equals(id)) return s;
        return null;
    }

    public static final class Portal {
        public String id, kind, facing;
        public float[] foot;
    }

    public static final class Facility {
        public Prop.Kind kind;
        public float[] center;
        public float widthTiles, heightTiles;
        public boolean baked;
        /** 침대: 누운 용사의 가운데 (그림 기준 0~1). 없으면 시설 가운데. */
        public float[] sleepAt;
    }

    public static final class Scene {
        public String id, title, image;
        public int pixelWidth, pixelHeight;
        public float suggestedWidthTiles;
        public float[] walkable, playerSpawn;
        /** Northern return doorway foot in original artwork coordinates. */
        public float[] returnDoor;
        /** 이동 마스크 PNG (assets 아래 경로). 있으면 walkable·blocked 대신 쓴다. */
        public String walkMask;
        public List<float[]> blocked = new ArrayList<>();
        public List<float[]> enemySpawns;
        public List<Portal> portals;
        public List<Facility> facilities = new ArrayList<>();
        /** 광물방: 그림 속 광석 덩어리 자리. 광맥은 이 앞 바닥에만 놓인다. */
        public List<float[]> oreSpots = new ArrayList<>();

        public void validate() {
            if (id == null || image == null || pixelWidth <= 0 || pixelHeight <= 0
                    || !Float.isFinite(suggestedWidthTiles) || suggestedWidthTiles <= 0
                    || walkable == null || walkable.length < 6 || walkable.length % 2 != 0)
                throw new IllegalArgumentException("Invalid room scene: " + id);
            for (float p : walkable) normalized(p);
            if (blocked == null) throw new IllegalArgumentException("Missing obstacle footprints");
            for (float[] polygon : blocked) {
                if (polygon == null || polygon.length < 6 || polygon.length % 2 != 0)
                    throw new IllegalArgumentException("Invalid obstacle footprint: " + id);
                for (float p : polygon) normalized(p);
            }
            point(playerSpawn);
            if (returnDoor != null) point(returnDoor);
            if (enemySpawns == null || portals == null) throw new IllegalArgumentException("Missing anchors");
            for (float[] p : enemySpawns) point(p);
            for (Portal p : portals) point(p.foot);
            if (facilities == null) throw new IllegalArgumentException("Missing facilities: " + id);
            java.util.Set<Prop.Kind> kinds = new java.util.HashSet<>();
            for (Facility f : facilities) {
                point(f.center);
                if (f.kind == null || !kinds.add(f.kind) || !Float.isFinite(f.widthTiles)
                        || !Float.isFinite(f.heightTiles) || f.widthTiles <= 0 || f.heightTiles <= 0)
                    throw new IllegalArgumentException("Invalid facility: " + id);
            }
        }

        private static void normalized(float n) {
            if (!Float.isFinite(n) || n < 0 || n > 1) throw new IllegalArgumentException("Invalid normalized point");
        }

        private static void point(float[] p) {
            if (p == null || p.length != 2) throw new IllegalArgumentException("Invalid point");
            normalized(p[0]); normalized(p[1]);
        }

        public float height(float width) { return width * pixelHeight / pixelWidth; }
        public float x(float u, float originX, float width) { return originX + u * width; }
        public float y(float v, float originY, float width) { return originY + (1f - v) * height(width); }
        public float u(float worldX, float originX, float width) { return (worldX - originX) / width; }
        public float v(float worldY, float originY, float width) { return 1f - (worldY - originY) / height(width); }

        /** Feet fit inside visible ground and outside solid footprints, including concave corners. */
        public boolean allowsFoot(float worldX, float worldY, float radius,
                                  float originX, float originY, float width) {
            if (!Float.isFinite(worldX) || !Float.isFinite(worldY) || !Float.isFinite(radius)
                    || !Float.isFinite(originX) || !Float.isFinite(originY)
                    || radius < 0 || !Float.isFinite(width) || width <= 0) return false;
            float px = worldX - originX, py = worldY - originY, h = height(width);
            WalkMask mask = WalkMask.get(walkMask);
            if (mask != null) return mask.fits(px / width, 1 - py / h, radius / width, radius / h);
            if (!fitsPolygon(walkable, px, py, radius, width, h, true)) return false;
            for (float[] polygon : blocked)
                if (!fitsPolygon(polygon, px, py, radius, width, h, false)) return false;
            return true;
        }

        static boolean fitsPolygon(float[] polygon, float px, float py, float radius,
                                           float width, float h, boolean mustBeInside) {
            boolean inside = false;
            float radius2 = radius * radius;
            int count = polygon.length / 2;
            for (int i = 0, j = count - 1; i < count; j = i++) {
                float ax = polygon[j * 2] * width, ay = (1 - polygon[j * 2 + 1]) * h;
                float bx = polygon[i * 2] * width, by = (1 - polygon[i * 2 + 1]) * h;
                if ((ay > py) != (by > py) && px < (bx - ax) * (py - ay) / (by - ay) + ax)
                    inside = !inside;
                float dx = bx - ax, dy = by - ay;
                float length2 = dx * dx + dy * dy;
                float t = length2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / length2));
                float ex = px - ax - t * dx, ey = py - ay - t * dy;
                if (ex * ex + ey * ey < radius2) return false;
            }
            return inside == mustBeInside;
        }
    }
}
