package com.yongsa;

import com.yongsa.map.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Image-reviewed floor samples supplement the existing door-to-door centreline tests. */
class DungeonWalkabilityTest {
    private WorldMap unlocked() {
        var map=TestData.map(); for(var link:map.links) link.gateClosed=false;
        map.returnDoorOpen = room -> true; // Audit traversable floors with all doors open.
        return map;
    }

    @Test void visibleStairsLandingsAndMiningAlcovesFitTheHero() {
        var map=unlocked(); var failures=new ArrayList<String>();
        for(Object[] sample:new Object[][]{
                {"L1",.32f,.25f},{"L1",.495f,.25f},{"L1",.70f,.25f},
                {"L8",.07f,.48f},{"L8",.50f,.79f},{"R6",.50f,.205f}}) {
            var r=map.room((String)sample[0]);float u=(float)sample[1],v=(float)sample[2];
            if(!map.free(r.sceneX(u),r.sceneY(v),9,6)) failures.add(r.id+" "+u+","+v);
        }
        var j=map.paintedJoints.stream().filter(x->x.id.equals("exit-L2-L3")).findFirst().orElseThrow();
        for(float[] uv:new float[][]{{.15f,.78f},{.44f,.49f}})
            if(!map.free(j.x(uv[0]),j.y(uv[1]),9,6)) failures.add(j.id+" "+Arrays.toString(uv));
        for(var c:map.corridors) {
            float[][] samples=switch(c.scene.id) {
                case "mine-traverse" -> new float[][]{{.49f,.27f}};
                case "mine-rail-bend" -> new float[][]{{.68f,.38f}};
                case "mine-descent" -> new float[][]{{.29f,.20f},{.205f,.585f},{.26f,.785f}};
                default -> new float[0][];
            };
            for(float[] uv:samples) {
                float x=c.sceneX(uv[0]),y=c.sceneY(uv[1]);
                // A local repaint replaces the source scene here; audit its own floor instead.
                if(map.paintedJoints.stream().anyMatch(p->p.alpha(x,y)>=.5f))continue;
                if(!map.free(x,y,9,6)) failures.add(c.key()+" "+Arrays.toString(uv));
            }
        }
        assertTrue(failures.isEmpty(),"Visible floor blocked: "+failures);
    }

    @Test void everyRoomFloorAndActiveArrivalRemainReachable() {
        var map=unlocked(); int checked=0;
        for(var room:map.rooms) {
            float step=4; int nx=(int)Math.ceil(room.widthPx/step)+1,ny=(int)Math.ceil(room.heightPx/step)+1;
            boolean[] free=new boolean[nx*ny],seen=new boolean[nx*ny];
            for(int y=0;y<ny;y++)for(int x=0;x<nx;x++)
                free[x+y*nx]=map.free(room.originX+x*step,room.originY+y*step,9,6);
            int start=near(room.spawnX(map.tileSize),room.spawnY(map.tileSize),room,step,nx,ny,free);
            assertTrue(start>=0,room.id+" spawn");var queue=new ArrayDeque<Integer>();queue.add(start);seen[start]=true;
            while(!queue.isEmpty()) {
                int p=queue.removeFirst(),x=p%nx,y=p/nx;
                for(int k=0;k<4;k++) {
                    int xx=x+(k==0?-1:k==1?1:0), yy=y+(k==2?-1:k==3?1:0);
                    if(xx<0||yy<0||xx>=nx||yy>=ny)continue;int next=xx+yy*nx;
                    if(free[next]&&!seen[next]){seen[next]=true;queue.add(next);}
                }
            }
            for(var d:room.doors)if(!d.dead()) {
                int arrival=near(d.arriveX,d.arriveY,room,step,nx,ny,free);
                assertTrue(arrival>=0 && seen[arrival],room.id+"."+d.name+" disconnected arrival");checked++;
            }
            if(room.type!=RoomType.CAMP)for(int i=0;i<free.length;i++)
                assertFalse(free[i]&&!seen[i],room.id+" isolated floor at grid "+(i%nx)+","+(i/nx));
        }
        assertTrue(checked>=50,"Audit every placed doorway, including caves and mirrored rooms");
    }

    @Test void realHeroFeetCanWalkOntoRepairedStairsAndBack() {
        var map=unlocked();
        for(Object[] sample:new Object[][]{
                {"L1",.32f,.33f,.32f,.25f},{"L1",.495f,.33f,.495f,.25f},
                {"L1",.70f,.33f,.70f,.25f},{"L8",.20f,.56f,.07f,.48f},
                {"L8",.50f,.65f,.50f,.79f},{"R6",.50f,.32f,.50f,.205f}}) {
            var r=map.room((String)sample[0]);
            float ax=r.sceneX((float)sample[1]),ay=r.sceneY((float)sample[2]);
            float bx=r.sceneX((float)sample[3]),by=r.sceneY((float)sample[4]);
            var hero=new com.yongsa.combat.HeroActor();hero.x=ax;hero.y=ay;
            for(float[] target:new float[][]{{bx,by},{ax,ay}}) {
                assertFalse(com.yongsa.combat.Physics.move(map,hero,target[0]-hero.x,target[1]-hero.y,map.tileSize),r.id);
                assertEquals(target[0],hero.x,.03f); assertEquals(target[1],hero.y,.03f);
            }
        }
    }

    private int near(float wx,float wy,Room room,float step,int nx,int ny,boolean[] free) {
        int cx=Math.round((wx-room.originX)/step),cy=Math.round((wy-room.originY)/step),best=-1;
        float distance=Float.MAX_VALUE;
        for(int y=cy-2;y<=cy+2;y++)for(int x=cx-2;x<=cx+2;x++) {
            if(x<0||y<0||x>=nx||y>=ny||!free[x+y*nx])continue;
            float d=(x-cx)*(x-cx)+(y-cy)*(y-cy);
            if(d<distance){distance=d;best=x+y*nx;}
        } return best;
    }

    @Test void expandedFloorDoesNotOpenCliffsPropsOrLockedPassages() {
        var map=unlocked();
        var repaint=map.paintedJoints.stream().filter(j->j.id.equals("exit-L1-L8")).findFirst().orElseThrow();
        float rx=repaint.x(.0433f),ry=repaint.y(.1087f);
        assertTrue(repaint.alpha(rx,ry)>.5f);
        assertFalse(repaint.allowsFoot(rx,ry,0));
        assertFalse(map.free(rx,ry,9,6),"Dominant painted rock must hide the old alcove beneath it");
        var junction=map.room("L8");
        assertFalse(map.free(junction.sceneX(.5f),junction.sceneY(.92f),9,6),"Front cliff");
        var boss=map.room("R6");
        assertFalse(map.free(boss.sceneX(.35f),boss.sceneY(.17f),9,6),"Statue plinth");
        for(var c:map.corridors)if(c.scene.id.equals("mine-rail-bend")) {
            assertFalse(map.free(c.sceneX(.77f),c.sceneY(.35f),9,6),"Storage crates remain solid");
            c.link.doorA.lockedByFight=true;
            assertFalse(map.free(c.sceneX(.68f),c.sceneY(.38f),9,6),"Alcove cannot bypass fight lock");
        }
        var unused=map.room("L2").doors.stream().filter(d->d.name.equals("west")).findFirst().orElseThrow();
        assertTrue(unused.dead()); assertFalse(map.free(unused.footX,unused.footY,9,6),"No new exit through blocked mine end");
    }
}
