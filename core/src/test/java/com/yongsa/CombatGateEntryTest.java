package com.yongsa;

import com.yongsa.combat.Physics;
import com.yongsa.model.*;
import com.yongsa.map.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatGateEntryTest {
    private GameSession session() {
        var balance=TestData.balance();
        var state=NewGame.create(balance);
        state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));
        state.unsealed=true;
        var game=new GameSession(balance,TestData.map(),state,42);
        game.devGodMode=true;
        return game;
    }

    private List<GameEvent> tick(GameSession game, Controls input) {
        game.update(1/60f,input);
        return game.pollEvents();
    }

    private List<GameEvent> enter(GameSession game, Door door) {
        game.hero.x=door.arriveX;game.hero.y=door.arriveY;
        var events=new ArrayList<>(tick(game,new Controls()));
        assertFalse(game.fighting(),door.room.id+" arrival must remain safe");
        var input=new Controls();
        for(int i=0;i<300&&!game.fighting();i++) {
            float x=(door.room.centerTileX()+.5f)*game.balance.tileSize-game.hero.x;
            float y=(door.room.centerTileY()+.5f)*game.balance.tileSize-game.hero.y;
            float length=(float)Math.hypot(x,y);
            input.moveX=x/Math.max(length,1);input.moveY=y/Math.max(length,1);
            events.addAll(tick(game,input));
        }
        assertTrue(game.fighting(),door.room.id+"."+door.name);
        assertTrue(Physics.free(game.map,game.hero.x,game.hero.y,game.hero,game.balance.tileSize),"Gate must not trap the feet");
        return events;
    }

    @Test void everyConnectedCombatEntranceLeavesArrivalSafeAndLocksAfterWalkingIn() {
        var layout=TestData.map();
        var directions=EnumSet.noneOf(Door.Side.class);
        for(var room:layout.rooms) {
            if(!room.type.hasFight())continue;
            for(var door:room.doors) {
                if(door.dead())continue;
                var game=session();
                var actual=game.map.room(room.id).doors.stream().filter(d->d.name.equals(door.name)).findFirst().orElseThrow();
                // Bosses marked as absorbed above have no portal unless this room was cleared.
                enter(game,actual);
                directions.add(actual.side);
                assertTrue(actual.along(game.hero.x,game.hero.y)<=-2.6f*game.balance.tileSize);
            }
        }
        assertEquals(EnumSet.allOf(Door.Side.class),directions);
    }

    @Test void walkingSidewaysAtAWideThresholdDoesNotCountAsEntering() {
        int checked=0;
        var layout=TestData.map();
        for(var room:layout.rooms)for(var door:room.doors) {
            if(!room.type.hasFight()||door.dead())continue;
            for(int sign:new int[]{-1,1}) {
                var game=session();
                var d=game.map.room(room.id).doors.stream().filter(x->x.name.equals(door.name)).findFirst().orElseThrow();
                game.hero.x=d.arriveX;game.hero.y=d.arriveY;
                tick(game,new Controls());
                float depth=-d.along(d.arriveX,d.arriveY);
                if(depth>=2.6f*game.balance.tileSize)continue;
                var input=new Controls();input.moveX=-d.dirY*sign;input.moveY=d.dirX*sign;
                for(int frame=0;frame<90;frame++) {
                    tick(game,input);
                    if(game.heroRoom()!=d.room)break;
                    assertFalse(game.fighting(),room.id+"."+door.name+" sideways motion");
                    float distance=(float)Math.hypot(game.hero.x-d.footX,game.hero.y-d.footY);
                    if(distance>2.6f*game.balance.tileSize+2){checked++;break;}
                }
            }
        }
        assertTrue(checked>0,"Exercise a real doorway beyond the old circular threshold");
    }

    @Test void lockCueOccursOnceAndOnlyTheLastWaveReopensTheRoom() {
        for(String id:List.of("L3","R3","T3")) {
            var game=session();var room=game.map.room(id);
            assertTrue(room.waves>1,id);
            var door=room.doors.stream().filter(d->!d.dead()).findFirst().orElseThrow();
            var events=enter(game,door);
            assertEquals(1,events.stream().filter(e->e.type==GameEvent.Type.ROOM_LOCKED).count());
            assertEquals(1,events.stream().filter(e->e.type==GameEvent.Type.SOUND&&e.text.equals("gateClose_"+room.dungeonId)).count());
            assertFalse(tick(game,new Controls()).stream().anyMatch(e->e.type==GameEvent.Type.ROOM_LOCKED));
            assertTrue(game.monsters.size()>1);
            for(int i=1;i<game.monsters.size();i++)game.monsters.get(i).dead=true;
            events=tick(game,new Controls());
            assertTrue(game.fighting(),"One remaining enemy must keep the gates locked");
            assertFalse(events.stream().anyMatch(e->e.type==GameEvent.Type.ROOM_CLEARED||e.type==GameEvent.Type.WAVE_STARTED));
            for(int wave=0;wave<room.waves;wave++) {
                game.debugKillAll();events=tick(game,new Controls());
                boolean last=wave==room.waves-1;
                assertEquals(!last,game.fighting(),id+" wave "+wave);
                assertEquals(last,events.stream().anyMatch(e->e.type==GameEvent.Type.ROOM_CLEARED));
                assertEquals(last,events.stream().anyMatch(e->e.type==GameEvent.Type.SOUND&&e.text.equals("gateOpen_"+room.dungeonId)));
                for(var d:room.doors)assertEquals(!last,d.lockedByFight);
            }
            assertFalse(tick(game,new Controls()).stream().anyMatch(e->e.type==GameEvent.Type.ROOM_CLEARED));
        }
    }

    @Test void retreatBeforeEnteringLeavesNoDelayedCombatLock() {
        var game=session();var room=game.map.room("L1");
        var door=room.doors.stream().filter(d->d.link!=null&&d.partner().room.type==RoomType.TUNNEL).findFirst().orElseThrow();
        game.hero.x=door.arriveX;game.hero.y=door.arriveY;tick(game,new Controls());
        game.deferDoors=true;
        assertTrue(TestData.walkTransition(game,door,new ArrayList<>(),game::fighting),
                "Retreat along the cave opening, including its lateral arrival offset");
        assertEquals(RoomType.TUNNEL,game.heroRoom().type);
        for(int i=0;i<60;i++)assertFalse(tick(game,new Controls()).stream().anyMatch(e->e.type==GameEvent.Type.ROOM_LOCKED));
        assertFalse(game.fighting());
        assertFalse(door.lockedByFight);
    }
}
