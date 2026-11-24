package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.map.Door;
import com.yongsa.map.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 선우 버전의 마을: 캠프 아래 계단에서 통로로 이어진다. 용사는 몇 번 망설이다 나가고, 마을 사람들은 마왕을 잡아야 수정에서 풀린다. */
class VillageTest {
    private final Balance b = TestData.balance();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
            c.interact = false;
        }
        return all;
    }

    private static boolean heroSaid(List<GameEvent> ev) {
        return ev.stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_SPEECH && "용사".equals(e.speaker));
    }

    /** 캠프 아래 문 앞에서 통로를 따라 마을로 걸어가 본다. 마을에 나갔으면 true. */
    private boolean tryLeave(GameSession g, List<GameEvent> out) {
        Door down = g.map.room("CAMP").doors.stream().filter(d -> d.name.equals("down")).findFirst().get();
        g.hero.x = down.arriveX;
        g.hero.y = down.arriveY;
        TestData.walkThrough(g, down, out, () -> g.inVillage() || heroSaid(out));
        out.addAll(run(g, new Controls(), 0.6f));
        return g.inVillage();
    }

    @Test
    void heroRefusesToLeaveFiveTimesThenGivesUp() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        for (int i = 0; i < b.village.exitRefusals; i++) {
            List<GameEvent> ev = new ArrayList<>();
            assertFalse(tryLeave(g, ev), "아직은 나가지 않는다 " + i);
            assertTrue(g.inCamp());
            assertTrue(heroSaid(ev));
        }
        List<GameEvent> ev = new ArrayList<>();
        assertTrue(tryLeave(g, ev), "여섯 번째에는 체념하고 나간다");
        assertTrue(ev.stream().anyMatch(e -> b.village.giveUpLine.equals(e.text)));
    }

    @Test
    void villagersAreTrappedUntilTheDemonLordFalls() {
        GameState s = NewGame.create(b);
        s.exitRefusals = 99;
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        assertTrue(tryLeave(g, new ArrayList<>()));
        assertEquals(RoomType.VILLAGE, g.heroRoom().type);

        Balance.Villager v = b.village.villagers.get(0);
        g.hero.x = g.villagerX(v);
        g.hero.y = g.villagerY(v) - 1.0f * b.tileSize;
        Controls e = new Controls();
        e.interact = true;
        List<GameEvent> ev = run(g, e, 0.1f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.BOSS_SPEECH
                && "용사".equals(x.speaker) && x.text.contains(v.name)), "수정 속 사람을 살펴본다");

        s.finalBossDefeated = true;
        e.interact = true;
        ev = run(g, e, 0.1f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.BOSS_SPEECH
                && v.name.equals(x.speaker) && v.freeLines.contains(x.text)), "풀려난 사람과 이야기한다");
    }
}
