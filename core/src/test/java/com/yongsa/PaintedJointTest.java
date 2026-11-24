package com.yongsa;

import com.yongsa.map.PaintedJointLayout;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class PaintedJointTest {
    @Test void authoredPaintingsMatchCurrentMapAndRemainPassable() throws Exception {
        var map=TestData.map();
        var layout=PaintedJointLayout.parse(Files.readString(Path.of("assets/data/map.json")));
        assertEquals(35,layout.joints.size());
        for(var link:map.links)link.gateClosed=false;
        for(var j:layout.joints) {
            assertTrue(j.matches(map),"Stale world coordinates: "+j.id);
            var image=ImageIO.read(Path.of("assets",j.image).toFile());
            assertNotNull(image,j.id);
            assertEquals(j.rect[2]/j.rect[3],image.getWidth()/(float)image.getHeight(),.001f,"No artwork stretching: "+j.id);
            var door=map.room(j.room).doors.stream().filter(d->Math.abs(d.footX-j.door[0])<.02f && Math.abs(d.footY-j.door[1])<.02f).findFirst().orElseThrow();
            door.link.gateClosed=false;
            assertTrue(map.free(door.footX,door.footY,9,6),j.id);
            door.lockedByFight=true;
            assertFalse(map.free(door.footX,door.footY,9,6),"Repaint must not bypass a combat gate: "+j.id);
            for(int across=-224;across<=224;across+=2) {
                float x=door.footX-door.dirY*across,y=door.footY+door.dirX*across;
                boolean sameOpening=true;
                for(int k=0;k<=Math.abs(across);k+=2) {
                    float sign=Math.signum(across);
                    if(!j.allowsFoot(door.footX-door.dirY*k*sign,door.footY+door.dirX*k*sign,0)){sameOpening=false;break;}
                }
                if(sameOpening && j.owns(x,y) && j.allowsFoot(x,y,9))
                    assertFalse(map.free(x,y,9,6),"Repaint cannot open a route around a closed gate: "+j.id+" / "+across);
            }
            door.lockedByFight=false;
        }
    }

    @Test void changedMapCannotSilentlyReuseArtAtAnUnrelatedLocation() throws Exception {
        var map=TestData.map();
        var layout=PaintedJointLayout.parse(Files.readString(Path.of("assets/data/map.json")));
        for(var j:layout.joints) {
            j.door[0]+=map.tileSize;
            assertFalse(j.matches(map),j.id);
        }
    }

    @Test void redrawnStairsAndSideFloorReplaceOldCollisionSilhouettes() {
        var map=TestData.map();
        for(var link:map.links)link.gateClosed=false;
        for(var j:map.paintedJoints) {
            if(j.id.equals("mine-north")) {
                assertTrue(map.free(j.x(.50f),j.y(.45f),9,6),"New stair tread");
                assertFalse(map.free(j.x(.36f),j.y(.45f),9,6),"New left cliff cannot be walked on");
            } else if(j.id.equals("mine-side")) {
                assertTrue(map.free(j.x(.59f),j.y(.52f),9,6),"Floor where the old doorway wall stood");
                assertFalse(map.free(j.x(.50f),j.y(.32f),9,6),"Timber retaining wall");
            }
        }
    }

    @Test void everyAuditedRepairIsInstalledExactlyOnce() throws Exception {
        var audit=com.google.gson.JsonParser.parseString(Files.readString(Path.of("design/full-map-audit/findings.json"))).getAsJsonObject();
        var expected=new java.util.HashSet<String>();
        for(var item:audit.getAsJsonArray("endpointReviews")) {
            var row=item.getAsJsonObject();
            if(!row.get("status").getAsString().equals("유지"))expected.add(row.get("id").getAsString());
        }
        var actual=new java.util.HashSet<String>();
        for(var j:TestData.map().paintedJoints)if(j.image.startsWith("scenes/joints-v2/"))assertTrue(actual.add(j.id));
        assertEquals(39,expected.size());
        // Six camp connector ends are now separate cave transitions, restoring the source scenes.
        for(String room:java.util.List.of("L1","R1","T1"))for(String end:java.util.List.of("entry","exit"))
            assertTrue(expected.remove(end+"-CAMP-"+room));
        assertEquals(expected,actual);
    }
}
