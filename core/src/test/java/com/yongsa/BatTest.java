package com.yongsa;

import com.yongsa.enemy.Monster;
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

/** 갱도 박쥐: 더 긴 덮치기, 초음파, 떼 지어 덮치기. */
class BatTest {
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

    /** L1 에 들어가 박쥐가 다 나타날 때까지 기다린 뒤, 첫 박쥐만 남기고 나머지는 멈춰 둔다. */
    private GameSession batRoom(Balance bal) {
        GameState s = NewGame.create(bal);
        GameSession g = new GameSession(bal, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room("L1"));
        assertTrue(g.fighting());
        g.hero.invuln = 99f;
        run(g, idle, 1.3f);
        return g;
    }

    private List<Monster> bats(GameSession g) {
        List<Monster> out = new ArrayList<>();
        for (Monster m : g.monsters) if (!m.dead && "swoop".equals(m.ai())) out.add(m);
        return out;
    }

    /** 박쥐를 용사에게서 dx 만큼 오른쪽, 방 한가운데에 세우고 바로 공격하게 한다. */
    private Monster ready(GameSession g, Monster m, float dx) {
        Room r = g.map.room("L1");
        g.hero.x = (r.centerTileX() + 0.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.attackCd = 0;
        return m;
    }

    private void freezeOthers(GameSession g, Monster keep) {
        for (Monster o : g.monsters) if (o != keep) o.enter(Monster.State.RECOVER, 99f);
    }

    @Test
    void swoopReachesFiveTiles() {
        assertEquals(5.0f, b.monster.swoopDistanceTiles, 0.001f);
    }

    @Test
    void spitsASmallFastPellet() {
        Balance bal = TestData.balance();
        bal.monster.pelletChance = 1f;
        GameSession g = batRoom(bal);
        Monster bat = bats(g).get(0);
        freezeOthers(g, bat);
        ready(g, bat, 3 * b.tileSize);
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CAST_TELEGRAPH, bat.state);
        assertEquals("pellet", bat.castKind);
        g.projectiles.clear();
        run(g, idle, bal.monster.pelletTelegraph);
        assertEquals(1, g.projectiles.size(), "콩알탄 하나");
        var p = g.projectiles.get(0);
        assertTrue(p.radius < bal.monster.projectileRadius, "보통 탄보다 작다");
        float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        assertTrue(sp > bal.monster.projectileSpeedTiles * b.tileSize, "보통 탄보다 빠르다");
        assertTrue(p.damage < bat.attack(), "약하다");
        assertTrue(p.vx < 0, "용사 쪽(왼쪽)으로 날아간다");
    }

    @Test
    void withoutPelletsBatsSwoop() {
        Balance bal = TestData.balance();
        bal.monster.pelletChance = 0f;
        GameSession g = batRoom(bal);
        Monster bat = bats(g).get(0);
        freezeOthers(g, bat);
        ready(g, bat, 1.2f * b.tileSize);
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.SWOOP_TELEGRAPH, bat.state);
    }

    @Test
    void aSecondBatJoinsTheSwoopSlightlyLater() {
        Balance bal = TestData.balance();
        bal.monster.pelletChance = 0f;
        bal.monster.packChance = 1f;
        GameSession g = batRoom(bal);
        List<Monster> bats = bats(g);
        assertTrue(bats.size() >= 2);
        Monster lead = bats.get(0), wing = bats.get(1);
        freezeOthers(g, lead);
        ready(g, lead, 3 * b.tileSize);
        wing.x = g.hero.x - 3 * b.tileSize;
        wing.y = g.hero.y;
        wing.enter(Monster.State.CHASE, 0);
        wing.attackCd = 5f;
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.SWOOP_TELEGRAPH, lead.state);
        assertEquals(Monster.State.SWOOP_TELEGRAPH, wing.state, "근처 박쥐가 같이 덮친다");
        assertEquals(lead.stateT + bal.monster.packDelay, wing.stateT, 0.05f);
        assertTrue(wing.lockX > 0.9f, "따라 덮치는 박쥐도 용사를 노린다");
    }
}
