package com.yongsa;

import com.yongsa.model.*;
import com.yongsa.map.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CaveTransitionTest {
    @Test void allThreeEntrancesWalkThroughDarknessAndReturnAlongTheSameRoute() {
        for (String direction : List.of("LEFT", "RIGHT", "UP")) {
            var state=NewGame.create(TestData.balance());
            state.bossesDefeated.addAll(List.of("LEFT","RIGHT","TOP"));
            for(String room:List.of("L1","R1","T1"))state.room(room).cleared=true;
            var g=new GameSession(TestData.balance(),TestData.map(),state,42);
            g.deferDoors=true;
            Door entry=g.map.room("CAMP").doors.stream().filter(d->d.name.equals(direction)).findFirst().orElseThrow();
            g.hero.x=entry.arriveX;g.hero.y=entry.arriveY;
            var events=new ArrayList<GameEvent>();
            assertTrue(TestData.walkTransition(g,entry,events,()->false),direction+" camp to cave");
            Room cave=g.heroRoom();
            assertEquals(RoomType.TUNNEL,cave.type);
            assertFalse(g.fighting());
            assertTrue(events.stream().anyMatch(e->e.type==GameEvent.Type.DOOR_ENTER),"Transition must request a fade");
            Door end=cave.doors.stream().filter(d->d.name.equals("exit")).findFirst().orElseThrow();
            Door start=entry.partner();
            switch(direction) {
                case "LEFT" -> { assertTrue(end.footX<start.footX-400); assertEquals(-1,end.dirX); }
                case "RIGHT" -> { assertTrue(end.footX>start.footX+400); assertEquals(1,end.dirX); }
                case "UP" -> { assertTrue(end.footY>start.footY+400); assertEquals(1,end.dirY); }
            }
            assertTrue(Math.hypot(end.arriveX-g.hero.x,end.arriveY-g.hero.y)>200,"The cave must be playable, not an immediate second warp");
            g.prepareSave();
            var resumed=new GameSession(TestData.balance(),TestData.map(),state,42);
            assertEquals(cave.id,resumed.heroRoom().id,"Saving in the cave resumes there");
            assertTrue(TestData.walkTransition(g,end,events,()->false),direction+" cave to dungeon");
            assertSame(end.partner().room,g.heroRoom());
            assertTrue(TestData.walkTransition(g,end.partner(),events,()->false),direction+" dungeon to cave");
            assertSame(cave,g.heroRoom());
            assertTrue(TestData.walkTransition(g,entry.partner(),events,()->false),direction+" cave to camp");
            assertTrue(g.inCamp());
        }
    }

}
