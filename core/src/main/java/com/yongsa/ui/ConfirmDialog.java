package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

/** 확인창. 기본 선택은 “취소”이고 Esc 도 취소다 (UI-05). */
public class ConfirmDialog extends Modal {
    public static class Line {
        final String text;
        final Color color;

        public Line(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

    private final String title;
    private final Line[] lines;
    private final String confirmText;
    private final boolean danger;
    private final Runnable onConfirm;

    public ConfirmDialog(Skin skin, String title, Line[] lines, String confirmText, boolean danger, Runnable onConfirm) {
        super(skin);
        this.title = title;
        this.lines = lines;
        this.confirmText = confirmText;
        this.danger = danger;
        this.onConfirm = onConfirm;
    }

    public static ConfirmDialog simple(Skin skin, String title, String message, String confirmText, Runnable onConfirm) {
        return new ConfirmDialog(skin, title, new Line[]{new Line(message, null)}, confirmText, false, onConfirm);
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label(title, "large", danger ? Ui.RED : Ui.GOLD)).padBottom(14).row();
        for (Line l : lines) {
            Label lab = label(l.text, "default", l.color);
            lab.setWrap(true);
            lab.setAlignment(Align.center);
            p.add(lab).width(560).padBottom(6).row();
        }
        Table buttons = new Table();
        buttons.add(nav.add("취소", this::close)).width(180).height(52).padRight(16);
        buttons.add(nav.add(confirmText, () -> {
            close();
            onConfirm.run();
        }, danger)).width(220).height(52);
        p.add(buttons).padTop(16);
        nav.setFocusIndex(0);
    }
}
