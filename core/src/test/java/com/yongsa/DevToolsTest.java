package com.yongsa;

import com.yongsa.enemy.Projectile;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.map.Room;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 개발용 메뉴 (F1) 기능. */
class DevToolsTest {
    private final Balance b = TestData.balance();

    private void step(GameSession g, int frames) {
        for (int i = 0; i < frames; i++) {
            g.update(1 / 60f, new Controls());
            g.pollEvents();
        }
    }

    @Test
    void revivedBossFightsAgainWithoutLosingAbsorbedPower() {
        GameState s = NewGame.create(b);
        s.bossesDefeated.add("LEFT");
        s.room("L6").cleared = true;
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        int maxHp = g.rules.maxHp(s);
        Room boss = g.map.room("L6");
        assertTrue(g.hasPortal(boss));

        assertTrue(g.devReviveBoss("LEFT"));
        assertFalse(g.devReviveBoss("LEFT"), "이미 기다리는 보스는 또 되살리지 않는다");
        assertFalse(g.hasPortal(boss), "되살린 동안에는 귀환 포탈이 없다");
        assertTrue(s.bossDefeated("LEFT"), "흡수한 힘은 그대로");
        assertEquals(maxHp, g.rules.maxHp(s));
        assertFalse(g.map.links.stream().filter(l -> "RIGHT".equals(l.gate)).findFirst().get().gateClosed,
                "열린 다음 던전도 그대로");

        TestData.walkIn(g, boss);
        assertTrue(g.fighting());
        assertNotNull(g.activeBoss());

        g.debugKillAll();
        step(g, 1);
        assertFalse(g.fighting());
        assertTrue(g.hasPortal(boss), "다시 잡으면 포탈이 돌아온다");
        assertEquals(maxHp, g.rules.maxHp(s), "두 번 흡수하지 않는다");

        assertTrue(g.hasChest(boss), "다시 잡아도 보물상자가 나온다");
        int before = s.ore(com.yongsa.model.OreType.BLACKIRON);
        g.openChest(boss);
        int got = s.ore(com.yongsa.model.OreType.BLACKIRON) - before;
        assertTrue(got >= 30 && got <= 40, "상자 광물 30~40개: " + got);
        assertFalse(g.hasChest(boss), "한 번 열면 사라진다");
    }

    @Test
    void aliveBossCannotBeRevived() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        assertFalse(g.devReviveBoss("LEFT"));
        assertFalse(g.devReviveBoss("FINAL"));
    }

    @Test
    void godModeTakesNoDamageAndOresCanBeFilled() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        g.devGodMode = true;
        Projectile p = new Projectile();
        p.x = g.hero.x + 40;
        p.y = g.hero.centerY();
        p.vx = -200;
        p.radius = 7;
        p.life = 2;
        p.damage = 50;
        g.projectiles.add(p);
        int hp = s.hp;
        step(g, 30);
        assertEquals(hp, s.hp);

        g.devFillOres(999);
        for (OreType t : OreType.values()) assertEquals(999, s.ore(t));
    }
}
