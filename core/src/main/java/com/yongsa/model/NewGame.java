package com.yongsa.model;

import com.yongsa.config.Balance;

/** 새 게임의 처음 상태: 가보인 봉인된 성검 하나, 체력 가득. */
public final class NewGame {
    private NewGame() {
    }

    public static GameState create(Balance b) {
        GameState s = new GameState();
        s.mapVersion = com.yongsa.map.WorldMap.VERSION;
        Weapon sword = s.newWeapon(Rules.HOLY_SEALED);
        s.equippedUid = sword.uid;
        s.hp = b.hero.baseMaxHp;
        for (OreType t : OreType.values()) s.ores.put(t, 0);
        return s;
    }
}
