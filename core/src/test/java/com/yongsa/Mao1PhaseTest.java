package com.yongsa;

import com.yongsa.enemy.Hazard;
import com.yongsa.enemy.Monster;
import com.yongsa.enemy.Projectile;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 마왕 1페이즈: 리치 패턴(뼈 가시, 해골병 소환, 늪, 실명)과 어둠 구체, 마왕의 낙인, 어둠 기둥. */
class Mao1PhaseTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private GameSession demonFight(GameState s) {
        s.equippedUid = s.newWeapon("holy_awakened").uid;
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        s.unsealed = true;
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        g.dropToAbyss();
        Controls up = new Controls();
        up.moveY = 1;
        for (int i = 0; i < 600 && !g.fighting(); i++) {
            g.update(1 / 60f, up);
            g.pollEvents();
        }
        assertTrue(g.fighting());
        return g;
    }

    private Monster forcePattern(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.def.patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    /** 패턴을 쓰고 나면 보스를 멈춰 둔다 (다음 패턴이 섞이지 않게). */
    private void hold(Monster m) {
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
    }

    @Test
    void phaseOnePatternList() {
        Balance.MonsterDef d = b.finalBoss;
        assertFalse(d.patterns.contains("combo"), "콤보는 뺐다");
        assertEquals(List.of("ring", "darkorb", "brand"), d.patterns);
        for (String p : new String[]{"swamp", "pillars", "combo", "rush", "lightning", "bonespike", "summon", "slam", "charge", "whip"}) {
            assertFalse(d.patterns.contains(p), "피드백으로 뺐다: " + p);
        }
        assertTrue(d.noMeleePhase1, "1페이즈는 기본 근접 공격이 없다");
    }

    @Test
    void phaseOneKeepsItsDistanceInsteadOfMelee() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.patternCd = 99f;
        m.attackCd = 0;
        m.x = g.hero.x + 1.2f * b.tileSize;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        g.hero.invuln = 99f;
        boolean swung = false;
        for (int i = 0; i < 90; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            m.patternCd = 99f;
            if (m.state == Monster.State.TELEGRAPH) swung = true;
        }
        assertFalse(swung, "붙어 있어도 칼을 휘두르지 않는다");
        float dx = m.x - g.hero.x, dy = m.y - g.hero.y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        assertTrue(d > 1.8f * b.tileSize, "뒤로 물러나 거리를 둔다: " + d / b.tileSize + "칸");
    }

    @Test
    void darkOrbBurstsIntoEightShots() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "darkorb", 6 * b.tileSize);
        g.hero.invuln = 99f;
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CAST_TELEGRAPH, m.state);
        run(g, idle, b.monster.volleyTelegraph);
        hold(m);
        Projectile orb = null;
        for (Projectile p : g.projectiles) if (p.burst > 0) orb = p;
        assertNotNull(orb, "구체를 쏜다");
        // 용사를 비켜 세워 구체가 제 시간에 터지게 한다
        g.hero.y += 4 * b.tileSize;
        g.projectiles.removeIf(p -> p.burst == 0);
        run(g, idle, b.monster.orbFuse + 0.05f);
        long shots = g.projectiles.stream().filter(p -> p.burst == 0).count();
        assertTrue(shots >= b.monster.orbBurst - 1, "터지며 사방으로 탄: " + shots);
    }

    @Test
    void brandFollowsThenLocksAndExplodes() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "brand", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        hold(m);
        Hazard brand = null;
        for (Hazard h : g.hazards) if ("brand".equals(h.style)) brand = h;
        assertNotNull(brand);
        Controls walk = new Controls();
        walk.moveX = -1;
        run(g, walk, 0.5f);
        assertEquals(g.hero.x, brand.x, 1f, "낙인이 용사를 따라다닌다");
        run(g, idle, b.monster.brandFollow - 0.5f + 0.03f);
        float lx = brand.x;
        s.hp = 999;
        Controls away = new Controls();
        away.moveX = 1;
        run(g, away, b.monster.brandLock + 0.2f);
        assertEquals(lx, brand.x, 0.01f, "멈춘 뒤에는 따라오지 않는다");
        assertEquals(999, s.hp, "멈춘 자리를 벗어나면 안 맞는다");
    }

    @Test
    void minionsCrumbleWhenTheDemonLordFalls() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "summon", 5 * b.tileSize);
        m.def.summonFrom = "RIGHT"; // 마왕은 이제 소환하지 않지만, 혹시 남은 부하가 있으면 함께 사라지는지 본다
        m.def.summonCount = 2;
        run(g, idle, 2 / 60f);
        assertTrue(g.monsters.stream().anyMatch(o -> !o.boss && !o.dead));
        // 2페이즈에서 마지막 일격
        m.phase2 = true;
        m.hp = 1;
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        g.hero.invuln = 99f;
        Controls atk = new Controls();
        atk.attack = true;
        run(g, atk, 2 / 60f);
        assertTrue(m.dead);
        assertTrue(g.monsters.stream().noneMatch(o -> !o.dead), "부하도 함께 무너진다");
    }
}
