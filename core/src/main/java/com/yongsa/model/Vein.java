package com.yongsa.model;

/** 광물방의 광맥 하나. 타일 좌표에 놓이고, 여러 번 쳐야 부서진다. */
public class Vein {
    public int tileX, tileY;
    public OreType ore;
    public int amount;
    public int hitsLeft;
    /** 그림 속 광석 덩어리 자리 (월드 좌표). 0 이면 칸 위에 광석 덩어리를 그린다. */
    public float spotX, spotY;

    public Vein() {
    }

    public Vein(int tileX, int tileY, OreType ore, int amount, int hits) {
        this.tileX = tileX;
        this.tileY = tileY;
        this.ore = ore;
        this.amount = amount;
        this.hitsLeft = hits;
    }

    public boolean painted() {
        return spotX != 0 || spotY != 0;
    }
}
