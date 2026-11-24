package com.yongsa;

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

/** 몬스터 행동과 보스 패턴 (예고, 투사체, 기절, 2페이즈). */
class CombatTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    /** 방 문 앞에서 걸어 들어가 싸움을 시작한다. */
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

    private Monster boss(GameSession g) {
        return g.activeBoss();
    }

    @Test
    void monstersWaitOnTheirSpawnCircleBeforeActing() {
        GameState s = NewGame.create(b);
        GameSession g = fightIn("L1", s);
        for (Monster m : g.monsters) assertEquals(Monster.State.SPAWN, m.state);
        // 바로 옆에 붙어 있어도 나타나는 동안에는 맞지 않는다
        Monster m = g.monsters.get(0);
        m.x = g.hero.x + 20;
        m.y = g.hero.y;
        int hp = s.hp;
        run(g, idle, b.monster.spawnSeconds * 0.9f);
        assertEquals(hp, s.hp);
        run(g, idle, 1.0f);
        for (Monster x : g.monsters) assertNotEquals(Monster.State.SPAWN, x.state);
    }

    @Test
    void windingUpMonsterIsNotKnockedBack() {
        GameState s = NewGame.create(b);
        GameSession g = fightIn("R1", s);
        run(g, idle, 1.2f);
        Monster m = g.monsters.get(0);
        for (int i = 1; i < g.monsters.size(); i++) g.monsters.get(i).dead = true;
        m.x = g.hero.x + 30;
        m.y = g.hero.y;
        m.dirX = -1;
        m.dirY = 0;
        m.enter(Monster.State.TELEGRAPH, 0.5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        Controls atk = new Controls();
        atk.attack = true;
        float x0 = m.x;
        run(g, atk, 2 / 60f);
        assertTrue(m.hp < m.maxHp, "맞기는 한다");
        assertEquals(x0, m.x, 0.01f, "공격 준비 중에는 밀려나지 않는다");
    }

    @Test
    void projectileHurtsUnlessHeroRollsThroughIt() {
        GameState s = NewGame.create(b);
        GameSession g = fightIn("T1", s);
        for (Monster m : g.monsters) m.dead = true; // 방은 잠긴 채로 둔다
        g.monsters.add(new Monster(b.dungeon("TOP").mob, false, false, b.tileSize));
        g.monsters.get(g.monsters.size() - 1).enter(Monster.State.SPAWN, 99f);
        g.monsters.get(g.monsters.size() - 1).x = g.hero.x + 300;
        g.monsters.get(g.monsters.size() - 1).y = g.hero.y;

        Projectile p = new Projectile();
        p.x = g.hero.x + 60;
        p.y = g.hero.centerY();
        p.vx = -200;
        p.radius = 7;
        p.life = 3;
        p.damage = 20;
        g.projectiles.add(p);
        int hp = s.hp;
        g.hero.rollT = g.hero.rollTotal = 1f; // 구르는 중
        g.hero.rollX = 0;
        g.hero.rollY = 0;
        run(g, idle, 0.5f);
        assertEquals(hp, s.hp, "구르는 중에는 통과한다");

        g.hero.rollT = 0;
        g.hero.invuln = 0;
        Projectile q = new Projectile();
        q.x = g.hero.x + 60;
        q.y = g.hero.centerY();
        q.vx = -200;
        q.radius = 7;
        q.life = 3;
        q.damage = 20;
        g.projectiles.add(q);
        run(g, idle, 0.5f);
        assertTrue(s.hp < hp, "서 있으면 맞는다");
        assertTrue(g.projectiles.isEmpty());
    }

    @Test
    void casterShootsFromAfar() {
        GameState s = NewGame.create(b);
        GameSession g = fightIn("T1", s);
        boolean sawProjectile = false;
        for (int i = 0; i < 60 * 6 && !sawProjectile; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            s.hp = 100; // 쓰러지지 않게
            sawProjectile = !g.projectiles.isEmpty();
        }
        assertTrue(sawProjectile, "마족병은 멀리서 불덩이를 쏜다");
    }

    @Test
    void chargingBossIsStunnedWhenItHitsAWall() {
        GameState s = NewGame.create(b);
        GameSession g = fightIn("L6", s);
        Monster m = boss(g);
        run(g, idle, 2.0f);
        Room r = g.map.room("L6");
        // 용사는 비켜 서 있고, 보스는 오른쪽 벽을 향해 돌진한다
        g.hero.y = (r.y0 + 2.5f) * b.tileSize;
        g.hero.x = (r.x0 + 2.5f) * b.tileSize;
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 1.5f) * b.tileSize;
        m.lockX = 1;
        m.lockY = 0;
        m.dashLeft = 40 * b.tileSize;
        m.enter(Monster.State.CHARGING, 3f);
        boolean stunned = false;
        for (int i = 0; i < 120 && !stunned; i++) {
            g.update(1 / 60f, idle);
            stunned = m.state == Monster.State.STUNNED;
        }
        assertTrue(stunned, "벽에 부딪히면 기절해서 반격할 틈이 생긴다");
    }

    @Test
    void bossEntersSecondPhaseAtHalfHealth() {
        GameState s = NewGame.create(b);
        GameSession g = fightIn("L6", s);
        Monster m = boss(g);
        run(g, idle, 2.0f);
        m.hp = m.maxHp / 2 + 3;
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        Controls atk = new Controls();
        atk.attack = true;
        List<GameEvent> ev = run(g, atk, 0.1f);
        assertTrue(m.enraged);
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_ENRAGED));
    }
}
