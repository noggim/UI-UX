package com.yongsa.model;

/** 가방에 든 무기 한 자루. defId 로 Balance 의 무기 정의를 찾는다. */
public class Weapon {
    public int uid;
    public String defId;
    public int level;
    public boolean awakened;

    public Weapon() {
    }

    public Weapon(int uid, String defId) {
        this.uid = uid;
        this.defId = defId;
    }
}
