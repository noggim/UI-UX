package com.yongsa.map;

import java.util.ArrayList;
import java.util.List;

/**
 * 방 하나. x0~x1, y0~y1 은 방이 차지하는 타일 범위 (양 끝 포함).
 * 캠프처럼 타일로 짓는 방은 그 테두리가 벽이고, 그림 방(scene)은 그림의 바닥 경계(다각형)가 벽이다.
 */
public class Room {
    public final String id;
    public final RoomType type;
    /** LEFT/RIGHT/TOP, 캠프면 null. */
    public final String dungeonId;
    public final int cellX, cellY;
    public final int x0, y0, x1, y1;
    public final List<Door> doors = new ArrayList<>();

    /** 배경 그림과 바닥 경계. 타일 방이면 null. */
    public final RoomSceneLayout.Scene scene;
    /** 그림을 놓는 자리와 크기 (월드 픽셀), 좌우 뒤집기. */
    public final float originX, originY, widthPx, heightPx;
    public final boolean flip;
    /** 잡몹방 웨이브 수 (1 이면 한 번에 다 나온다). */
    public int waves = 1;
    /** 그림 방: 칸 가운데가 바닥인가 / 벽에서 넉넉히 떨어진 안쪽인가. */
    private boolean[] floorMask, innerMask;
    private float centerX, centerY;

    Room(String id, RoomType type, String dungeonId, int cellX, int cellY, int x0, int y0, int x1, int y1) {
        this(id, type, dungeonId, cellX, cellY, x0, y0, x1, y1, null, 0, 0, 0, 0, false);
    }

    Room(String id, RoomType type, String dungeonId, int cellX, int cellY, int x0, int y0, int x1, int y1,
         RoomSceneLayout.Scene scene, float originX, float originY, float widthPx, float heightPx, boolean flip) {
        this.id = id;
        this.type = type;
        this.dungeonId = dungeonId;
        this.cellX = cellX;
        this.cellY = cellY;
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.scene = scene;
        this.originX = originX;
        this.originY = originY;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.flip = flip;
        this.centerX = (x0 + x1) / 2f;
        this.centerY = (y0 + y1) / 2f;
    }

    /** 그림 방의 바닥 칸을 다각형에서 미리 계산해 둔다. */
    void buildMasks(int tileSize) {
        if (scene == null) return;
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        floorMask = new boolean[w * h];
        innerMask = new boolean[w * h];
        float sx = 0, sy = 0;
        int n = 0;
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                float cx = (tx + 0.5f) * tileSize, cy = (ty + 0.5f) * tileSize;
                int i = (tx - x0) + (ty - y0) * w;
                floorMask[i] = allowsFoot(cx, cy, 0);
                innerMask[i] = allowsFoot(cx, cy, tileSize * 0.35f);
                if (innerMask[i]) {
                    sx += tx;
                    sy += ty;
                    n++;
                }
            }
        }
        if (n == 0) return;
        // 무게중심에서 가장 가까운 안쪽 칸을 방 가운데로 삼는다 (오목한 방이어도 바닥 위)
        float gx = sx / n, gy = sy / n, best = Float.MAX_VALUE;
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (!insideWithMargin(tx, ty, 1)) continue;
                float d = (tx - gx) * (tx - gx) + (ty - gy) * (ty - gy);
                if (d < best) {
                    best = d;
                    centerX = tx;
                    centerY = ty;
                }
            }
        }
    }

    public boolean contains(int tx, int ty) {
        return tx >= x0 && tx <= x1 && ty >= y0 && ty <= y1;
    }

    /** 칸 가운데가 걸을 수 있는 바닥인가 (그림 방은 경계가 조금 걸쳐도 바닥). */
    public boolean floor(int tx, int ty) {
        if (scene == null) return inside(tx, ty);
        return contains(tx, ty) && floorMask[(tx - x0) + (ty - y0) * (x1 - x0 + 1)];
    }

    /** 벽 안쪽 바닥인가. 그림 방은 경계에서 조금 떨어진 칸만. */
    public boolean inside(int tx, int ty) {
        if (scene == null) return tx > x0 && tx < x1 && ty > y0 && ty < y1;
        return contains(tx, ty) && innerMask[(tx - x0) + (ty - y0) * (x1 - x0 + 1)];
    }

    /** 벽에서 margin 칸 이상 떨어진 안쪽인가. */
    public boolean insideWithMargin(int tx, int ty, int margin) {
        if (scene == null) return tx > x0 + margin && tx < x1 - margin && ty > y0 + margin && ty < y1 - margin;
        for (int y = ty - margin; y <= ty + margin; y++)
            for (int x = tx - margin; x <= tx + margin; x++)
                if (!inside(x, y)) return false;
        return true;
    }

    public float centerTileX() {
        return centerX;
    }

    public float centerTileY() {
        return centerY;
    }

    /** 그림 방: 발(반지름 radius)이 바닥 안이고 막힌 소품 밖인가. 월드 좌표. */
    public boolean allowsFoot(float wx, float wy, float radius) {
        if (scene == null) return true;
        if (flip) wx = originX + originX + widthPx - wx;
        return scene.allowsFoot(wx, wy, radius, originX, originY, widthPx);
    }

    /** 그림 좌표(u, v: 0~1, 위가 0)를 월드 좌표로. 뒤집힌 방이면 u 도 뒤집는다. */
    public float sceneX(float u) {
        return originX + (flip ? 1f - u : u) * widthPx;
    }

    public float sceneY(float v) {
        return originY + (1f - v) * heightPx;
    }

    /** 처음 들어올 자리: 그림 방은 그림에 정해 둔 자리, 아니면 가운데. */
    public float spawnX(int tileSize) {
        if (scene == null) return (centerX + 0.5f) * tileSize;
        return sceneX(scene.playerSpawn[0]);
    }

    public float spawnY(int tileSize) {
        if (scene == null) return (centerY + 0.5f) * tileSize;
        return sceneY(scene.playerSpawn[1]);
    }

    public void setFightLock(boolean locked) {
        for (Door d : doors) d.lockedByFight = locked;
    }
}
