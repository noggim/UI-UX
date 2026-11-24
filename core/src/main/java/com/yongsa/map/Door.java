package com.yongsa.map;

/**
 * 방에서 나가는 길 하나. 문 밖은 이어진 방까지 가는 통로다 (통로가 없는 이음은 지나가면 짝 문 앞으로 옮겨 간다).
 * 캠프 문은 벽에 난 3칸짜리 문이고, 그림 방의 문은 그림 속 계단·갱도 입구 자리(foot)다.
 * 이어진 곳이 없는 그림 속 출구는 늘 막혀 있다.
 */
public class Door {
    public enum Side { LEFT, RIGHT, DOWN, UP }

    public final Room room;
    /** 그림의 출구 이름 (south, east…), 캠프 문은 방향 이름. */
    public final String name;
    public final Side side;
    /** 문이 차지하는 칸들 (캠프 문은 벽 칸 3개, 그림 문은 출구 자리 한 칸). */
    public final int[] tilesX, tilesY;
    /** 문 바로 안쪽 칸 (들어왔을 때 서는 칸). */
    public final int insideX, insideY;
    /** 바깥 칸: 캠프 문은 문 밖 통로, 그림 문은 이어진 방에서 서는 칸. */
    public int outsideX, outsideY;
    /** 출구 자리와 나가는 방향 (월드 좌표, 단위 벡터). */
    public final float footX, footY, dirX, dirY;
    /** 출구 폭의 절반 (월드 픽셀): 잠기면 이 폭만큼 막는다. */
    public float halfWidth;
    /** Sideways center of an asymmetric repainted opening, relative to the route anchor. */
    public float openingOffset;
    public float gateX() { return footX-dirY*openingOffset; }
    public float gateY() { return footY+dirX*openingOffset; }
    /** 출구 자리에서 이만큼 더 나가면 이어진 방으로 옮겨 간다 (음수면 그 전에). */
    public float triggerDepth;
    /** 이 문으로 들어올 때 서는 자리 (월드 좌표). */
    public float arriveX, arriveY;
    public Link link;
    /** 방 잠금 때문에 닫힘. */
    public boolean lockedByFight;

    Door(Room room, String name, Side side, int[] tx, int[] ty, int inX, int inY, int outX, int outY,
         float footX, float footY, float dirX, float dirY) {
        this.room = room;
        this.name = name;
        this.side = side;
        this.tilesX = tx;
        this.tilesY = ty;
        this.insideX = inX;
        this.insideY = inY;
        this.outsideX = outX;
        this.outsideY = outY;
        this.footX = footX;
        this.footY = footY;
        this.dirX = dirX;
        this.dirY = dirY;
    }

    /** 이어진 곳이 없거나, 방이 잠겼거나, 던전 입구가 아직 봉인돼 있으면 닫힘. */
    public boolean isClosed() {
        return lockedByFight || link == null || link.gateClosed;
    }

    /** 이어진 방이 없는 그림 속 출구 (늘 막혀 있다). */
    public boolean dead() {
        return link == null;
    }

    public boolean horizontal() {
        return side == Side.DOWN || side == Side.UP;
    }

    /** 이어진 짝 문. */
    public Door partner() {
        return link == null ? null : link.doorA == this ? link.doorB : link.doorA;
    }

    /** 출구 자리 기준 앞으로 얼마나 나갔나 (along) 와 옆으로 얼마나 벗어났나 (side). */
    public float along(float x, float y) {
        return (x - footX) * dirX + (y - footY) * dirY;
    }

    public float across(float x, float y) {
        return Math.abs(-(x - footX) * dirY + (y - footY) * dirX - openingOffset);
    }

    /** 이 자리에 서면 이어진 방으로 넘어가는가. */
    public boolean triggers(float x, float y) {
        return along(x, y) > triggerDepth && across(x, y) < halfWidth + 8;
    }

    /** 닫힌 그림 문이 막는 자리인가: 출구 자리에서 바깥쪽 전부. */
    public boolean blocks(float x, float y, float radius) {
        return along(x, y) > -radius - 4 && across(x, y) < halfWidth + radius + 8;
    }
}
