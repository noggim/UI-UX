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

/** 탑의 수문장: 번개 감옥, 소환 + 결계, 유도 마탄. */
class HouseKeeperTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession wardenFight(GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room("T6"));
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

    /** 보스를 방 한가운데, 용사를 dx 만큼 왼쪽에 세우고 다음 패턴을 하나로 정한다. */
    private Monster forcePattern(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        for (Monster o : g.monsters) if (!o.boss) o.dead = true;
        m.def.patterns = new ArrayList<>(List.of(pattern));
        m.def.phase2Patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        Room r = g.map.room("T6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x - dx;
        g.hero.y = m.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    private List<GameEvent> hitBoss(GameSession g, Monster m) {
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.enter(Monster.State.RECOVER, 5f);
        m.immuneMsgCd = 0;
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        List<GameEvent> ev = run(g, atk, 2 / 60f);
        ev.addAll(run(g, idle, 1 / 60f));
        return ev;
    }

    @Test
    void wardenLearnsCageAndHomingAndSummonsTwoOrThree() {
        Balance.MonsterDef w = b.dungeon("TOP").boss;
        assertTrue(w.patterns.contains("cage"));
        assertTrue(w.patterns.contains("homing"));
        assertTrue(w.phase2Patterns.contains("summon"));
        assertEquals(2, w.summonCount);
        assertEquals(3, w.summonCountMax);
    }

    @Test
    void lightningCageLeavesOneLateSide() {
        GameState s = hero();
        GameSession g = wardenFight(s);
        forcePattern(g, "cage", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Hazard> walls = new ArrayList<>();
        for (Hazard h : g.hazards) if ("cage".equals(h.style)) walls.add(h);
        long late = walls.stream().filter(h -> h.delay > b.monster.cageCoreWarning).count();
        assertEquals(1, late, "한쪽 벽만 가운데보다 늦게 터진다");
        assertEquals(3 * b.monster.cagePulses + 1, walls.size());
        assertTrue(g.hazards.stream().anyMatch(h -> "bolt".equals(h.style) && h.covers(g.hero.x, g.hero.y, 0)), "가운데는 용사 자리");
    }

    @Test
    void escapingThroughTheLateSideIsSafe() {
        GameState s = hero();
        GameSession g = wardenFight(s);
        Monster m = forcePattern(g, "cage", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        Hazard open = null;
        for (Hazard h : g.hazards) if ("cage".equals(h.style) && h.delay > b.monster.cageCoreWarning) open = h;
        assertNotNull(open);
        // 늦게 터지는 벽 쪽으로 걸어 나가 감옥 밖까지 간다
        float mx = (open.x + open.x2) / 2f - g.hero.x, my = (open.y + open.y2) / 2f - g.hero.y;
        float l = (float) Math.sqrt(mx * mx + my * my);
        Controls out = new Controls();
        out.moveX = mx / l;
        out.moveY = my / l;
        s.hp = 999;
        run(g, out, 1.4f);
        run(g, idle, b.monster.cageOpenDelay);
        assertEquals(999, s.hp, "늦게 터지는 벽으로 빠져나가면 안 맞는다");
    }

    @Test
    void stayingInsideTheCageGetsYouHit() {
        GameState s = hero();
        GameSession g = wardenFight(s);
        Monster m = forcePattern(g, "cage", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        s.hp = 999;
        run(g, idle, b.monster.cageCoreWarning + 0.1f);
        assertTrue(s.hp < 999);
    }

    @Test
    void summonsTwoOrThreeMinionsAtRandomWithoutABarrier() {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int seed = 0; seed < 12; seed++) {
            GameState s = hero();
            GameSession g = new GameSession(b, TestData.map(), s, seed);
            TestData.walkIn(g, g.map.room("T6"));
            Monster m = forcePattern(g, "summon", 5 * b.tileSize);
            run(g, idle, 2 / 60f);
            long n = g.monsters.stream().filter(o -> !o.boss && !o.dead).count();
            assertTrue(n >= 2 && n <= 3, "2~3마리: " + n);
            seen.add(n);
            // 결계는 없다: 부하가 살아 있어도 보스가 맞는다
            int hp = m.hp;
            hitBoss(g, m);
            assertTrue(m.hp < hp, "부하가 살아 있어도 상처를 낼 수 있다");
        }
        assertEquals(java.util.Set.of(2L, 3L), seen, "2마리도 3마리도 나온다");
    }

    @Test
    void homingBoltsCurveTowardTheHero() {
        GameState s = hero();
        GameSession g = wardenFight(s);
        Monster m = forcePattern(g, "homing", 5 * b.tileSize);
        g.hero.invuln = 99f;
        run(g, idle, b.monster.volleyTelegraph + 0.05f);
        List<Projectile> bolts = new ArrayList<>();
        for (Projectile p : g.projectiles) if (p.homing > 0) bolts.add(p);
        assertEquals(b.monster.homingCount, bolts.size());
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        // 용사가 위로 비켜서면 탄이 따라 휜다
        Projectile edge = bolts.get(0);
        g.hero.y += 3 * b.tileSize;
        float before = (float) Math.atan2(edge.vy, edge.vx);
        run(g, idle, 0.3f);
        float after = (float) Math.atan2(edge.vy, edge.vx);
        float want = (float) Math.atan2(g.hero.centerY() - edge.y, g.hero.x - edge.x);
        assertTrue(Math.abs(angle(after - want)) < Math.abs(angle(before - want)), "용사 쪽으로 방향을 튼다");
    }

    /** 유도 마탄 하나를 용사 오른쪽 dx 에 만들어 둔다 (보스는 멈춰 둔다). */
    private Projectile oneBolt(GameSession g, float dx) {
        Monster m = forcePattern(g, "homing", 5 * b.tileSize);
        run(g, idle, b.monster.volleyTelegraph + 0.05f);
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        Projectile bolt = null;
        for (Projectile p : g.projectiles) if (p.homing > 0) bolt = p;
        g.projectiles.clear();
        g.projectiles.add(bolt);
        bolt.x = g.hero.x + dx;
        bolt.y = g.hero.centerY();
        float sp = (float) Math.sqrt(bolt.vx * bolt.vx + bolt.vy * bolt.vy);
        bolt.vx = -sp;
        bolt.vy = 0;
        return bolt;
    }

    @Test
    void homingBoltsAreFasterAndShortLived() {
        assertTrue(b.monster.homingSpeedTiles > 3.2f, "예전보다 빠르다");
        assertTrue(b.monster.homingLife <= 3.5f, "예전(6초)보다 짧게 따라온다");
    }

    @Test
    void rollingThroughAHomingBoltMakesItVanish() {
        GameState s = hero();
        GameSession g = wardenFight(s);
        Projectile bolt = oneBolt(g, 0);
        g.hero.invuln = 0;
        g.hero.rollT = 0.3f;
        s.hp = 999;
        run(g, idle, 2 / 60f);
        assertTrue(g.projectiles.isEmpty(), "한 번 피하면 사라진다");
        assertEquals(999, s.hp);
    }

    @Test
    void homingStopsTurningOnceClose() {
        GameState s = hero();
        GameSession g = wardenFight(s);
        Projectile bolt = oneBolt(g, b.monster.homingStopTiles * b.tileSize * 0.8f);
        g.hero.invuln = 99f;
        run(g, idle, 1 / 60f);
        assertEquals(0f, bolt.homing, 0.0001f, "가까이 오면 더 꺾지 않는다");
        // 용사가 옆으로 비켜도 따라 꺾지 않는다
        g.hero.y += 2 * b.tileSize;
        float vy = bolt.vy;
        run(g, idle, 0.2f);
        assertEquals(vy, bolt.vy, 0.001f);
    }

    private static float angle(float a) {
        while (a > Math.PI) a -= (float) (2 * Math.PI);
        while (a < -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }
}
