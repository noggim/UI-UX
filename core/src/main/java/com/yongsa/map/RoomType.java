package com.yongsa.map;

public enum RoomType {
    CAMP, MOB, ORE, BOSS, FINAL,
    /** A short, separate cave between the camp mouth and the existing dungeon. */
    TUNNEL,
    /** 던전 밖 마을 (선우 버전): 몬스터가 없고 캠프처럼 체력이 찬다. */
    VILLAGE;

    /** 들어가면 문이 잠기고 몬스터가 나오는 방. */
    public boolean hasFight() {
        return this == MOB || this == BOSS || this == FINAL;
    }
}
