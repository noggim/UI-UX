package com.yongsa.combat;

import com.yongsa.map.WorldMap;

/** 타일과 발 사각형의 겹침으로 막힌 곳을 처리한다. 물리 엔진은 쓰지 않는다. */
public final class Physics {
    private Physics() {
    }

    /** 축마다 따로, 한 번에 최대 4픽셀씩 나눠 움직여 벽을 뚫지 않게 한다. 실제로 움직인 거리 비율을 돌려준다. */
    public static boolean move(WorldMap map, Actor a, float dx, float dy, int tileSize) {
        float dist = Math.max(Math.abs(dx), Math.abs(dy));
        int steps = Math.max(1, (int) Math.ceil(dist / 4f));
        float sx = dx / steps, sy = dy / steps;
        boolean blocked = false;
        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                if (free(map, a.x + sx, a.y, a, tileSize)) a.x += sx;
                else blocked = true;
            }
            if (sy != 0) {
                if (free(map, a.x, a.y + sy, a, tileSize)) a.y += sy;
                else blocked = true;
            }
        }
        return blocked;
    }

    public static boolean free(WorldMap map, float x, float y, Actor a, int tileSize) {
        return map.free(x, y, a.hw, a.hh);
    }
}
