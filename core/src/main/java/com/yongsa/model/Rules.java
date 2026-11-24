package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.Balance.WeaponDef;

/** 상태에서 바로 계산되는 값들 (공격력, 최대 체력, 능력 보유 여부). */
public final class Rules {
    public static final String HOLY_SEALED = "holy_sealed";
    public static final String HOLY_AWAKENED = "holy_awakened";

    private final Balance b;

    public Rules(Balance b) {
        this.b = b;
    }

    public Balance balance() {
        return b;
    }

    /** 공격력 = 기본 + 단계 × 단계당 (각성 무기 흡수는 2026-10-14 에 없앴다). */
    public int attack(GameState s, Weapon w) {
        if (w == null) return 0;
        WeaponDef d = b.weapon(w.defId);
        return d.baseAttack + w.level * d.perLevel;
    }

    public int attack(GameState s) {
        return attack(s, s.equipped());
    }

    /** 최대 체력은 흡수한 보스 수로만 정해진다 (100 → 250 → 450 → 700). */
    public int maxHp(GameState s) {
        int n = s.bossesDefeated.size();
        if (n <= 0) return b.hero.baseMaxHp;
        int[] t = b.hero.maxHpAfterBosses;
        return t[Math.min(n, t.length) - 1];
    }

    /** 각성 능력은 각성한 무기를 들고 있을 때만 쓴다. */
    public boolean hasAbility(GameState s, String weaponDefId) {
        Weapon w = s.equipped();
        return w != null && w.awakened && w.defId.equals(weaponDefId);
    }

    /** 스킬 칸 수: 검 스킬 셋 (L, U, I) + 깨어난 성검 (O). */
    public static final int SKILL_SLOTS = 4;
    /** 깨어난 성검 스킬 칸. */
    public static final int HOLY_SLOT = 3;

    /** 열린 스킬 칸 하나: 어느 검의 스킬인지, 지금 쓸 수 있는지, 각성해서 강해졌는지. */
    public static final class SkillSlot {
        public final String weaponId;
        public final Balance.SkillDef def;
        public final boolean awakened;
        /** 검을 흡수해 익힌 스킬 (어떤 검을 들어도 쓴다). */
        public final boolean learned;
        /** 지금 쓸 수 있나: 익혔거나, 그 검을 들고 있다. */
        public final boolean usable;

        SkillSlot(String weaponId, Balance.SkillDef def, boolean awakened, boolean learned, boolean usable) {
            this.weaponId = weaponId;
            this.def = def;
            this.awakened = awakened;
            this.learned = learned;
            this.usable = usable;
        }
    }

    /** 이 검의 스킬이 열렸나: 강화 skillUnlockLevel 단계부터 (각성했거나 성검이면 바로). */
    public boolean skillUnlocked(Weapon w) {
        if (w == null || b.weapon(w.defId).skill == null) return false;
        return isHoly(w) || w.awakened || w.level >= b.enhance.skillUnlockLevel;
    }

    /** 새로 열린 검 스킬을 빈 칸에 차례로 넣는다 (가진 검이 +10 이 되거나 흡수했을 때). 칸이 바뀌면 true. */
    public boolean noteOpenedSkills(GameState s) {
        boolean changed = false;
        for (Weapon w : s.weapons) {
            if (isHoly(w) || !skillUnlocked(w) || s.skillOrder.contains(w.defId)) continue;
            if (s.skillOrder.size() >= HOLY_SLOT) break;
            s.skillOrder.add(w.defId);
            changed = true;
        }
        for (String id : s.learnedSkills) {
            if (s.skillOrder.contains(id) || s.skillOrder.size() >= HOLY_SLOT) continue;
            s.skillOrder.add(id);
            changed = true;
        }
        return changed;
    }

    /**
     * 스킬 칸 넷 (0=L, 1=U, 2=I, 3=O). 아직 안 열린 칸은 null.
     * 검 스킬은 익혔거나 그 검을 들고 있어야 쓸 수 있고, 성검 스킬은 깨어난 성검을 들고 있어야 쓴다.
     */
    public SkillSlot[] skillSlots(GameState s) {
        SkillSlot[] out = new SkillSlot[SKILL_SLOTS];
        Weapon eq = s.equipped();
        for (int i = 0; i < Math.min(HOLY_SLOT, s.skillOrder.size()); i++) {
            String id = s.skillOrder.get(i);
            WeaponDef d = b.weapon(id);
            if (d.skill == null) continue;
            boolean learned = s.learnedSkills.contains(id);
            boolean holding = eq != null && eq.defId.equals(id) && skillUnlocked(eq);
            boolean aw = s.learnedAwakened.contains(id) || (holding && eq.awakened);
            out[i] = new SkillSlot(id, d.skill, aw, learned, learned || holding);
        }
        if (s.unsealed) {
            WeaponDef d = b.weapon(HOLY_AWAKENED);
            boolean holding = eq != null && eq.defId.equals(HOLY_AWAKENED);
            out[HOLY_SLOT] = new SkillSlot(HOLY_AWAKENED, d.skill, false, false, holding);
        }
        return out;
    }

    /**
     * 조작 키 창에 보일 스킬 칸 이름 (0=스킬 1 … 3=성검 스킬). 지금 쓸 수 있는 칸만 이름이 있고
     * (그 검을 들고 있거나 흡수해서 익혔을 때), 나머지는 null 이라 창에서 그 줄을 감춘다.
     * 2026-11-10 피드백: 검을 안 들었거나 흡수하지 않아 스킬을 못 쓸 때는 보이지 않아야 한다.
     */
    public String[] skillKeyNames(GameState s) {
        String[] out = new String[SKILL_SLOTS];
        SkillSlot[] slots = skillSlots(s);
        for (int i = 0; i < SKILL_SLOTS; i++) {
            if (slots[i] == null || slots[i].def == null || !slots[i].usable) continue;
            out[i] = (i == HOLY_SLOT ? "성검 스킬" : "스킬 " + (i + 1)) + " · " + slots[i].def.name;
        }
        return out;
    }

    public String weaponName(Weapon w) {
        WeaponDef d = b.weapon(w.defId);
        String name = w.awakened && d.awakenedName != null ? d.awakenedName : d.name;
        return d.enhanceable ? name + " +" + w.level : name;
    }

    public boolean isHoly(Weapon w) {
        return w != null && b.weapon(w.defId).holy;
    }
}
