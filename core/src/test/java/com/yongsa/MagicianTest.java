package com.yongsa;

import com.yongsa.enemy.Hazard;
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

/** 탑의 마족병: 세 번째마다 세 갈래, 불기둥, 순간이동 직후 사격. */
class MagicianTest {
    private final Controls idle = new Controls();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    /** T1 에 들어가 마족병 하나만 남기고, 용사는 방 한가운데, 마족병은 dx 오른쪽에 세운다. */
    private Monster lone(GameSession g, Balance b, float dx) {
        TestData.walkIn(g, g.map.room("T1"));
        assertTrue(g.fighting());
        g.hero.invuln = 99f;
        run(g, idle, 1.3f);
        Monster m = null;
        for (Monster o : g.monsters) {
            if (o.dead) continue;
            if (m == null && "caster".equals(o.ai())) m = o;
            else o.dead = true;
        }
        assertNotNull(m);
        Room r = g.map.room("T1");
        g.hero.x = (r.centerTileX() + 0.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.attackCd = 0;
        return m;
    }

    private GameSession session(Balance b) {
        GameState s = NewGame.create(b);
        return new GameSession(b, TestData.map(), s, 7);
    }

    /** 한 번 쏘게 하고 쏜 종류를 돌려준다. */
    private String castOnce(GameSession g, Monster m) {
        m.enter(Monster.State.CHASE, 0);
        m.attackCd = 0;
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CAST_TELEGRAPH, m.state);
        String kind = m.castKind;
        run(g, idle, 0.8f);
        return kind;
    }

    @Test
    void everyThirdShotIsAThreeWaySpread() {
        Balance b = TestData.balance();
        b.monster.flameChance = 0f;
        GameSession g = session(b);
        Monster m = lone(g, b, 3.5f * b.tileSize);
        assertEquals("cast", castOnce(g, m));
        assertEquals("cast", castOnce(g, m));
        g.projectiles.clear();
        m.x = g.hero.x + 3.5f * b.tileSize;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.attackCd = 0;
        run(g, idle, 2 / 60f);
        assertEquals("tri", m.castKind, "세 번째는 세 갈래");
        run(g, idle, m.stateT + 1 / 60f);
        assertEquals(3, g.projectiles.size());
    }

    @Test
    void flamePillarRisesUnderTheHero() {
        Balance b = TestData.balance();
        b.monster.flameChance = 1f;
        GameSession g = session(b);
        Monster m = lone(g, b, 3.5f * b.tileSize);
        run(g, idle, 2 / 60f);
        assertEquals("flame", m.castKind);
        run(g, idle, m.stateT + 1 / 60f);
        Hazard flame = null;
        for (Hazard h : g.hazards) if ("flame".equals(h.style)) flame = h;
        assertNotNull(flame, "불기둥 예고가 뜬다");
        assertTrue(flame.covers(g.hero.x, g.hero.y, 0), "용사 발밑");
        assertTrue(g.projectiles.isEmpty(), "불기둥을 쓸 때는 불덩이를 쏘지 않는다");
        g.hero.invuln = 0;
        g.state.hp = 999;
        run(g, idle, b.monster.flameWarning + 0.05f);
        assertTrue(g.state.hp < 999, "가만히 있으면 맞는다");
    }

    @Test
    void shootsRightAfterBlinking() {
        Balance b = TestData.balance();
        GameSession g = session(b);
        Monster m = lone(g, b, 1.2f * b.tileSize);
        m.blinkCd = 0;
        List<GameEvent> ev = run(g, idle, 2 / 60f);
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.BLINK_FX), "붙으면 순간이동한다");
        assertEquals(Monster.State.CAST_TELEGRAPH, m.state, "곧바로 쏠 준비");
        assertEquals(b.monster.blinkShotTelegraph, m.stateTotal, 0.01f);
        run(g, idle, b.monster.blinkShotTelegraph + 1 / 60f);
        assertFalse(g.projectiles.isEmpty(), "순간이동 직후 한 발");
    }
}
