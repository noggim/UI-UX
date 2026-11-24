package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.Balance.WeaponDef;

/** 대장간의 제작·분해·장착 (FR-대장간-02, 04, 14). */
public class ForgeService {
    private final Balance b;
    private final Rules rules;

    public ForgeService(Balance b) {
        this.b = b;
        this.rules = new Rules(b);
    }

    public String craftBlockReason(GameState s, WeaponDef d) {
        if (d.ore == null) return "만들 수 없는 무기입니다";
        OreType ore = OreType.valueOf(d.ore);
        if (s.ore(ore) < d.craftCost) return ore.displayName + "이 모자랍니다";
        return null;
    }

    /** 무기를 만든다. 지금 든 무기보다 세면 바로 장착한다. */
    public Weapon craft(GameState s, WeaponDef d) {
        String reason = craftBlockReason(s, d);
        if (reason != null) throw new IllegalStateException(reason);
        s.addOre(OreType.valueOf(d.ore), -d.craftCost);
        Weapon w = s.newWeapon(d.id);
        s.stats.crafted++;
        if (rules.attack(s, w) > rules.attack(s, s.equipped())) s.equippedUid = w.uid;
        return w;
    }

    public String dismantleBlockReason(GameState s, Weapon w) {
        if (w == null) return "무기를 고르세요";
        if (rules.isHoly(w)) return "성검은 분해할 수 없습니다";
        if (w.uid == s.equippedUid) return "장착한 무기는 분해할 수 없습니다";
        return null;
    }

    public int dismantle(GameState s, Weapon w) {
        String reason = dismantleBlockReason(s, w);
        if (reason != null) throw new IllegalStateException(reason);
        OreType ore = OreType.valueOf(b.weapon(w.defId).ore);
        s.weapons.remove(w);
        s.addOre(ore, b.enhance.dismantleRefund);
        return b.enhance.dismantleRefund;
    }

    public void equip(GameState s, Weapon w) {
        if (s.weapons.contains(w)) s.equippedUid = w.uid;
    }
}
