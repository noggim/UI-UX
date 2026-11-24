package com.yongsa.model;

import com.yongsa.config.Balance;

/**
 * 힘 흡수를 한곳에서 처리한다. 힘은 무기가 아니라 용사에게 쌓인다 (FR-대장간-20, FR-던전-12).
 * 흡수 장소·시점(명세서 7.1)이 정해지면 여기만 바꾼다. 지금은 ‘바로 흡수’:
 * 보스는 잡는 순간 흡수한다. 검은 대장간에서 흡수한다: 스킬이 열린 검(+10 각성)을 흡수하면 검은 사라지고
 * 그 검의 스킬을 용사가 익혀 어떤 검을 들어도 쓴다 (2026-10-14 해성 결정, 공격력 보너스는 없다).
 */
public class AbsorbService {
    public static final String[] BOSS_ORDER = {"LEFT", "RIGHT", "TOP"};

    private final Balance b;
    private final Rules rules;

    public AbsorbService(Balance b) {
        this.b = b;
        this.rules = new Rules(b);
    }

    /** 보스의 힘을 흡수한다. 오른 최대 체력을 돌려준다. 체력은 가득 찬다. */
    public int absorbBoss(GameState s, String dungeonId) {
        int before = rules.maxHp(s);
        if (!s.bossesDefeated.contains(dungeonId)) s.bossesDefeated.add(dungeonId);
        int after = rules.maxHp(s);
        s.hp = after;
        return after - before;
    }

    /** 이 검을 흡수할 수 없는 까닭 (흡수할 수 있으면 null). */
    public String skillAbsorbBlockReason(GameState s, Weapon w) {
        if (w == null) return "검을 고르세요";
        if (rules.isHoly(w)) return "성검은 흡수할 수 없습니다";
        Balance.WeaponDef d = b.weapon(w.defId);
        if (d.skill == null) return "스킬이 없는 검입니다";
        if (!rules.skillUnlocked(w)) return "+" + b.enhance.skillUnlockLevel + " 까지 강화해야 흡수할 수 있습니다";
        if (s.learnedAwakened.contains(w.defId) || (s.learnedSkills.contains(w.defId) && !w.awakened))
            return "이미 익힌 스킬입니다";
        return null;
    }

    /** 검을 흡수한다: 검은 사라지고 그 스킬을 용사가 익힌다 (각성한 검이면 강해진 스킬). */
    public void absorbSkill(GameState s, Weapon w) {
        String reason = skillAbsorbBlockReason(s, w);
        if (reason != null) throw new IllegalStateException(reason);
        s.weapons.remove(w);
        if (!s.learnedSkills.contains(w.defId)) s.learnedSkills.add(w.defId);
        if (w.awakened && !s.learnedAwakened.contains(w.defId)) s.learnedAwakened.add(w.defId);
        if (!s.skillOrder.contains(w.defId)) s.skillOrder.add(w.defId);
        if (s.equippedUid == w.uid) s.equippedUid = strongest(s).uid;
    }

    /** 예전 저장 파일에서 흡수한 각성 무기를 익힌 스킬로 옮긴다. */
    public void migrateImprints(GameState s) {
        for (String id : s.imprints) {
            Balance.WeaponDef d = b.weapon(id);
            if (d == null || d.skill == null) continue;
            if (!s.learnedSkills.contains(id)) s.learnedSkills.add(id);
            if (!s.learnedAwakened.contains(id)) s.learnedAwakened.add(id);
            if (!s.skillOrder.contains(id)) s.skillOrder.add(id);
        }
        s.imprints.clear();
    }

    private Weapon strongest(GameState s) {
        Weapon best = null;
        for (Weapon w : s.weapons)
            if (best == null || rules.attack(s, w) > rules.attack(s, best)) best = w;
        return best;
    }

    /** 세 보스의 힘을 모두 흡수했고 아직 봉인을 풀지 않았으면 풀 수 있다. */
    public boolean canUnseal(GameState s) {
        if (s.unsealed) return false;
        for (String id : BOSS_ORDER) if (!s.bossDefeated(id)) return false;
        return true;
    }

    /** 강해진 용사가 성검의 봉인을 푼다. 성검이 깨어나고 바로 장착된다. */
    public Weapon unseal(GameState s) {
        if (!canUnseal(s)) throw new IllegalStateException("아직 봉인을 풀 수 없습니다");
        Weapon sword = s.weaponByDef(Rules.HOLY_SEALED);
        if (sword == null) sword = s.newWeapon(Rules.HOLY_SEALED);
        sword.defId = Rules.HOLY_AWAKENED;
        s.unsealed = true;
        s.equippedUid = sword.uid;
        return sword;
    }
}
