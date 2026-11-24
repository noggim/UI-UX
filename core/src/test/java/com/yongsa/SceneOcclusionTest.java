package com.yongsa;

import com.yongsa.map.SceneOcclusion;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneOcclusionTest {
    @Test
    void everyActiveSceneHasAnExistingFrontImageOrTracedOriginalPixels() throws Exception {
        SceneOcclusion data=SceneOcclusion.parse(Files.readString(Path.of("assets/data/scene-occlusion.json")));
        Set<String> active=new HashSet<>();
        for(var room:TestData.map().rooms) {
            if(room.scene==null)continue;
            active.add(room.scene.id);
            assertTrue(Files.exists(Path.of("assets",room.scene.image.replace(".png","-front.png")))
                    || data.scenes.containsKey(room.scene.id),"Uncovered room: "+room.id);
        }
        assertTrue(active.containsAll(data.scenes.keySet()),"Do not silently attach masks to unused scenes");
    }

    @Test
    void concaveMasksKeepAnExitCutoutOpen() {
        SceneOcclusion data=SceneOcclusion.parse("{\"version\":1,\"scenes\":{\"room\":[{\"id\":\"rim\",\"depth\":1,\"polygon\":[0,0.5,0.3,0.5,0.3,0.9,0.7,0.9,0.7,0.5,1,0.5,1,1,0,1]}]}}");
        var mask=data.scenes.get("room").get(0);
        assertTrue(mask.contains(.2f,.7f));
        assertTrue(mask.contains(.8f,.7f));
        assertFalse(mask.contains(.5f,.7f),"The doorway must stay transparent");
        assertTrue(mask.contains(.5f,.95f));
    }

    @Test
    void invalidDepthCannotPutAnObjectAboveTheWrongActors() {
        assertThrows(IllegalArgumentException.class,()->SceneOcclusion.parse("{\"version\":1,\"scenes\":{\"room\":[{\"id\":\"pillar\",\"depth\":0.1,\"polygon\":[0.2,0.4,0.3,0.4,0.3,0.7,0.2,0.7]}]}}"));
    }
}
