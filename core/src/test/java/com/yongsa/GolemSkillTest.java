package com.yongsa;

import com.yongsa.enemy.Hazard;
import com.yongsa.enemy.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 골렘 2페이즈 패턴: 지진 파동, 바위 던지기, 땅속 잠행. */
class GolemSkillTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession golemFight(GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room("L6"));
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
        // 방 한가운데에 용사, 그 오른쪽에 골렘 (벽에 걸리지 않게)
        com.yongsa.map.Room r = g.map.room("L6");
        g.hero.x = (r.centerTileX() - 1.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    @Test
    void golemGetsNewPatternsOnlyInPhaseTwo() {
        Balance.MonsterDef golem = b.dungeon("LEFT").boss;
        assertEquals(List.of("rush", "slam"), golem.patterns, "2026-11-10: 낙석은 뺐다");
        assertEquals(List.of("boulder", "pound"), golem.phase2Patterns, "지진 파동은 내려찍기에 합쳤다");
    }

    @Test
    void eachPoundSendsQuakeRingsFromTheInsideOut() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 6 * b.tileSize);
        g.hero.invuln = 99f;
        run(g, idle, 2 / 60f);
        m.patternCd = 99f;
        assertTrue(g.hazards.stream().noneMatch(h -> "quake".equals(h.style)), "찍기 전에는 파동이 없다");
        Hazard slam = null;
        for (Hazard h : g.hazards) if ("pound".equals(h.style)) { slam = h; break; }
        float sx = slam.x, sy = slam.y, sr = slam.radius;
        run(g, idle, b.monster.poundWarning + 1 / 60f);
        List<Hazard> rings = new ArrayList<>();
        for (Hazard h : g.hazards) if ("quake".equals(h.style)) rings.add(h);
        assertEquals(b.monster.quakeWaves, rings.size(), "찍는 순간 그 자리에서 파동 세 겹");
        assertEquals(sr, rings.get(0).innerRadius, 0.01f, "첫 고리는 찍은 자리 바로 바깥");
        for (Hazard r : rings) assertEquals(sx, r.x, 0.01f);
        for (int i = 1; i < rings.size(); i++) {
            assertTrue(rings.get(i).delay > rings.get(i - 1).delay, "바깥 고리가 더 늦게 터진다");
            assertEquals(rings.get(i - 1).radius, rings.get(i).innerRadius, 0.01f);
        }
        assertFalse(rings.get(rings.size() - 1).covers(sx, sy, 0), "고리 가운데는 비어 있다");
    }

    @Test
    void secondPoundLandsWhenTheFirstRingVanishes() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 6 * b.tileSize);
        g.hero.invuln = 99f;
        run(g, idle, 2 / 60f);
        m.patternCd = 99f;
        List<Hazard> slams = new ArrayList<>();
        for (Hazard h : g.hazards) if ("pound".equals(h.style)) slams.add(h);
        assertEquals(2, slams.size(), "두 번만 내려찍는다");
        Hazard second = slams.get(1);
        // 첫 번째가 떨어진 직후: 첫 파동의 첫 고리와 두 번째 내려찍기가 같은 순간에 터진다
        run(g, idle, b.monster.poundWarning + 1 / 60f);
        Hazard firstRing = null;
        for (Hazard h : g.hazards) if ("quake".equals(h.style) && (firstRing == null || h.delay < firstRing.delay)) firstRing = h;
        assertNotNull(firstRing);
        assertEquals(firstRing.delay, second.delay, 0.02f);
        assertFalse(second.dead);
        run(g, idle, firstRing.delay + 1 / 60f);
        assertTrue(firstRing.dead && second.dead, "첫 고리가 사라질 때 두 번째가 떨어진다");
        long rings = g.hazards.stream().filter(h -> "quake".equals(h.style) && h.x == second.x).count();
        assertEquals(b.monster.quakeWaves, rings, "두 번째 자리에서도 파동");
    }

    @Test
    void boulderShattersIntoShards() {
        GameState s = hero();
        GameSession g = golemFight(s);
        forcePattern(g, "boulder", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        Hazard rock = null;
        for (Hazard h : g.hazards) if ("boulder".equals(h.style)) rock = h;
        assertNotNull(rock, "용사 자리에 바위가 떨어진다");
        assertTrue(rock.covers(g.hero.x, g.hero.y, 0));
        g.activeBoss().enter(Monster.State.RECOVER, 99f);
        // 바위를 피해 옆으로 비켜서 있는다
        g.hero.invuln = 99f;
        g.projectiles.clear();
        run(g, idle, b.monster.boulderWarning + 0.05f);
        long shards = g.projectiles.stream().filter(p -> "golem".equals(p.source)).count();
        assertTrue(shards >= b.monster.shardCount - 1, "바위가 깨지며 파편이 튄다: " + shards);
    }

    @Test
    void poundSlamsTwiceTowardTheHero() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 6 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Hazard> slams = new ArrayList<>();
        for (Hazard h : g.hazards) if ("pound".equals(h.style)) slams.add(h);
        assertEquals(2, b.monster.poundCount);
        assertEquals(b.monster.poundCount, slams.size(), "쿵, 쿵");
        for (int i = 1; i < slams.size(); i++) {
            Hazard prev = slams.get(i - 1), cur = slams.get(i);
            assertTrue(cur.delay > prev.delay, "차례로 내려찍는다");
            assertTrue(cur.x < prev.x, "용사 쪽으로 나아간다");
            assertTrue(cur.radius > prev.radius, "갈수록 넓다");
        }
        assertEquals(Monster.State.RECOVER, m.state);
        assertTrue(m.stateT > slams.get(slams.size() - 1).delay + 0.5f, "다 찍고 나서 오래 숨을 고른다 (반격 기회)");
    }

    @Test
    void eachPoundShakesTheScreen() {
        GameState s = hero();
        GameSession g = golemFight(s);
        forcePattern(g, "pound", 6 * b.tileSize);
        g.hero.invuln = 99f;
        List<GameEvent> ev = run(g, idle, b.monster.poundWarning + b.monster.quakeGap * b.monster.poundCount + 0.1f);
        long shakes = ev.stream().filter(e -> e.type == GameEvent.Type.SLAM_FX).count();
        assertTrue(shakes >= b.monster.poundCount, "무거운 한 방마다 화면이 울린다: " + shakes);
    }

    @Test
    void standingInLineGetsPounded() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.patternCd = 99f;
        s.hp = 999;
        run(g, idle, b.monster.poundWarning + b.monster.quakeGap * b.monster.poundCount);
        assertTrue(s.hp < 999);
    }

    /** 연속 돌진을 방 한가운데에서 시작한다. 용사는 dx 오른쪽. */
    private Monster rush(GameSession g, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        com.yongsa.map.Room r = g.map.room("L6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x + dx;
        g.hero.y = m.y;
        g.hero.invuln = 99f;
        m.rushLeft = b.monster.rushCount;
        m.lastPattern = "rush";
        m.patternCd = 99f;
        m.dirX = m.lockX = 1; // 용사 쪽(오른쪽)을 노린다
        m.dirY = m.lockY = 0;
        m.enter(Monster.State.CHARGE_TELEGRAPH, 0.2f);
        return m;
    }

    @Test
    void firstTwoRushesStopShortOfTheWallWithoutReeling() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = rush(g, 3 * b.tileSize);
        int charges = 0, bumps = 0;
        Monster.State last = m.state;
        for (int i = 0; i < 60 * 10 && m.state != Monster.State.STUNNED; i++) {
            g.update(1 / 60f, idle);
            for (GameEvent e : g.pollEvents()) if (e.type == GameEvent.Type.SLAM_FX) bumps++;
            if (m.state == Monster.State.CHARGING && last != Monster.State.CHARGING) {
                charges++;
                assertEquals(0, bumps, "앞선 돌진에서는 벽에 부딪히지 않았다 (" + charges + "번째 시작)");
            }
            last = m.state;
            assertNotEquals(Monster.State.RECOVER, m.state, "중간에 쉬거나 휘청이지 않는다");
        }
        assertEquals(b.monster.rushCount, charges);
        assertEquals(Monster.State.STUNNED, m.state, "셋째 돌진 끝에 기절");
        assertEquals(b.monster.rushStun, m.stateTotal, 0.01f);
        assertTrue(bumps >= 1, "셋째는 벽에 부딪힌다");
    }

    @Test
    void thirdRushRunsAllTheWayIntoTheWall() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = rush(g, 3 * b.tileSize);
        m.rushLeft = 1;
        float x0 = m.x, wall = clear(g, m.x, m.y);
        for (int i = 0; i < 60 * 8 && m.state != Monster.State.STUNNED; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
        }
        assertEquals(Monster.State.STUNNED, m.state);
        float ran = (m.x - x0) / b.tileSize;
        assertTrue(ran > 3.5f, "용사(3칸)를 지나 계속 달린다");
        assertEquals(wall, ran, 0.5f, "벽에 닿을 때까지 달려간다");
    }

    private float clear(GameSession g, float x0, float y) {
        float d = 0;
        while (com.yongsa.combat.Physics.free(g.map, x0 + d + 4, y, g.activeBoss(), b.tileSize) && d < 40 * b.tileSize) d += 4;
        return d / b.tileSize;
    }

    @Test
    void golemNoLongerBurrows() {
        assertFalse(b.dungeon("LEFT").boss.phase2Patterns.contains("burrow"));
    }
}
