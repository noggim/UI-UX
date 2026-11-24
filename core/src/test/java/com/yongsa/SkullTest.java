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

/** 저주받은 해골병: 방패 들기, 뼈 던지기, 한 번 다시 일어나기. */
class SkullTest {
    private final Controls idle = new Controls();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private GameSession skullRoom(Balance b) {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room("R1"));
        assertTrue(g.fighting());
        g.hero.invuln = 99f;
        run(g, idle, 1.3f);
        Room r = g.map.room("R1");
        g.hero.x = (r.centerTileX() + 0.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
        return g;
    }

    private List<Monster> skulls(GameSession g) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : g.monsters) if (!m.dead && m.def.guard) out.add(m);
        return out;
    }

    /** 해골병 하나만 용사 오른쪽에 세우고, 나머지는 멀리 치워 멈춰 둔다. */
    private Monster lone(GameSession g, float dx) {
        List<Monster> sk = skulls(g);
        Monster m = sk.get(0);
        for (Monster o : g.monsters) {
            if (o == m) continue;
            o.dead = true;
        }
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.dirX = -1;
        m.dirY = 0;
        m.attackCd = 99f;
        m.enter(Monster.State.CHASE, 0);
        return m;
    }

    /** 오른쪽을 보고 한 번 벤다. 해골병이 잃은 체력을 돌려준다. */
    private int slash(GameSession g, Monster m) {
        int hp = m.hp;
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        run(g, atk, 2 / 60f);
        return hp - m.hp;
    }

    @Test
    void skeletonMobGuardsAndRevivesButThrowsNothing() {
        Balance.MonsterDef d = TestData.balance().dungeon("RIGHT").mob;
        assertTrue(d.guard);
        assertTrue(d.revive);
    }

    @Test
    void raisedShieldBlocksMostFrontalDamage() {
        Balance b = TestData.balance();
        GameSession g = skullRoom(b);
        Monster m = lone(g, 40);
        m.guarding = false;
        m.guardT = 99f;
        int open = slash(g, m);
        assertTrue(open > 0);
        m.hp = m.maxHp;
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.dirX = -1;
        m.dirY = 0;
        m.enter(Monster.State.CHASE, 0);
        m.guarding = true;
        g.hero.attackCd = 0;
        run(g, idle, 0.6f);
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        int blocked = slash(g, m);
        assertTrue(blocked > 0 && blocked < open * 0.5f, "정면은 막힌다: " + blocked + " < " + open);
    }

    @Test
    void shieldDoesNotCoverTheBack() {
        Balance b = TestData.balance();
        b.monster.guardTurn = 0f; // 돌아서지 못하게
        GameSession g = skullRoom(b);
        Monster m = lone(g, 40);
        m.guarding = true;
        m.guardT = 99f;
        m.dirX = 1; // 용사에게 등을 보인다
        List<GameEvent> ev = new ArrayList<>();
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        ev.addAll(run(g, atk, 2 / 60f));
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.FLOAT_TEXT && e.tone == GameEvent.Tone.MONSTER_HIT),
                "등 뒤는 그대로 맞는다");
        assertTrue(ev.stream().noneMatch(e -> e.type == GameEvent.Type.FLOAT_TEXT && e.tone == GameEvent.Tone.SHIELD));
    }

    @Test
    void shieldedSkeletonTurnsSlowly() {
        Balance b = TestData.balance();
        GameSession g = skullRoom(b);
        Monster m = lone(g, 2 * b.tileSize);
        m.guarding = true;
        m.guardT = 99f;
        m.dirX = 1; // 등을 보인 채 시작
        run(g, idle, 0.2f);
        assertTrue(m.dirX > -0.95f, "방패를 들면 한 번에 돌아서지 못한다");
        run(g, idle, 1.5f);
        float hx = g.hero.x - m.x, hy = g.hero.y - m.y, hl = (float) Math.sqrt(hx * hx + hy * hy);
        assertTrue((m.dirX * hx + m.dirY * hy) / hl > 0.9f, "결국은 용사를 향한다");
    }

    @Test
    void neverThrowsBonesFromAfar() {
        // 2026-11-07 피드백: 해골병이 탄을 날리는 건 어색해서 뺐다
        Balance b = TestData.balance();
        GameSession g = skullRoom(b);
        lone(g, 4 * b.tileSize);
        g.projectiles.clear();
        run(g, idle, 3f);
        assertTrue(g.projectiles.isEmpty(), "멀리 있어도 아무것도 던지지 않는다");
    }

    @Test
    void risesAgainOnceThenStaysDown() {
        Balance b = TestData.balance();
        GameSession g = skullRoom(b);
        Monster m = lone(g, 40);
        m.guarding = false;
        m.guardT = 99f;
        m.hp = 1;
        slash(g, m);
        assertFalse(m.dead, "처음에는 무너지기만 한다");
        assertEquals(Monster.State.DOWNED, m.state);
        assertTrue(g.fighting(), "쓰러진 뼈가 남아 있으면 방이 끝나지 않는다");
        run(g, idle, b.monster.reviveSeconds + 0.1f);
        assertEquals(Math.round(m.maxHp * b.monster.reviveHpRatio), m.hp, "체력 30%로 다시 일어난다");

        // 두 번째로 쓰러지면 끝
        m.hp = 1;
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.guarding = false;
        m.enter(Monster.State.CHASE, 0);
        slash(g, m);
        assertTrue(m.dead, "두 번은 일어나지 않는다");
    }

    @Test
    void smashingTheBonePileFinishesIt() {
        Balance b = TestData.balance();
        GameSession g = skullRoom(b);
        Monster m = lone(g, 40);
        m.guarding = false;
        m.guardT = 99f;
        m.hp = 1;
        slash(g, m);
        assertEquals(Monster.State.DOWNED, m.state);
        g.hero.attackCd = 0;
        run(g, idle, 0.5f);
        slash(g, m);
        assertTrue(m.dead, "쓰러져 있을 때 때리면 완전히 부서진다");
    }
}
