package com.yongsa.save;

import com.badlogic.gdx.Input;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 사용자가 바꿀 수 있는 조작 키 (2026-10-27 김선우 결정). 동작마다 키를 최대 {@link #SLOTS}개까지 둔다.
 * 설정 파일에는 {@code 동작 이름 → 키 코드 배열}로 저장되고, 빠진 동작은 기본값으로 채운다.
 * Esc(메뉴·취소)와 F1(개발용 메뉴)은 언제나 쓸 수 있어야 하므로 바꿀 수 없다.
 */
public final class KeyBindings {
    /** 동작 하나에 둘 수 있는 키 수. */
    public static final int SLOTS = 3;
    /** 빈 칸. */
    public static final int NONE = -1;

    public enum Action {
        MOVE_UP("위로 이동", Input.Keys.W, Input.Keys.UP),
        MOVE_DOWN("아래로 이동", Input.Keys.S, Input.Keys.DOWN),
        MOVE_LEFT("왼쪽으로 이동", Input.Keys.A, Input.Keys.LEFT),
        MOVE_RIGHT("오른쪽으로 이동", Input.Keys.D, Input.Keys.RIGHT),
        ATTACK("공격 (누르고 있으면 계속)", Input.Keys.J),
        ROLL("구르기 (잠깐 무적)", Input.Keys.K, Input.Keys.SPACE, Input.Keys.SHIFT_LEFT),
        SKILL_1("스킬 1", Input.Keys.L),
        SKILL_2("스킬 2", Input.Keys.U),
        SKILL_3("스킬 3", Input.Keys.I),
        SKILL_HOLY("깨어난 성검 스킬", Input.Keys.O),
        INTERACT("상호작용 (채굴·대장간·상자 등)", Input.Keys.E),
        BAG("가방", Input.Keys.B),
        MAP("지도 열기 / 닫기", Input.Keys.M, Input.Keys.TAB),
        ZOOM_IN("화면 확대", Input.Keys.EQUALS, Input.Keys.PLUS, Input.Keys.NUMPAD_ADD),
        ZOOM_OUT("화면 축소", Input.Keys.MINUS, Input.Keys.NUMPAD_SUBTRACT);

        public final String title;
        final int[] defaults;

        Action(String title, int... defaults) {
            this.title = title;
            this.defaults = defaults;
        }
    }

    /** 스킬 칸 0~3 의 동작. */
    public static final Action[] SKILLS = {Action.SKILL_1, Action.SKILL_2, Action.SKILL_3, Action.SKILL_HOLY};

    private final EnumMap<Action, int[]> keys = new EnumMap<>(Action.class);

    public KeyBindings() {
        resetAll();
    }

    /** 설정 파일에서 읽은 값. 모르는 이름은 버리고, 없는 동작·잘못된 키는 기본값으로 둔다. */
    public static KeyBindings fromSaved(Map<String, int[]> saved) {
        KeyBindings b = new KeyBindings();
        if (saved == null) return b;
        for (Action a : Action.values()) {
            int[] v = saved.get(a.name());
            if (v == null) continue;
            int[] slots = empty();
            int n = 0;
            for (int k : v) if (n < SLOTS && k != NONE && !isReserved(k) && !b.contains(slots, k)) slots[n++] = k;
            b.keys.put(a, slots);
        }
        // 같은 키가 두 동작에 걸려 있으면 (손으로 고친 파일) 앞 동작만 남긴다
        for (Action a : Action.values()) {
            for (int i = 0; i < SLOTS; i++) {
                int k = b.keys.get(a)[i];
                if (k == NONE) continue;
                Action owner = b.owner(k);
                if (owner != a) b.keys.get(a)[i] = NONE;
            }
        }
        return b;
    }

    /** 설정 파일에 쓸 값. */
    public Map<String, int[]> toSaved() {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (Action a : Action.values()) out.put(a.name(), keys.get(a).clone());
        return out;
    }

    private static int[] empty() {
        int[] e = new int[SLOTS];
        Arrays.fill(e, NONE);
        return e;
    }

    private boolean contains(int[] slots, int k) {
        for (int x : slots) if (x == k) return true;
        return false;
    }

    /** 바꿀 수 없는 키 (Esc 메뉴, F1 개발용 메뉴, Enter 창 선택). */
    public static boolean isReserved(int keycode) {
        return keycode == Input.Keys.ESCAPE || keycode == Input.Keys.F1 || keycode == Input.Keys.ENTER
                || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.UNKNOWN || keycode == Input.Keys.ANY_KEY;
    }

    public int key(Action a, int slot) {
        return keys.get(a)[slot];
    }

    /** 이 키가 이 동작의 키인가. */
    public boolean is(Action a, int keycode) {
        if (keycode == NONE) return false;
        for (int k : keys.get(a)) if (k == keycode) return true;
        return false;
    }

    /** 이 키를 쓰는 동작 (없으면 null). */
    public Action owner(int keycode) {
        for (Action a : Action.values()) if (is(a, keycode)) return a;
        return null;
    }

    /**
     * 동작 a 의 slot 칸에 키를 둔다. 다른 동작이 쓰던 키면 그쪽에서 빼고 그 동작을 돌려준다 (안내용).
     * 같은 동작의 다른 칸에 이미 있으면 그 칸을 비운다. 바꿀 수 없는 키면 IllegalArgumentException.
     */
    public Action bind(Action a, int slot, int keycode) {
        if (isReserved(keycode)) throw new IllegalArgumentException("바꿀 수 없는 키");
        Action previous = owner(keycode);
        if (previous != null) {
            int[] p = keys.get(previous);
            for (int i = 0; i < SLOTS; i++) if (p[i] == keycode) p[i] = NONE;
        }
        keys.get(a)[slot] = keycode;
        return previous == a ? null : previous;
    }

    public void clear(Action a, int slot) {
        keys.get(a)[slot] = NONE;
    }

    public void reset(Action a) {
        // 기본 키를 다른 동작이 가져가 있으면 그쪽에서 뺀다
        int[] d = empty();
        for (int i = 0; i < Math.min(SLOTS, a.defaults.length); i++) {
            int k = a.defaults[i];
            Action other = owner(k);
            if (other != null && other != a) {
                int[] o = keys.get(other);
                for (int j = 0; j < SLOTS; j++) if (o[j] == k) o[j] = NONE;
            }
            d[i] = k;
        }
        keys.put(a, d);
    }

    public void resetAll() {
        for (Action a : Action.values()) {
            int[] d = empty();
            for (int i = 0; i < Math.min(SLOTS, a.defaults.length); i++) d[i] = a.defaults[i];
            keys.put(a, d);
        }
    }

    /** 키가 하나도 없는 동작 (안내용). */
    public boolean unbound(Action a) {
        for (int k : keys.get(a)) if (k != NONE) return false;
        return true;
    }

    /** 첫 번째 키 이름 (화면 안내: "E  대장간"). 없으면 "-". */
    public String primaryName(Action a) {
        for (int k : keys.get(a)) if (k != NONE) return keyName(k);
        return "키 없음";
    }

    /** 모든 키 이름 ("J", "K / 스페이스"). */
    public String names(Action a) {
        StringBuilder sb = new StringBuilder();
        for (int k : keys.get(a)) {
            if (k == NONE) continue;
            if (sb.length() > 0) sb.append(" / ");
            sb.append(keyName(k));
        }
        return sb.length() == 0 ? "없음" : sb.toString();
    }

    /** 좁은 칸(스킬 칸 위)에 쓸 짧은 첫 키 이름. */
    public String primaryShort(Action a) {
        for (int k : keys.get(a)) {
            if (k == NONE) continue;
            switch (k) {
                case Input.Keys.SPACE: return "Space";
                case Input.Keys.SHIFT_LEFT: return "L-Shift";
                case Input.Keys.SHIFT_RIGHT: return "R-Shift";
                case Input.Keys.CONTROL_LEFT: return "L-Ctrl";
                case Input.Keys.CONTROL_RIGHT: return "R-Ctrl";
                case Input.Keys.NUMPAD_ADD: return "Num+";
                case Input.Keys.NUMPAD_SUBTRACT: return "Num-";
                default: return keyName(k);
            }
        }
        return "-";
    }

    /** 키 코드를 화면에 보일 이름으로. */
    public static String keyName(int keycode) {
        switch (keycode) {
            case NONE: return "비어 있음";
            case Input.Keys.UP: return "↑";
            case Input.Keys.DOWN: return "↓";
            case Input.Keys.LEFT: return "←";
            case Input.Keys.RIGHT: return "→";
            case Input.Keys.SPACE: return "스페이스";
            case Input.Keys.SHIFT_LEFT: return "왼쪽 Shift";
            case Input.Keys.SHIFT_RIGHT: return "오른쪽 Shift";
            case Input.Keys.CONTROL_LEFT: return "왼쪽 Ctrl";
            case Input.Keys.CONTROL_RIGHT: return "오른쪽 Ctrl";
            case Input.Keys.ALT_LEFT: return "왼쪽 Alt";
            case Input.Keys.ALT_RIGHT: return "오른쪽 Alt";
            case Input.Keys.TAB: return "Tab";
            case Input.Keys.EQUALS: return "=";
            case Input.Keys.PLUS: return "+";
            case Input.Keys.MINUS: return "-";
            case Input.Keys.NUMPAD_ADD: return "숫자패드 +";
            case Input.Keys.NUMPAD_SUBTRACT: return "숫자패드 -";
            case Input.Keys.BACKSPACE: return "Backspace";
            case Input.Keys.FORWARD_DEL: return "Delete";
            default:
                String s = Input.Keys.toString(keycode);
                return s == null ? "키 " + keycode : s;
        }
    }
}
