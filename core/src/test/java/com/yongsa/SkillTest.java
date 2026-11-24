package com.yongsa;

import com.yongsa.enemy.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 검 스킬 (L·U·I 칸): 강화 단계에서 열리고, 검마다 모양이 다르고, 각성하면 강해지고, 흡수하면 어떤 검으로도 쓴다. */
class SkillTest {
    private final Balance b = TestData.balance();

    private GameSession fightWith(String weaponId, int level, boolean awakened) {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon(weaponId);
        w.level = level;
        w.awakened = awakened;
        s.equippedUid = w.uid;
        GameSession g = new GameSession(b, TestData.map(), s, 11);
        TestData.walkIn(g, g.map.room("L1"));
        assertTrue(g.fighting());
        run(g, new Controls(), 1.5f); // 마법진이 사라질 때까지
        return g;
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
            c.skill = -1;
        }
        return all;
    }

    /** 몬스터 하나만 남겨 용사 오른쪽 dx 에 세운다. */
    private Monster lineUp(GameSession g, float dx) {
        com.yongsa.map.Room r = g.map.room("L1");
        g.hero.x = (r.centerTileX() + 0.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
        Monster m = g.monsters.get(0);
        for (int i = 1; i < g.monsters.size(); i++) g.monsters.get(i).dead = true;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.hp = m.maxHp = 100000;
        m.attackCd = 99;
        m.patternCd = 99;
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        return m;
    }

    private Controls skill() {
        return skill(0);
    }

    private Controls skill(int slot) {
        Controls c = new Controls();
        c.skill = slot;
        return c;
    }

    @Test
    void skillIsLockedBelowUnlockLevel() {
        GameSession g = fightWith("blackiron", b.enhance.skillUnlockLevel - 1, false);
        lineUp(g, 60);
        List<GameEvent> ev = run(g, skill(), 0.1f);
        assertTrue(g.shots.isEmpty());
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.TOAST && e.text.contains("강화하면")));
    }

    @Test
    void blackironCleaveHitsAndStunsAndHasCooldown() {
        GameSession g = fightWith("blackiron", b.enhance.skillUnlockLevel, false);
        Monster m = lineUp(g, 70);
        run(g, skill(), 0.02f);
        assertEquals(1, g.shots.size());
        assertTrue(g.hero.skillCd[0] > 0);
        run(g, new Controls(), 0.4f);
        assertTrue(m.hp < m.maxHp, "충격파에 맞는다");
        assertEquals(Monster.State.STUNNED, m.state);
        run(g, skill(), 0.02f);
        assertTrue(g.shots.isEmpty(), "쿨타임 동안은 다시 못 쓴다");
    }

    @Test
    void awakenedCleaveSplitsIntoThree() {
        GameSession g = fightWith("blackiron", 10, true);
        lineUp(g, 190);
        run(g, skill(), 0.01f); // 한 프레임: 벽에 닿기 전에 센다
        assertEquals(3, g.shots.size());
    }

    @Test
    void bloodmoonCrescentHealsOnHit() {
        GameSession g = fightWith("bloodmoon", b.enhance.skillUnlockLevel, false);
        lineUp(g, 80);
        g.state.hp = 10;
        run(g, skill(), 0.5f);
        assertTrue(g.state.hp > 10, "맞힌 만큼 회복");
    }

    @Test
    void skyfallStrikesNearestEnemies() {
        GameSession g = fightWith("skyfall", b.enhance.skillUnlockLevel, false);
        Monster m = lineUp(g, 120);
        m.y += 60; // 바라보는 방향이 아니어도 맞는다
        run(g, skill(), 0.02f);
        assertFalse(g.shots.isEmpty());
        run(g, new Controls(), 0.6f);
        assertTrue(m.hp < m.maxHp);
    }

    @Test
    void openedSkillsFillSlotsInOrderAndStayAfterAbsorbing() {
        GameState s = NewGame.create(b);
        Weapon black = s.newWeapon("blackiron");
        black.level = b.enhance.skillUnlockLevel;
        Weapon blood = s.newWeapon("bloodmoon");
        blood.level = b.enhance.skillUnlockLevel;
        s.equippedUid = blood.uid;
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        run(g, new Controls(), 0.05f);
        assertEquals(java.util.List.of("blackiron", "bloodmoon"), s.skillOrder, "열린 차례대로 L, U 칸");
        com.yongsa.model.Rules.SkillSlot[] slots = g.rules.skillSlots(s);
        assertFalse(slots[0].usable, "흑철검을 들지 않으면 L 칸은 못 쓴다");
        assertTrue(slots[1].usable);
        assertNull(slots[2]);

        g.absorb.absorbSkill(s, black);
        assertFalse(s.weapons.contains(black), "흡수한 검은 사라진다");
        slots = g.rules.skillSlots(s);
        assertTrue(slots[0].usable && slots[0].learned, "흡수한 스킬은 어떤 검을 들어도 쓴다");
        assertEquals("blackiron", slots[0].weaponId, "칸 자리는 그대로");
    }

    @Test
    void absorbedSkillWorksWithAnotherSword() {
        GameState s = NewGame.create(b);
        Weapon black = s.newWeapon("blackiron");
        black.level = b.enhance.skillUnlockLevel;
        GameSession g0 = new GameSession(b, TestData.map(), s, 3);
        assertNotNull(g0.absorb.skillAbsorbBlockReason(s, s.weaponByDef(com.yongsa.model.Rules.HOLY_SEALED)), "성검은 흡수할 수 없다");
        g0.absorb.absorbSkill(s, black);
        assertEquals(com.yongsa.model.Rules.HOLY_SEALED, s.equipped().defId, "들고 있던 검이 사라지면 남은 검을 든다");
        GameSession g = new GameSession(b, TestData.map(), s, 11);
        TestData.walkIn(g, g.map.room("L1"));
        run(g, new Controls(), 1.5f);
        lineUp(g, 70);
        run(g, skill(0), 0.02f);
        assertEquals(1, g.shots.size(), "봉인된 성검을 들고도 익힌 대지 가르기를 쓴다");
    }

    @Test
    void lowLevelSwordCannotBeAbsorbed() {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        w.level = b.enhance.skillUnlockLevel - 1;
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        assertNotNull(g.absorb.skillAbsorbBlockReason(s, w));
        assertThrows(IllegalStateException.class, () -> g.absorb.absorbSkill(s, w));
    }
}
