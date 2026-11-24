package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.YongsaGame;
import com.yongsa.save.KeyBindings;
import com.yongsa.save.KeyBindings.Action;

/**
 * 조작 키 보기와 바꾸기 (명세서 4.2, 2026-10-27 김선우 결정).
 * 동작마다 키 칸이 세 개 있다. 칸을 고르고 Enter(또는 클릭) → 바꿀 키를 누르면 그 키로 바뀐다.
 * 다른 동작이 쓰던 키면 그쪽에서 빠진다는 안내가 뜬다. 기다리는 동안 Esc 는 취소, Backspace·Delete 는 칸 비우기.
 * Esc(메뉴), F1(개발용 메뉴), Enter(창 선택)는 바꿀 수 없다. 바꾼 키는 설정 파일에 바로 저장된다.
 * 스킬 키 줄은 그 스킬을 지금 쓸 수 있을 때만(검을 들었거나 흡수했을 때) 이름과 함께 보인다 (2026-11-10 피드백).
 */
public class ControlsWindow extends Modal {
    /** 바꿀 수 없는 키 안내. */
    public static final String[][] FIXED = {
            {"메뉴 · 창 닫기 · 취소", "Esc"},
            {"창 안의 버튼 이동 · 선택", "방향키 또는 W A S D, Tab, Enter (마우스도 가능)"},
            {"화면 확대 / 축소", "마우스 휠 (아래 키로도 가능)"},
            {"포션", "지나가면 주워서 바로 회복"},
            {"개발용 메뉴", "F1"},
    };

    private final YongsaGame game;
    /** 키를 바꾼 뒤 할 일 (게임 화면의 안내 문구 갱신). 없으면 null. */
    private final Runnable onChanged;
    /** 스킬 칸 이름 (KeyBindings.SKILLS 순서). 지금 못 쓰는 칸은 null 이고 그 줄은 감춘다. 타이틀 화면에서는 모두 null. */
    private final String[] skillNames;
    /** 새 키를 기다리는 칸. 없으면 null. */
    private Action waitingAction;
    private int waitingSlot;
    private String notice;
    private boolean noticeWarn;
    private float scrollY = -1;
    private ScrollPane scroll;

    /** 타이틀 화면: 진행 중인 게임이 없어 스킬 키 줄은 감춘다. */
    public ControlsWindow(YongsaGame game, Runnable onChanged) {
        this(game, onChanged, new String[KeyBindings.SKILLS.length]);
    }

    /** 게임 중: skillNames 는 열린 스킬 칸의 이름 (Rules.skillKeyNames). */
    public ControlsWindow(YongsaGame game, Runnable onChanged, String[] skillNames) {
        super(game.skin);
        this.game = game;
        this.onChanged = onChanged;
        this.skillNames = skillNames;
    }

    /** 이 동작의 스킬 칸 번호. 스킬이 아니면 -1. */
    private static int skillIndex(Action a) {
        for (int i = 0; i < KeyBindings.SKILLS.length; i++) if (KeyBindings.SKILLS[i] == a) return i;
        return -1;
    }

    /** 이 줄에 보일 이름 ("스킬 1 · 대지 가르기"). 지금 못 쓰는 스킬이면 null (줄을 감춘다). */
    private String title(Action a) {
        int si = skillIndex(a);
        if (si < 0) return a.title;
        return si < skillNames.length ? skillNames[si] : null;
    }

    /** 안내 문구에 쓸 이름: 감춘 스킬 칸이면 원래 이름. */
    private String nameOf(Action a) {
        String t = title(a);
        return t != null ? t : a.title;
    }

    private KeyBindings keys() {
        return game.settings.keys();
    }

