package com.yongsa;

import com.yongsa.enemy.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.*;
import com.yongsa.save.SaveManager;
import com.yongsa.map.Door;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Progression smoke test, not a difficulty/play-time test. Travel and boss HP are fixture shortcuts. */
class CampaignJourneyTest {
    @TempDir Path saves;
    private final Balance balance = TestData.balance();
    private final Controls idle = new Controls();

    private List<GameEvent> tick(GameSession game, Controls input, int frames) {
        List<GameEvent> events = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            game.update(1 / 60f, input);
            events.addAll(game.pollEvents());
        }
        return events;
    }

    /** Real hit/death/phase handling, with HP and shield reduced to avoid testing balance here. */
    private List<GameEvent> defeatPhase(GameSession game, Monster boss) {
        boss.hp = 1;
        boss.shield = 0;
        boss.x = game.hero.x + 40;
        boss.y = game.hero.y;
        boss.enter(Monster.State.RECOVER, 5);
        game.hero.aimX = 1;
        game.hero.aimY = 0;
        game.hero.attackCd = 0;
        Controls attack = new Controls();
        attack.attack = true;
        return tick(game, attack, 3);
    }

    private GameSession resume(GameSession game) throws Exception {
        game.prepareSave();
        SaveManager manager = new SaveManager(saves);
        manager.save(game.state);
        SaveManager.LoadResult loaded = manager.load();
        assertFalse(loaded.corrupt);
        assertNotNull(loaded.state);
        GameSession next = new GameSession(balance, TestData.map(), loaded.state, 42);
        next.devGodMode = true;
        return next;
    }

    @Test
    void savedCampaignUnlocksThreeDungeonsTwoBossPhasesAndAllVillagers() throws Exception {
        GameState state = NewGame.create(balance);
        state.equippedUid = state.newWeapon("skyfall").uid;
        GameSession game = new GameSession(balance, TestData.map(), state, 42);
        game.devGodMode = true;
        assertTrue(game.inCamp());
        assertFalse(game.absorb.canUnseal(game.state));

        String[] dungeons = {"LEFT", "RIGHT", "TOP"};
        String[] rooms = {"L6", "R6", "T6"};
        for (int i = 0; i < rooms.length; i++) {
            TestData.walkIn(game, game.map.room(rooms[i]));
            assertTrue(game.fighting(), rooms[i]);
            List<GameEvent> events = defeatPhase(game, game.activeBoss());
            assertTrue(events.stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_DEFEATED));
            assertFalse(game.fighting());
            assertTrue(game.state.bossDefeated(dungeons[i]));
            assertTrue(game.hasChest(game.map.room(rooms[i])));
            game.openChest(game.map.room(rooms[i]));
            GameEvent reward = game.pollEvents().stream()
                    .filter(e -> e.type == GameEvent.Type.CHEST_OPENED).findFirst().orElseThrow();
            assertEquals(rooms[i], reward.opened);
            assertEquals(dungeons[i], reward.dungeonId);
            assertTrue(game.exitDungeon());
            game.useBed();
            game = resume(game);
            assertEquals(i + 1, game.state.bossesDefeated.size());
            assertTrue(game.state.chests.isEmpty(), "Opened rewards must not reappear after rest/load");
            if (i < 2) {
                String nextDoor = i == 0 ? "RIGHT" : "UP";
                Door gate = game.map.room("CAMP").doors.stream()
                        .filter(d -> d.name.equals(nextDoor)).findFirst().orElseThrow();
                assertFalse(gate.link.gateClosed, "Next dungeon remains open after loading");
            }
        }

        assertTrue(game.absorb.canUnseal(game.state));
        assertEquals(Rules.HOLY_AWAKENED, game.unseal().defId);
        game = resume(game);
        assertEquals(Rules.HOLY_AWAKENED, game.state.equipped().defId);
        game.dropToAbyss();
        Controls up = new Controls();
        up.moveY = 1;
        for (int i = 0; i < 600 && !game.fighting(); i++) tick(game, up, 1);
        assertTrue(game.fighting());
        Monster demon = game.activeBoss();
        assertTrue(defeatPhase(game, demon).stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_PHASE2));
        assertFalse(game.state.finalBossDefeated);
        for (int i = 0; i < 240 && demon.state == Monster.State.PHASE_SHIFT; i++) tick(game, idle, 1);
        assertNotEquals(Monster.State.PHASE_SHIFT, demon.state);
        List<GameEvent> ending = defeatPhase(game, demon);
        assertTrue(ending.stream().anyMatch(e -> e.type == GameEvent.Type.FINAL_DEFEATED));
        assertTrue(ending.stream().anyMatch(e -> e.type == GameEvent.Type.MONSTER_DIED && e.phase2));

        game = resume(game);
        assertTrue(game.inCamp(), "The completed abyss resumes safely in camp");
        assertTrue(game.state.finalBossDefeated);
        game.useBed();
        assertTrue(game.state.room("F").cleared, "Rest must not imprison villagers again");
        Door village = game.map.room("CAMP").doors.stream()
                .filter(d -> d.name.equals("down")).findFirst().orElseThrow();
        assertTrue(TestData.walkThrough(game, village));
        assertTrue(game.inVillage());
        for (Balance.Villager npc : balance.village.villagers) {
            game.hero.x = game.villagerX(npc);
            game.hero.y = game.villagerY(npc) - balance.tileSize;
            Controls interact = new Controls();
            interact.interact = true;
            assertTrue(tick(game, interact, 1).stream().anyMatch(e ->
                    e.type == GameEvent.Type.BOSS_SPEECH && npc.name.equals(e.speaker)
                            && npc.freeLines.contains(e.text)), npc.name);
        }
    }
}
