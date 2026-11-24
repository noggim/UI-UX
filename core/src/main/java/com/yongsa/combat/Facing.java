package com.yongsa.combat;

/**
 * 용사가 바라보는 8방향 (FR-전투-12). 새 아틀라스는 대각선 전용 자세를 쓰고,
 * 예전 세 줄 그림만 있을 때는 옆모습으로 대체한다.
 */
public enum Facing {
    DOWN(0, -1), UP(0, 1), LEFT(-1, 0), RIGHT(1, 0),
    DOWN_LEFT(-1, -1), DOWN_RIGHT(1, -1), UP_LEFT(-1, 1), UP_RIGHT(1, 1);

    /** 가로(-1 왼쪽, 1 오른쪽)와 세로(-1 아래, 1 위) 성분. */
    public final int sx, sy;

    Facing(int sx, int sy) {
        this.sx = sx;
        this.sy = sy;
    }

    /** 45도씩 여덟 칸으로 나눠 가장 가까운 방향을 고른다. */
    public static Facing of(float dx, float dy) {
        if (dx == 0 && dy == 0) return DOWN;
        int sector = Math.round((float) Math.atan2(dy, dx) / (float) (Math.PI / 4));
        switch (Math.floorMod(sector, 8)) {
            case 0: return RIGHT;
            case 1: return UP_RIGHT;
            case 2: return UP;
            case 3: return UP_LEFT;
            case 4: return LEFT;
            case 5: return DOWN_LEFT;
            case 6: return DOWN;
            default: return DOWN_RIGHT;
        }
    }

    /** 그림을 좌우로 뒤집어 그려야 하는 방향 (왼쪽 성분이 있음). */
    public boolean leftward() {
        return sx < 0;
    }

    public boolean diagonal() {
        return sx != 0 && sy != 0;
    }
}