    @Override
    public void build() {
        // 키 칸 셋 + “기본” 버튼이 한 줄: 위/아래 키는 다음 동작의 같은 칸으로 간다
        nav.columns = KeyBindings.SLOTS + 1;
        Table p = panel();
        p.add(label("조작 키", "large", Ui.GOLD)).padBottom(4).row();
        p.add(label("칸을 고르고 Enter(또는 클릭)한 뒤 바꿀 키를 누르세요. 한 동작에 키를 세 개까지 둘 수 있습니다.",
                "small", Ui.DIM)).padBottom(10).row();

        Table rows = new Table();
        rows.top().left();
        rows.add(label("동작", "small", Ui.DIM)).width(330).left();
        for (int i = 0; i < KeyBindings.SLOTS; i++) rows.add(label("키 " + (i + 1), "small", Ui.DIM)).width(150).left();
        rows.add().width(90).row();
        for (Action a : Action.values()) {
            String title = title(a);
            if (title == null) continue; // 지금 못 쓰는 스킬은 감춘다
            rows.add(label(title, "default", keys().unbound(a) ? Ui.ORANGE : Ui.TEXT)).width(330).left().padBottom(6);
            for (int i = 0; i < KeyBindings.SLOTS; i++) {
                int slot = i;
                boolean waiting = waitingAction == a && waitingSlot == i;
                int k = keys().key(a, i);
                String text = waiting ? "키를 누르세요…" : k == KeyBindings.NONE ? "·" : KeyBindings.keyName(k);
                TextButton b = nav.add(text, () -> startWaiting(a, slot));
                if (waiting) b.getLabel().setColor(Ui.GOLD);
                else if (k == KeyBindings.NONE) b.getLabel().setColor(Ui.DIM);
                rows.add(b).width(142).height(38).left().padRight(8).padBottom(6);
            }
            TextButton reset = nav.add("기본", () -> {
                keys().reset(a);
                changed("「" + title + "」을(를) 기본 키(" + keys().names(a) + ")로 되돌렸습니다.", false);
            });
            rows.add(reset).width(82).height(38).left().padBottom(6).row();
        }
        rows.add(label("바꿀 수 없는 키", "default", Ui.GOLD)).colspan(KeyBindings.SLOTS + 2).left().padTop(14).padBottom(6).row();
        for (String[] f : FIXED) {
            rows.add(label(f[0], "small", Ui.DIM)).width(330).left().padBottom(5);
            rows.add(label(f[1], "small", Ui.TEXT)).colspan(KeyBindings.SLOTS + 1).left().padBottom(5).row();
        }
        scroll = new ScrollPane(rows);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        p.add(scroll).width(900).height(430).row();
        if (scrollY >= 0) {
            scroll.layout();
            scroll.setScrollY(scrollY);
            scroll.updateVisualScroll();
        }

        Label info = label(notice != null ? notice
                        : waitingAction != null ? "「" + title(waitingAction) + "」 키 " + (waitingSlot + 1) + " — 바꿀 키를 누르세요.  Esc 취소 · Backspace/Delete 칸 비우기"
                        : "Esc 닫기 · 방향키/Tab 이동 · Enter 선택",
                "small", noticeWarn ? Ui.ORANGE : waitingAction != null ? Ui.GOLD : Ui.DIM);
        info.setWrap(true);
        p.add(info).width(900).padTop(8).row();

        Table foot = new Table();
        foot.add(nav.add("모두 기본값으로", () -> {
            keys().resetAll();
            changed("모든 조작 키를 기본값으로 되돌렸습니다.", false);
        })).width(240).height(48).padRight(12);
        foot.add(nav.add("닫기", this::close)).width(200).height(48);
        p.add(foot).padTop(8);
    }

    private void startWaiting(Action a, int slot) {
        waitingAction = a;
        waitingSlot = slot;
        notice = null;
        noticeWarn = false;
        keepScrollAndRebuild();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (waitingAction == null) {
            notice = null;
            return super.keyDown(keycode);
        }
        Action a = waitingAction;
        int slot = waitingSlot;
        waitingAction = null;
        if (keycode == Input.Keys.ESCAPE) {
            notice = "바꾸지 않았습니다.";
            noticeWarn = false;
            keepScrollAndRebuild();
            return true;
        }
        if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
            keys().clear(a, slot);
            changed("「" + nameOf(a) + "」 키 " + (slot + 1) + " 칸을 비웠습니다."
                    + (keys().unbound(a) ? " 이 동작은 이제 키가 없습니다!" : ""), keys().unbound(a));
            return true;
        }
        if (KeyBindings.isReserved(keycode)) {
            notice = KeyBindings.keyName(keycode) + " 키는 바꿀 수 없습니다. 다른 키를 골라 주세요.";
            noticeWarn = true;
            keepScrollAndRebuild();
            return true;
        }
        Action previous = keys().bind(a, slot, keycode);
        String msg = "「" + nameOf(a) + "」 키 " + (slot + 1) + " → " + KeyBindings.keyName(keycode);
        boolean warn = false;
        if (previous != null) {
            msg += ".  「" + nameOf(previous) + "」에서 쓰던 키라 그쪽에서는 뺐습니다.";
            if (keys().unbound(previous)) {
                msg += " 「" + nameOf(previous) + "」은(는) 이제 키가 없습니다!";
                warn = true;
            }
        }
        changed(msg, warn);
        return true;
    }

    @Override
    protected void onCancel() {
        if (waitingAction != null) {
            waitingAction = null;
            keepScrollAndRebuild();
            return;
        }
        super.onCancel();
    }

    private void changed(String message, boolean warn) {
        notice = message;
        noticeWarn = warn;
        game.saveSettings();
        if (onChanged != null) onChanged.run();
        keepScrollAndRebuild();
    }

    private void keepScrollAndRebuild() {
        scrollY = scroll != null ? scroll.getScrollY() : -1;
        rebuild();
    }
}
