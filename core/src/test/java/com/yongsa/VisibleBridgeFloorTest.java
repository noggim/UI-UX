package com.yongsa;

import com.yongsa.model.*;
import com.yongsa.map.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisibleBridgeFloorTest {
    @Test void heroCanWalkBothWaysAcrossTheReportedTowerPavingSeam() {
        var map=TestData.map();
        var state=NewGame.create(TestData.balance());
        for(var r:map.rooms)state.room(r.id).cleared=true;
        var game=new GameSession(TestData.balance(),map,state,42);
        for(var l:map.links)l.gateClosed=false;
        var joint=map.paintedJoints.stream().filter(j->j.id.equals("exit-T1-T2")).findFirst().orElseThrow();
        float ax=joint.x(-.13f),ay=joint.y(.855f),bx=joint.x(.15f),by=joint.y(.745f);
        game.hero.x=ax;game.hero.y=ay;
        for(float[] end:new float[][]{{bx,by},{ax,ay}}) {
            var controls=new Controls();
            for(int frame=0;frame<180;frame++) {
                float dx=end[0]-game.hero.x,dy=end[1]-game.hero.y;
                float distance=(float)Math.hypot(dx,dy);
                if(distance<3)break;
                controls.moveX=dx/distance;controls.moveY=dy/distance;
                game.update(1/60f,controls);game.pollEvents();
            }
            assertTrue(Math.hypot(end[0]-game.hero.x,end[1]-game.hero.y)<3,
                    "Visible paving must not contain an invisible wall at the image seam");
        }
    }

    @Test void bothTowerBridgesExposeTheLowerPavingButKeepTheCliffSolid() {
        var map=TestData.map();for(var l:map.links)l.gateClosed=false;
        int checked=0;
        for(var c:map.corridors)if(c.scene.id.equals("tower-bridge")) {
            for(float[] uv:new float[][]{{.50f,.635f},{.65f,.56f},{.72f,.51f}})
                assertTrue(map.free(c.sceneX(uv[0]),c.sceneY(uv[1]),9,6),c.key());
            assertFalse(map.free(c.sceneX(.50f),c.sceneY(.77f),9,6),"Cliff face remains solid");
            checked++;
        }
        assertEquals(2,checked);
    }

    @Test void softEdgeUsesTheVisibleFloorAndStillHonorsCombatLocks() {
        var map=TestData.map();for(var l:map.links)l.gateClosed=false;
        var j=map.paintedJoints.stream().filter(p->p.id.equals("exit-T1-T2")).findFirst().orElseThrow();
        assertTrue(map.free(j.x(.05f),j.y(.82f),9,6),"Paving in the soft edge must use the new floor");
        assertFalse(map.free(j.x(.05f),j.y(.98f),9,6),"No extension onto the cliff");
        var door=map.room("T2").doors.stream().filter(d->d.name.equals("west")).findFirst().orElseThrow();
        door.lockedByFight=true;
        assertFalse(map.free(j.x(.05f),j.y(.82f),9,6),"Expanded paving must not bypass a fight lock");
    }
}
