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
import com.yongsa.map.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 혈월의 기사: 사방 탄막·순간이동을 빼고 회전 베기, 혈월 검기, 뒤로 도약. */
class BloodKnightTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession knightFight(GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room("R6"));
        assertTrue(g.fighting());
        return g;
    }

    private GameState hero() {
        GameState s = NewGame.create(b);
        s.equippedUid = s.newWeapon("skyfall").uid;
        return s;
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private Monster forcePattern(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.def.patterns = new ArrayList<>(List.of(pattern));
        m.def.phase2Patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    @Test
    void knightDropsRingAndBlinkForSlashesAndALeap() {
        Balance.MonsterDef knight = b.dungeon("RIGHT").boss;
        assertFalse(knight.patterns.contains("ring"));
        assertFalse(knight.phase2Patterns.contains("ring"));
        assertTrue(knight.patterns.contains("crescent"), "혈월 검기");
        assertTrue(knight.phase2Patterns.contains("spinslash"), "2페이즈 회전 베기");
        assertFalse(knight.cornerBlink, "순간이동은 쓰지 않는다");
        assertTrue(knight.cornerLeap, "구석에서는 뒤로 도약한다");
        assertFalse(knight.patterns.contains("bloodpool"), "2026-11-10: 피웅덩이는 뺐다");
        assertTrue(knight.patterns.contains("spearstand"), "멈춰 서서 창 찌르기");
    }

    /** 기사를 방 한가운데, 용사를 그 왼쪽 dx 에 세우고 창 찌르기를 시작한다. */
    private Monster spearStand(GameSession g, float dx) {
        Monster m = forcePattern(g, "spearstand", dx);
        Room r = g.map.room("R6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x - dx;
        g.hero.y = m.y;
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CHANNEL, m.state);
        assertEquals("spearstand", m.channelKind);
        return m;
    }

    @Test
    void spearsComeOnlyWhileTheKnightStandsStill() {
        GameState s = hero();
        GameSession g = knightFight(s);
        Monster m = spearStand(g, 4 * b.tileSize);
        g.hero.invuln = 99f;
        float x0 = m.x, y0 = m.y;
        int volleys = 0;
        java.util.Set<Hazard> seen = new java.util.HashSet<>();
        for (int i = 0; i < 60 * 5 && m.state == Monster.State.CHANNEL; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            int fresh = 0;
            for (Hazard h : g.hazards) if ("spear".equals(h.style) && seen.add(h)) fresh++;
            if (fresh > 0) volleys++;
            assertEquals(x0, m.x, 0.01f, "창을 찌르는 동안은 멈춰 있다");
            assertEquals(y0, m.y, 0.01f);
        }
        assertEquals(b.monster.spearStandVolleys, volleys, "멈춰 있는 동안 여러 번 찌른다");
        assertEquals(b.monster.spearStandVolleys * b.monster.spearCount, seen.size());
        assertTrue(g.hazards.stream().noneMatch(h -> "spear".equals(h.style)), "다 터진 뒤에 움직인다");
    }

    @Test
    void movingAgainCancelsSpearsNotYetThrust() {
        GameState s = hero();
        GameSession g = knightFight(s);
        Monster m = spearStand(g, 4 * b.tileSize);
        g.hero.invuln = 99f;
        run(g, idle, 0.3f);
        assertTrue(g.hazards.stream().anyMatch(h -> "spear".equals(h.style) && h.delay > 0), "창 예고가 떠 있다");
        // 무슨 이유로든 기사가 다시 움직이기 시작하면
        m.enter(Monster.State.CHASE, 0);
        run(g, idle, 2 / 60f);
        assertTrue(g.hazards.stream().noneMatch(h -> "spear".equals(h.style)), "움직이면 창은 나오지 않는다");
        s.hp = 999;
        g.hero.invuln = 0;
        m.enter(Monster.State.RECOVER, 99f);
        run(g, idle, b.monster.spearStandWarning + 0.2f);
        assertEquals(999, s.hp);
    }

    @Test
    void standingInTheSpearLineGetsHit() {
        GameState s = hero();
        GameSession g = knightFight(s);
        spearStand(g, 4 * b.tileSize);
        s.hp = 999;
        run(g, idle, 0.25f + b.monster.spearStandWarning + 0.1f);
        assertTrue(s.hp < 999);
    }

    @Test
    void spinSlashCutsAroundTheKnight() {
        GameState s = hero();
        GameSession g = knightFight(s);
        Monster m = forcePattern(g, "spinslash", 2 * b.tileSize);
        run(g, idle, 2 / 60f);
        Hazard spin = null;
        for (Hazard h : g.hazards) if ("spin".equals(h.style)) spin = h;
        assertNotNull(spin);
        assertEquals(m.x, spin.x, 0.01f);
        assertTrue(spin.covers(g.hero.x, g.hero.y, 0), "가까이 붙어 있으면 범위 안");
        s.hp = 999;
        run(g, idle, b.monster.spinSlashWarning + 0.1f);
        assertTrue(s.hp < 999, "범위 안에 있으면 베인다");
    }

    @Test
    void crescentWavesFlyInAFan() {
        GameState s = hero();
        GameSession g = knightFight(s);
        Monster m = forcePattern(g, "crescent", 4 * b.tileSize);
        // 방 한가운데에 세운다 (탄이 벽 속에서 나가지 않게)
        Room r = g.map.room("R6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x - 4 * b.tileSize;
        g.hero.y = m.y;
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CAST_TELEGRAPH, m.state);
        assertEquals("crescent", m.castKind);
        g.hero.invuln = 99f;
        run(g, idle, b.monster.volleyTelegraph);
        List<Projectile> waves = new ArrayList<>();
        for (Projectile p : g.projectiles) if ("crescent".equals(p.source)) waves.add(p);
        assertEquals(b.monster.crescentCount, waves.size());
        assertTrue(waves.get(0).radius > b.monster.projectileRadius * 1.5f, "보통 탄보다 크다");
        assertEquals(Math.round(m.attack() * b.monster.crescentMultiplier), waves.get(0).damage);
    }

    @Test
    void corneredKnightLeapsBackInsteadOfBlinking() {
        GameState s = hero();
        GameSession g = knightFight(s);
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        Room r = g.map.room("R6");
        float T = b.tileSize;
        // 왼쪽 벽에 붙은 기사, 바로 옆에 용사
        m.x = (r.x0 + 1.6f) * T;
        m.y = (r.centerTileY() + 0.5f) * T;
        g.hero.x = m.x + 2.2f * T;
        g.hero.y = m.y;
        m.enter(Monster.State.RECOVER, 99f);
        m.blinkCd = 0;
        m.cornerT = b.monster.cornerTime + 1f;
        float x0 = m.x, y0 = m.y;
        List<GameEvent> ev = run(g, idle, 2 / 60f);
        assertEquals(Monster.State.LEAP, m.state, "구석에 몰리면 뒤로 도약한다");
        assertTrue(m.invulnerable(), "공중에서는 맞지 않는다");
        assertTrue(ev.stream().noneMatch(e -> e.type == GameEvent.Type.BLINK_FX), "순간이동은 하지 않는다");
        assertTrue(g.hazards.stream().anyMatch(h -> "leap".equals(h.style)), "착지할 자리에 충격파 예고");
        run(g, idle, b.monster.leapTime + 0.05f);
        assertNotEquals(Monster.State.LEAP, m.state);
        float dx = m.x - g.hero.x, dy = m.y - g.hero.y;
        assertTrue(dx * dx + dy * dy > 4 * T * 4 * T, "용사에게서 멀리 떨어진 곳에 착지한다");
        assertTrue(Math.abs(m.x - x0) + Math.abs(m.y - y0) > T, "실제로 움직였다");
    }
}
