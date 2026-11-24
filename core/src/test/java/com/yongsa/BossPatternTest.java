package com.yongsa;

import com.yongsa.enemy.Hazard;
import com.yongsa.enemy.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.map.Prop;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 팀원 버전에서 가져온 보스 패턴: 방패, 바닥 공격, 연속 돌진, 마왕 2페이즈, 캠프 문양에서 심연으로. */
class BossPatternTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession fightIn(String roomId, GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room(roomId));
        assertTrue(g.fighting());
        return g;
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    /** 보스를 용사 바로 오른쪽에 세우고 한 번 벤다. */
    private List<GameEvent> hitBoss(GameSession g, Monster m) {
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        if (m.state != Monster.State.PHASE_SHIFT) m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        List<GameEvent> ev = run(g, atk, 2 / 60f);
        ev.addAll(run(g, idle, 1 / 60f));
        return ev;
    }

    private GameState strongHero() {
        GameState s = NewGame.create(b);
        s.equippedUid = s.newWeapon("skyfall").uid;
        return s;
    }

    @Test
    void shieldTakesDamageFirstThenStaggersTheBoss() {
        GameState s = strongHero();
        s.bossesDefeated.add("LEFT");
        GameSession g = fightIn("R6", s);
        Monster m = g.activeBoss();
        run(g, idle, 2.0f);
        m.shield = m.shieldMax = 100000;
        int hp = m.hp;
        hitBoss(g, m);
        assertEquals(hp, m.hp, "방패가 남아 있으면 체력은 그대로");
        assertTrue(m.shield < m.shieldMax);

        m.shield = 1;
        List<GameEvent> ev = hitBoss(g, m);
        assertEquals(0, m.shield);
        assertEquals(Monster.State.STUNNED, m.state, "방패가 깨지면 잠깐 휘청인다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.SHIELD_BREAK));
    }

    @Test
    void groundAttackHitsOnlyIfHeroStaysInIt() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        Monster m = g.activeBoss();
        m.enter(Monster.State.SPAWN, 99f); // 보스는 가만히
        s.hp = 100;

        Hazard stay = rock(g.hero.x, g.hero.y);
        g.hazards.add(stay);
        run(g, idle, 0.3f);
        assertEquals(100, s.hp, "예고 중에는 아프지 않다");
        run(g, idle, 0.4f);
        assertTrue(s.hp < 100, "그 자리에 있으면 맞는다");
        assertTrue(g.hazards.isEmpty() || stay.dead);

        g.hero.invuln = 0;
        int hp = s.hp;
        g.hazards.add(rock(g.hero.x, g.hero.y));
        Controls walk = new Controls();
        walk.moveY = 1;
        run(g, walk, 0.7f);
        assertEquals(hp, s.hp, "걸어서 벗어나면 안 맞는다");
    }

    private Hazard rock(float x, float y) {
        Hazard h = new Hazard(Hazard.Kind.CIRCLE, "rock");
        h.x = x;
        h.y = y;
        h.radius = 1.25f * b.tileSize;
        h.delay = h.warning = 0.5f;
        h.damage = 20;
        h.multiplier = 1f;
        return h;
    }

    @Test
    void bloodPoolSlowsAndDrains() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        g.activeBoss().enter(Monster.State.SPAWN, 99f);
        s.hp = 100;
        Hazard pool = new Hazard(Hazard.Kind.POOL, "blood");
        pool.x = g.hero.x;
        pool.y = g.hero.y;
        pool.radius = pool.startRadius = 2 * b.tileSize;
        pool.maxRadius = 2 * b.tileSize;
        pool.grow = 1;
        pool.delay = pool.warning = 0.1f;
        pool.life = 5;
        g.hazards.add(pool);
        run(g, idle, 2.0f);
        assertTrue(s.hp < 100, "밟고 있으면 체력이 닳는다");
        assertTrue(g.hero.slowT > 0, "느려진다");
    }

    @Test
    void golemRushesThreeTimesReAimingEachTime() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        Monster m = g.activeBoss();
        run(g, idle, 2.0f);
        Room r = g.map.room("L6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        m.rushLeft = b.monster.rushCount;
        m.enter(Monster.State.CHARGE_TELEGRAPH, 0.3f);
        int charges = 0;
        Monster.State last = m.state;
        for (int i = 0; i < 60 * 8 && m.state != Monster.State.STUNNED && m.state != Monster.State.RECOVER; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            s.hp = 999;
            g.hero.invuln = 1;
            if (m.state == Monster.State.CHARGING && last != Monster.State.CHARGING) charges++;
            last = m.state;
        }
        assertEquals(b.monster.rushCount, charges);
    }

    @Test
    void demonLordRisesAgainInsteadOfDying() {
        GameState s = NewGame.create(b);
        s.equippedUid = s.newWeapon("holy_awakened").uid; // 마왕은 깨어난 성검으로만 벨 수 있다
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        s.unsealed = true;
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        g.dropToAbyss();
        assertEquals(RoomType.FINAL, g.heroRoom().type);
        Controls up = new Controls();
        up.moveY = 1;
        for (int i = 0; i < 600 && !g.fighting(); i++) {
            g.update(1 / 60f, up);
            g.pollEvents();
        }
        assertTrue(g.fighting(), "심연에 떨어져 조금 걸으면 마왕과 싸움이 시작된다");
        Monster m = g.activeBoss();
        assertTrue(m.finalBoss);
        s.hp = 999;
        int max1 = m.maxHp;
        m.hp = 1;
        List<GameEvent> ev = hitBoss(g, m);
        assertFalse(m.dead, "한 번 쓰러뜨려도 끝나지 않는다");
        assertTrue(m.phase2);
        assertEquals(Monster.State.PHASE_SHIFT, m.state);
        assertTrue(m.invulnerable());
        assertEquals(Math.round(max1 * b.monster.finalPhase2HpMultiplier), m.maxHp);
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_PHASE2));
        for (int i = 0; i < 60 * 4 && m.state == Monster.State.PHASE_SHIFT; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            s.hp = 999;
        }
        assertEquals(m.maxHp, m.hp, "변신이 끝나면 체력이 다시 가득 찬다");
        assertFalse(s.finalBossDefeated);
        m.hp = 1;
        List<GameEvent> death = hitBoss(g, m);
        GameEvent corpse = death.stream().filter(e -> e.type == GameEvent.Type.MONSTER_DIED).findFirst().orElseThrow();
        assertTrue(m.dead);
        assertTrue(corpse.boss);
        assertTrue(corpse.phase2, "쓰러질 때도 마왕 본모습 시트를 유지한다");
        assertEquals(m.dirX, corpse.dirX, .001f, "사망 동작이 반대 방향으로 돌아서면 안 된다");
        assertEquals(2.3f, corpse.size, .001f, "2페이즈 사망 그림이 1페이즈 크기로 줄면 안 된다");
    }

    @Test
    void campSealUnsealsThenDropsIntoTheAbyss() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        Prop seal = null;
        for (Prop p : g.map.props) if (p.kind == Prop.Kind.SEAL) seal = p;
        assertNotNull(seal);
        assertTrue(seal.walkable(), "문양은 바닥에 그려져 있어 밟고 지나갈 수 있다");
        g.hero.x = (seal.tileX + 1) * b.tileSize;
        g.hero.y = (seal.tileY + 1) * b.tileSize;
        Controls e = new Controls();
        e.interact = true;

        List<GameEvent> ev = run(g, e, 1 / 60f);
        assertTrue(ev.stream().noneMatch(x -> x.type == GameEvent.Type.UNSEAL_READY), "보스 셋을 잡기 전엔 풀 수 없다");

        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        ev = run(g, e, 1 / 60f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.UNSEAL_READY));

        s.unsealed = true;
        ev = run(g, e, 1 / 60f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.FALL));
        g.dropToAbyss();
        assertEquals(RoomType.FINAL, g.heroRoom().type);
    }

    @Test
    void mobsInAClearedDungeonAreTougher() {
        GameState fresh = NewGame.create(b);
        GameSession a = fightIn("L1", fresh);
        int hp0 = a.monsters.get(0).maxHp;

        GameState s = NewGame.create(b);
        s.bossesDefeated.add("LEFT");
        GameSession g = fightIn("L1", s);
        Monster m = g.monsters.get(0);
        assertEquals(Math.round(hp0 * b.monster.clearedMobHpMultiplier), m.maxHp);
        assertEquals(b.monster.clearedMobAttackMultiplier, m.attackMul, 0.001f);
    }

    // ───────────── 선우 버전 패턴을 맞춘 것

    /** 보스가 다음에 쓸 패턴을 하나로 정해 두고, 용사 가까이 세운다. */
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
    void golemReelsAfterItsThirdRush() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        Monster m = g.activeBoss();
        run(g, idle, 2.0f);
        Room r = g.map.room("L6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x + 3 * b.tileSize;
        g.hero.y = m.y;
        m.rushLeft = 1; // 마지막 돌진
        m.lockX = 0;
        m.lockY = 1;
        m.dashLeft = 2 * b.tileSize;
        m.enter(Monster.State.CHARGING, 1f);
        for (int i = 0; i < 60 && m.state == Monster.State.CHARGING; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
        }
        assertEquals(Monster.State.STUNNED, m.state, "세 번째 돌진 뒤에는 휘청인다");
        assertEquals(b.monster.rushStun, m.stateTotal, 0.01f);
    }

    @Test
    void skeletonPriestRaisesBoneSpikes() {
        GameState s = strongHero();
        GameSession g = fightIn("T6", s);
        forcePattern(g, "bonespike", 5 * b.tileSize);
        List<GameEvent> ev = run(g, idle, 2 / 60f);
        long spikes = g.hazards.stream().filter(h -> "bone".equals(h.style)).count();
        assertTrue(spikes >= 3, "뼈 가시가 여러 개 솟는다: " + spikes);
        assertTrue(g.hazards.stream().anyMatch(h -> "bone".equals(h.style) && h.covers(g.hero.x, g.hero.y, 0)), "하나는 용사 발밑");
        // 2026-11-07 플레이 피드백: 실명은 의미가 없어 껐다 (monster.blindSeconds = 0)
        assertTrue(g.blindT <= 0, "앞이 캄캄해지지 않는다");
        assertTrue(ev.stream().noneMatch(e -> e.type == GameEvent.Type.TOAST && e.text.contains("캄캄")));
        s.hp = 999;
        int hp = s.hp;
        run(g, idle, 1.2f);
        assertTrue(s.hp < hp, "가만히 있으면 맞는다");
    }

    @Test
    void skeletonPriestSummonsSkeletonsFromTheCastle() {
        GameState s = strongHero();
        GameSession g = fightIn("T6", s);
        Monster boss = forcePattern(g, "summon", 5 * b.tileSize);
        for (Monster o : g.monsters) if (!o.boss) o.dead = true;
        run(g, idle, 2 / 60f);
        // 2026-11-10: 탑의 수문장은 자기 탑의 부하를 2~3마리 무작위로 부른다
        // (예전 이 검사는 summonCount 가 0 이라 0 == 0 으로 늘 통과하고 있었다)
        long minions = g.monsters.stream().filter(o -> !o.dead && !o.boss
                && o.def.sprite.equals(b.dungeon("TOP").mob.sprite)).count();
        assertTrue(minions >= boss.def.summonCount && minions <= boss.def.summonCountMax, "부하 " + minions + "마리");
    }

    /** 2026-10-27 버그: 스킬(검기)로 마왕 1페이즈를 마무리하면 ConcurrentModificationException 으로 게임이 꺼졌다. */
    @Test
    void finishingPhaseOneWithASkillShotDoesNotCrash() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = g.activeBoss();
        s.hp = 999;
        m.hp = 1;
        m.enter(Monster.State.RECOVER, 5f);
        for (int i = 0; i < 3; i++) { // 검기 여러 개가 동시에 날아가는 중
            com.yongsa.combat.HeroShot h = new com.yongsa.combat.HeroShot();
            h.kind = "beam";
            h.x = m.x;
            h.y = m.centerY();
            h.radius = 40;
            h.life = 1f;
            h.multiplier = 1f;
            g.shots.add(h);
        }
        assertDoesNotThrow(() -> run(g, idle, 0.5f));
        assertTrue(m.phase2, "스킬로 마무리해도 2페이즈로 넘어간다");
        assertTrue(g.shots.isEmpty(), "변신하면 날아가던 검기는 사라진다");
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

    @Test
    void demonWhipSweepsTheFrontArc() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "whip", 3 * b.tileSize);
        run(g, idle, 2 / 60f);
        long lines = g.hazards.stream().filter(h -> "whip".equals(h.style)).count();
        assertEquals(b.monster.whipLines, lines);
        s.hp = 999;
        run(g, idle, b.monster.whipWarning + b.monster.whipSweep + 0.2f);
        assertTrue(s.hp < 999, "채찍이 휩쓰는 자리에 서 있으면 맞는다");
    }

    @Test
    void demonEyesPunishLookingAtThem() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "eyes", 6 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Hazard> eyes = new ArrayList<>();
        for (Hazard h : g.hazards) if (h.kind == Hazard.Kind.EYE) eyes.add(h);
        assertFalse(eyes.isEmpty());
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99;
        // 첫 눈알만 남기고, 용사가 그 눈을 바라보게 한다
        Hazard eye = eyes.get(0);
        g.hazards.removeIf(h -> h != eye);
        eye.delay = 0.05f;
        s.hp = 800;
        float dx = eye.x - g.hero.x, dy = eye.y - g.hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        g.hero.aimX = dx / l;
        g.hero.aimY = dy / l;
        g.hero.invuln = 0;
        run(g, idle, 0.1f);
        assertEquals(400, s.hp, "눈이 마주치면 지금 체력의 절반을 잃는다");
        assertTrue(g.hero.stunT > 0, "몸이 굳는다");

        // 등을 돌리고 있으면 괜찮다
        Hazard eye2 = new Hazard(Hazard.Kind.EYE, "eye");
        eye2.x = eye.x;
        eye2.y = eye.y;
        eye2.radius = eye.radius;
        eye2.delay = eye2.warning = 0.05f;
        g.hazards.add(eye2);
        g.hero.stunT = 0;
        g.hero.invuln = 0;
        g.hero.aimX = -dx / l;
        g.hero.aimY = -dy / l;
        int before = s.hp;
        run(g, idle, 0.1f);
        assertEquals(before, s.hp);
    }
}
