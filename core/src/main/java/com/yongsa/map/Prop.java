package com.yongsa.map;

/** 캠프의 시설 (대장간, 침대, 모닥불, 바닥 문양). 문양 말고는 지나갈 수 없다. */
public class Prop {
    public enum Kind { FORGE, BED, CAMPFIRE, SEAL }

    public final Kind kind;
    public final int tileX, tileY, w, h;
    /** Artwork already contains this facility; scene polygons own its collision. */
    public final boolean baked;
    /** 침대: 누운 용사를 그릴 자리 (월드 좌표). NaN 이면 가운데. */
    public float sleepX = Float.NaN, sleepY = Float.NaN;
    private final float exactX, exactY, exactW, exactH;

    /** 바닥에 그려진 것이라 밟고 지나갈 수 있는가. */
    public boolean walkable() {
        return kind == Kind.SEAL;
    }

    public Prop(Kind kind, int tileX, int tileY, int w, int h) {
        this.kind = kind;
        this.tileX = tileX;
        this.tileY = tileY;
        this.w = w;
        this.h = h;
        baked = false;
        exactX = exactY = exactW = exactH = 0;
    }

    public Prop(Kind kind, float cx, float cy, float width, float height, int tileSize, boolean baked) {
        this.kind = kind;
        this.baked = baked;
        exactX = cx - width / 2; exactY = cy - height / 2;
        exactW = width; exactH = height;
        tileX = (int) Math.floor(exactX / tileSize);
        tileY = (int) Math.floor(exactY / tileSize);
        w = (int) Math.ceil((exactX + width) / tileSize) - tileX;
        h = (int) Math.ceil((exactY + height) / tileSize) - tileY;
    }

    public float x(int t) { return exactW > 0 ? exactX : tileX * t; }
    public float y(int t) { return exactH > 0 ? exactY : tileY * t; }
    public float width(int t) { return exactW > 0 ? exactW : w * t; }
    public float height(int t) { return exactH > 0 ? exactH : h * t; }
    public float centerX(int t) { return x(t) + width(t) / 2; }
    public float centerY(int t) { return y(t) + height(t) / 2; }

    public boolean covers(int tx, int ty) {
        return tx >= tileX && tx < tileX + w && ty >= tileY && ty < tileY + h;
    }
}
