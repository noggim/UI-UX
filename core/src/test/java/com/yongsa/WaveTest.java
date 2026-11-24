package com.yongsa;

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

/** 몇몇 잡몹방은 몬스터가 웨이브로 나뉘어 나온다 (지용 버전). */
class WaveTest {
    private final Balance b = TestData.balance();

    private List<GameEvent> killAllAndStep(GameSession g) {
        for (Monster m : g.monsters) m.dead = true;
        List<GameEvent> all = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            g.update(1 / 60f, new Controls());
            all.addAll(g.pollEvents());
        }
        return all;
    }

    @Test
    void waveRoomSpawnsTheNextGroupBeforeOpening() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 5);
        var room = g.map.room("L3");
        assertEquals(2, room.waves);
        TestData.walkIn(g, room);
        assertTrue(g.fighting());
        List<GameEvent> ev = killAllAndStep(g);
        assertTrue(g.fighting(), "첫 무리를 잡아도 다음 웨이브가 남아 있으면 문이 안 열린다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.WAVE_STARTED));
        assertTrue(g.monsters.stream().anyMatch(m -> !m.dead), "다음 무리가 나온다");
        killAllAndStep(g);
        assertFalse(g.fighting(), "마지막 웨이브를 잡으면 방이 열린다");
        assertTrue(s.room("L3").cleared);
    }

    @Test
    void ordinaryRoomHasOneWave() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 5);
        TestData.walkIn(g, g.map.room("L1"));
        killAllAndStep(g);
        assertFalse(g.fighting());
    }
}
