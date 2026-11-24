package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.RoomRecord;
import com.yongsa.model.Vein;
import com.yongsa.model.Weapon;
import com.yongsa.save.SaveManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SaveManagerTest {
    @TempDir
    Path dir;

    @Test
    void roundTripKeepsEverything() throws Exception {
        Balance b = TestData.balance();
        GameState s = NewGame.create(b);
        s.addOre(OreType.BLOODMOON, 7);
        Weapon w = s.newWeapon("blackiron");
        w.level = 10;
        w.awakened = true;
        s.equippedUid = w.uid;
        s.imprints.add("blackiron");
        s.bossesDefeated.add("LEFT");
        s.hp = 123;
        RoomRecord rec = s.room("L2");
        rec.cleared = true;
        rec.veins = new ArrayList<>();
        rec.veins.add(new Vein(10, 11, OreType.BLACKIRON, 2, 1));
        s.resumeX = 321;
        s.resumeY = 654;

        SaveManager sm = new SaveManager(dir);
        sm.save(s);
        SaveManager.LoadResult r = sm.load();
        assertFalse(r.corrupt);
        GameState t = r.state;
        assertEquals(7, t.ore(OreType.BLOODMOON));
        assertEquals(2, t.weapons.size());
        assertEquals(10, t.equipped().level);
        assertTrue(t.equipped().awakened);
        assertEquals(s.nextUid, t.nextUid);
        assertEquals("blackiron", t.imprints.get(0));
        assertTrue(t.bossDefeated("LEFT"));
        assertEquals(123, t.hp);
        assertTrue(t.room("L2").cleared);
        assertEquals(1, t.room("L2").veins.size());
        assertEquals(1, t.room("L2").veins.get(0).hitsLeft);
        assertEquals(321, t.resumeX, 1e-6);
        assertFalse(Files.exists(dir.resolve("save.json.tmp")), "임시 파일은 남지 않는다");
    }

    @Test
    void corruptSaveIsReportedNotThrown() throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("save.json"), "{ 깨진 파일".getBytes(StandardCharsets.UTF_8));
        SaveManager.LoadResult r = new SaveManager(dir).load();
        assertTrue(r.corrupt);
        assertNull(r.state);
    }

    @Test
    void missingSaveIsNotCorrupt() {
        SaveManager.LoadResult r = new SaveManager(dir.resolve("none")).load();
        assertFalse(r.corrupt);
        assertNull(r.state);
    }
}
