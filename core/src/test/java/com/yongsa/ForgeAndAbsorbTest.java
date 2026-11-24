package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.AbsorbService;
import com.yongsa.model.ForgeService;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.NextTask;
import com.yongsa.model.OreType;
import com.yongsa.model.Rules;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForgeAndAbsorbTest {
    private final Balance b = TestData.balance();
    private final ForgeService forge = new ForgeService(b);
    private final AbsorbService absorb = new AbsorbService(b);
    private final Rules rules = new Rules(b);

    @Test
    void newGameStartsWithSealedHolySword() {
        GameState s = NewGame.create(b);
        assertEquals(1, s.weapons.size());
        assertEquals(Rules.HOLY_SEALED, s.equipped().defId);
        assertEquals(10, rules.attack(s));
        assertEquals(100, rules.maxHp(s));
    }

    @Test
    void craftCostsEightOreAndEquipsStrongerWeapon() {
        GameState s = NewGame.create(b);
        assertNotNull(forge.craftBlockReason(s, b.weapon("blackiron")));
        s.addOre(OreType.BLACKIRON, 8);
        Weapon w = forge.craft(s, b.weapon("blackiron"));
        assertEquals(0, s.ore(OreType.BLACKIRON));
        assertEquals(w.uid, s.equippedUid);
        assertEquals(22, rules.attack(s));
    }

    @Test
    void dismantleRules() {
        GameState s = NewGame.create(b);
        Weapon holy = s.equipped();
        s.addOre(OreType.BLACKIRON, 16);
        Weapon a = forge.craft(s, b.weapon("blackiron"));
        Weapon c = forge.craft(s, b.weapon("blackiron"));
        assertNotNull(forge.dismantleBlockReason(s, holy));
        assertNotNull(forge.dismantleBlockReason(s, s.equipped()));
        Weapon spare = s.equippedUid == a.uid ? c : a;
        assertEquals(3, forge.dismantle(s, spare));
        assertEquals(3, s.ore(OreType.BLACKIRON));
    }

    @Test
    void nextWeaponAtZeroBeatsPreviousAtTen() {
        GameState s = NewGame.create(b);
        Weapon black = s.newWeapon("blackiron");
        black.level = 10;
        Weapon blood = s.newWeapon("bloodmoon");
        Weapon sky = s.newWeapon("skyfall");
        assertEquals(52, rules.attack(s, black));
        assertEquals(55, rules.attack(s, blood));
        blood.level = 10;
        assertEquals(105, rules.attack(s, blood));
        assertEquals(110, rules.attack(s, sky));
    }

    @Test
    void bossAbsorptionRaisesMaxHp() {
        GameState s = NewGame.create(b);
        assertEquals(150, absorb.absorbBoss(s, "LEFT"));
        assertEquals(250, rules.maxHp(s));
        assertEquals(250, s.hp);
        absorb.absorbBoss(s, "RIGHT");
        assertEquals(450, rules.maxHp(s));
        absorb.absorbBoss(s, "TOP");
        assertEquals(700, rules.maxHp(s));
    }

    @Test
    void awakenedAbilityWorksOnlyWhileHoldingTheSword() {
        // 2026-10-14: 각성 무기 흡수는 없앴다. 능력은 각성한 검을 들고 있을 때만.
        GameState s = NewGame.create(b);
        Weapon black = s.newWeapon("blackiron");
        black.level = 10;
        black.awakened = true;
        s.equippedUid = black.uid;
        assertTrue(rules.hasAbility(s, "blackiron"));
        Weapon blood = s.newWeapon("bloodmoon");
        s.equippedUid = blood.uid;
        assertFalse(rules.hasAbility(s, "blackiron"));
        assertEquals(55, rules.attack(s, blood), "흡수 보너스 없이 기본 공격력");
    }

    @Test
    void unsealAfterAllThreeBosses() {
        GameState s = NewGame.create(b);
        absorb.absorbBoss(s, "LEFT");
        absorb.absorbBoss(s, "RIGHT");
        assertFalse(absorb.canUnseal(s));
        absorb.absorbBoss(s, "TOP");
        assertTrue(absorb.canUnseal(s));
        Weapon sword = absorb.unseal(s);
        assertEquals(Rules.HOLY_AWAKENED, sword.defId);
        assertEquals(sword.uid, s.equippedUid);
        assertEquals(200, rules.attack(s));
        assertFalse(absorb.canUnseal(s));
    }

    @Test
    void nextTaskFollowsTheLoop() {
        GameState s = NewGame.create(b);
        assertEquals("왼쪽 던전으로 이동", NextTask.describe(s, b, false));
        assertEquals("왼쪽 던전 광물방에서 흑철석 모으기 0/8", NextTask.describe(s, b, true));
        s.addOre(OreType.BLACKIRON, 8);
        assertEquals("캠프 대장간에서 흑철검 제작", NextTask.describe(s, b, true));
        forge.craft(s, b.weapon("blackiron"));
        assertTrue(NextTask.describe(s, b, true).startsWith("왼쪽 던전 보스 잡기"));
    }
}
