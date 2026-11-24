package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.Balance.DungeonDef;
import com.yongsa.config.Balance.WeaponDef;

/**
 * 화면 왼쪽 아래 “다음 할 일” 한 줄 (FR-공통-07). 상태만 보고 정한다.
 * 던전마다 광물 모으기 → 무기 제작 → 장착 → 보스 순서로 안내한다.
 */
public final class NextTask {
    private NextTask() {
    }

    public static String describe(GameState s, Balance b, boolean leftDungeonVisited) {
        return describe(s, b, leftDungeonVisited, "B");
    }

    /** bagKey: 가방 키 이름 (조작 키를 바꾸면 그 이름으로 안내한다). */
    public static String describe(GameState s, Balance b, boolean leftDungeonVisited, String bagKey) {
        if (s.finalBossDefeated) return "자유롭게 던전을 돌며 무기를 키워 보세요";
        if (s.unsealed) return "캠프의 문양에서 심연으로 내려가 마왕 쓰러뜨리기";
        if (new AbsorbService(b).canUnseal(s)) return "캠프 바닥의 문양에서 성검의 봉인 풀기";

        Rules rules = new Rules(b);
        for (DungeonDef d : b.dungeons) {
            if (s.bossDefeated(d.id)) continue;
            WeaponDef wd = b.weaponForOre(d.ore);
            OreType ore = OreType.valueOf(d.ore);
            Weapon owned = s.weaponByDef(wd.id);
            String where = dungeonWord(d.id);
            if (owned == null) {
                if (s.ore(ore) >= wd.craftCost) return "캠프 대장간에서 " + wd.name + " 제작";
                if (d.id.equals("LEFT") && !leftDungeonVisited) return "왼쪽 던전으로 이동";
                return where + " 던전 광물방에서 " + ore.displayName + " 모으기 "
                        + s.ore(ore) + "/" + wd.craftCost;
            }
            if (s.equippedUid != owned.uid && rules.attack(s, owned) > rules.attack(s)) {
                return "가방(" + bagKey + ")에서 " + wd.name + " 장착";
            }
            return where + " 던전 보스 잡기 (" + d.boss.name + ")";
        }
        return "던전을 돌며 무기를 키워 보세요";
    }

    public static String dungeonWord(String id) {
        switch (id) {
            case "LEFT": return "왼쪽";
            case "RIGHT": return "오른쪽";
            default: return "위쪽";
        }
    }
}
