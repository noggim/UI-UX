package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.Rules;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 조작 키 창: 스킬 키 줄은 지금 쓸 수 있는 스킬만(검을 들었거나 흡수), 이름과 함께 (2026-11-10 피드백). */
class SkillKeyNamesTest {
    private final Balance b = TestData.balance();
    private final Rules rules = new Rules(b);

    @Test
    void newGameShowsNoSkillRows() {
        GameState s = NewGame.create(b);
        String[] names = rules.skillKeyNames(s);
        assertEquals(4, names.length);
        for (String n : names) assertNull(n, "아직 연 스킬이 없다");
    }

    @Test
    void openedSkillShowsWithItsName() {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        w.level = b.enhance.skillUnlockLevel;
        s.equippedUid = w.uid;
        rules.noteOpenedSkills(s);
        String[] names = rules.skillKeyNames(s);
        assertEquals("스킬 1 · " + b.weapon("blackiron").skill.name, names[0]);
        assertNull(names[1]);
        assertNull(names[2]);
        assertNull(names[3], "성검 스킬은 봉인을 풀어야 보인다");
    }

    @Test
    void openedSkillHidesWhenTheSwordIsPutAway() {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        w.level = b.enhance.skillUnlockLevel;
        s.equippedUid = w.uid;
        rules.noteOpenedSkills(s);
        assertNotNull(rules.skillKeyNames(s)[0]);
        Weapon other = s.newWeapon("bloodmoon");
        s.equippedUid = other.uid;
        assertNull(rules.skillKeyNames(s)[0], "다른 검을 들면 그 스킬은 못 쓰니 감춘다");
    }

    @Test
    void absorbedSkillShowsWhateverSwordIsHeld() {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        w.level = b.enhance.skillUnlockLevel;
        s.equippedUid = w.uid;
        rules.noteOpenedSkills(s);
        s.learnedSkills.add("blackiron");
        Weapon other = s.newWeapon("bloodmoon");
        s.equippedUid = other.uid;
        assertEquals("스킬 1 · " + b.weapon("blackiron").skill.name, rules.skillKeyNames(s)[0], "흡수한 스킬은 어떤 검을 들어도 쓴다");
    }

    @Test
    void holySkillAppearsOnlyWhileHoldingTheHolySword() {
        GameState s = NewGame.create(b);
        s.unsealed = true;
        assertNull(rules.skillKeyNames(s)[3], "봉인을 풀어도 성검을 안 들면 감춘다");
        Weapon holy = s.newWeapon("holy_awakened");
        s.equippedUid = holy.uid;
        assertEquals("성검 스킬 · " + b.weapon("holy_awakened").skill.name, rules.skillKeyNames(s)[3]);
    }
}
