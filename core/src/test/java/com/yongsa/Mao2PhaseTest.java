package com.yongsa;

import com.yongsa.enemy.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 마왕 2페이즈: 돌진·낙뢰를 빼고 삼키기, 최후의 발악. */
class Mao2PhaseTest {
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

    /** 2페이즈 마왕을 방 한가운데 세우고 다음 패턴을 하나로 정한다. 용사는 dx 만큼 왼쪽. */
    private Monster phaseTwo(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.phase2 = true;
        m.enraged = true;
        m.def.phase2Patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        Room r = null;
        for (Room o : g.map.rooms) if (o.type == RoomType.FINAL) r = o;
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x - dx;
        g.hero.y = m.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    private void hit(GameSession g, Monster m) {
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        run(g, atk, 2 / 60f);
        run(g, idle, 1 / 60f);
    }

    @Test
    void phaseTwoPatternList() {
        List<String> p2 = b.finalBoss.phase2Patterns;
        assertFalse(p2.contains("rush"), "연속 돌진은 뺐다");
        assertFalse(p2.contains("whip"), "2026-11-10: 척추 채찍은 뺐다");
        assertFalse(p2.contains("lightning"), "낙뢰는 뺐다");
        assertFalse(p2.contains("devour"), "2026-11-07 피드백: 끌어당기는 삼키기는 억까라 뺐다");
        assertFalse(p2.contains("drain"), "마력 흡수는 뺐다");
        assertFalse(b.finalBoss.patterns.contains("rush"));
        assertFalse(b.finalBoss.patterns.contains("lightning"));
    }

    @Test
    void eyeHurtsOnlyWhenTheHeroLooksAtIt() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "eyes", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        com.yongsa.enemy.Hazard eye = null;
        for (com.yongsa.enemy.Hazard h : g.hazards) if (h.kind == com.yongsa.enemy.Hazard.Kind.EYE) { eye = h; break; }
        assertNotNull(eye);
        // 눈알 쪽을 보면 '보고 있음', 반대쪽을 보면 아님 (화면의 시선 줄도 이 판정을 그대로 쓴다)
        float dx = eye.x - g.hero.x, dy = eye.y - g.hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        g.hero.aimX = dx / l;
        g.hero.aimY = dy / l;
        assertTrue(g.lookingAt(eye));
        g.hero.aimX = -dx / l;
        g.hero.aimY = -dy / l;
        assertFalse(g.lookingAt(eye));
    }

    @Test
    void lastStandShortensThePatternGap() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "tentacle", 5 * b.tileSize);
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        m.hp = Math.round(m.maxHp * b.monster.lastStandHpRatio) + 1;
        g.hero.invuln = 99f;
        hit(g, m);
        assertTrue(m.lastStand, "체력 20% 아래에서 최후의 발악");
        assertTrue(m.patternCd <= 0.6f);
        // 다음 패턴부터는 간격이 짧다
        m.x = g.hero.x + 5 * b.tileSize;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        run(g, idle, 2 / 60f);
        assertEquals(b.monster.bossPatternGap * b.monster.lastStandGapMultiplier, m.patternCd, 0.1f);
    }

    @Test
    void notInPhaseOne() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.enter(Monster.State.RECOVER, 99f);
        m.hp = 10;
        g.hero.invuln = 99f;
        hit(g, m);
        assertFalse(m.lastStand, "1페이즈에서는 발악하지 않는다");
    }
}
