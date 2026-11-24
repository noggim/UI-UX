package com.yongsa;

import com.badlogic.gdx.Input;
import com.google.gson.Gson;
import com.yongsa.save.KeyBindings;
import com.yongsa.save.KeyBindings.Action;
import com.yongsa.save.SaveManager;
import com.yongsa.save.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 사용자가 바꾸는 조작 키 (2026-10-27). */
class KeyBindingsTest {
    @TempDir Path dir;

    @Test
    void defaultsMatchTheOldControls() {
        KeyBindings k = new KeyBindings();
        assertEquals(Action.ATTACK, k.owner(Input.Keys.J));
        assertEquals(Action.ROLL, k.owner(Input.Keys.K));
        assertEquals(Action.ROLL, k.owner(Input.Keys.SPACE));
        assertEquals(Action.ROLL, k.owner(Input.Keys.SHIFT_LEFT));
        assertEquals(Action.INTERACT, k.owner(Input.Keys.E));
        assertEquals(Action.MOVE_UP, k.owner(Input.Keys.W));
        assertEquals(Action.MOVE_UP, k.owner(Input.Keys.UP));
        assertEquals(Action.MAP, k.owner(Input.Keys.TAB));
        assertEquals(Action.SKILL_HOLY, k.owner(Input.Keys.O));
        // 키 하나는 동작 하나에만
        Map<Integer, Action> seen = new HashMap<>();
        for (Action a : Action.values())
            for (int i = 0; i < KeyBindings.SLOTS; i++) {
                int key = k.key(a, i);
                if (key == KeyBindings.NONE) continue;
                assertNull(seen.put(key, a), Input.Keys.toString(key) + " 가 두 번");
            }
    }

    @Test
    void bindingAKeyTakesItAwayFromItsOldAction() {
        KeyBindings k = new KeyBindings();
        Action previous = k.bind(Action.ATTACK, 1, Input.Keys.SPACE);
        assertEquals(Action.ROLL, previous, "스페이스는 구르기에서 빠진다");
        assertTrue(k.is(Action.ATTACK, Input.Keys.SPACE));
        assertFalse(k.is(Action.ROLL, Input.Keys.SPACE));
        assertTrue(k.is(Action.ROLL, Input.Keys.K), "구르기의 다른 키는 그대로");
        assertNull(k.bind(Action.ATTACK, 2, Input.Keys.J), "같은 동작 안에서 옮기면 안내할 동작이 없다");
        assertEquals(KeyBindings.NONE, k.key(Action.ATTACK, 0));
        assertEquals(Input.Keys.J, k.key(Action.ATTACK, 2));
    }

    @Test
    void reservedKeysCannotBeBound() {
        KeyBindings k = new KeyBindings();
        assertThrows(IllegalArgumentException.class, () -> k.bind(Action.ATTACK, 0, Input.Keys.ESCAPE));
        assertThrows(IllegalArgumentException.class, () -> k.bind(Action.ATTACK, 0, Input.Keys.F1));
        assertThrows(IllegalArgumentException.class, () -> k.bind(Action.ATTACK, 0, Input.Keys.ENTER));
    }

    @Test
    void clearResetAndUnbound() {
        KeyBindings k = new KeyBindings();
        k.clear(Action.INTERACT, 0);
        assertTrue(k.unbound(Action.INTERACT));
        assertEquals("키 없음", k.primaryName(Action.INTERACT));
        k.bind(Action.BAG, 1, Input.Keys.E);
        k.reset(Action.INTERACT);
        assertEquals(Action.INTERACT, k.owner(Input.Keys.E), "기본으로 되돌리면 가져간 동작에서 다시 뺀다");
        assertFalse(k.is(Action.BAG, Input.Keys.E));
        k.bind(Action.ATTACK, 0, Input.Keys.F);
        k.resetAll();
        assertEquals(Action.ATTACK, k.owner(Input.Keys.J));
        assertNull(k.owner(Input.Keys.F));
    }

    @Test
    void savedThroughSettingsFileAndBrokenValuesAreFixed() {
        SaveManager saves = new SaveManager(dir);
        Settings s = saves.loadSettings();
        s.keys().bind(Action.ATTACK, 0, Input.Keys.F);
        s.keys().bind(Action.INTERACT, 0, Input.Keys.G);
        s.storeKeys();
        saves.saveSettings(s);

        Settings again = new SaveManager(dir).loadSettings();
        assertEquals(Action.ATTACK, again.keys().owner(Input.Keys.F));
        assertEquals(Action.INTERACT, again.keys().owner(Input.Keys.G));
        assertNull(again.keys().owner(Input.Keys.E));

        // 손으로 고친 파일: 예약 키·중복·모르는 동작은 버리고 빠진 동작은 기본값
        Map<String, int[]> bad = new HashMap<>();
        bad.put("ATTACK", new int[]{Input.Keys.ESCAPE, Input.Keys.F, Input.Keys.F});
        bad.put("BAG", new int[]{Input.Keys.F});
        bad.put("NOPE", new int[]{Input.Keys.Z});
        KeyBindings fixed = KeyBindings.fromSaved(bad);
        assertEquals(Input.Keys.F, fixed.key(Action.ATTACK, 0));
        assertEquals(KeyBindings.NONE, fixed.key(Action.ATTACK, 1));
        assertFalse(fixed.is(Action.BAG, Input.Keys.F), "같은 키는 앞 동작만");
        assertEquals(Action.INTERACT, fixed.owner(Input.Keys.E));
        assertNotNull(new Gson().toJson(fixed.toSaved()));
    }

    @Test
    void keyNamesAreReadable() {
        assertEquals("오른쪽 Shift", KeyBindings.keyName(Input.Keys.SHIFT_RIGHT));
        assertEquals("↑", KeyBindings.keyName(Input.Keys.UP));
        assertEquals("스페이스", KeyBindings.keyName(Input.Keys.SPACE));
        assertEquals("J", KeyBindings.keyName(Input.Keys.J));
        assertEquals("K / 스페이스 / 왼쪽 Shift", new KeyBindings().names(Action.ROLL));
    }
}
