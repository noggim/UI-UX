package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;

/** 결과창·안내창: 제목, 줄 몇 개, 확인 버튼 하나 (UI-08). */
public class MessageWindow extends Modal {
    private final String title;
    private final ConfirmDialog.Line[] lines;
    private final String button;
    private final com.badlogic.gdx.graphics.Color titleColor;

    public MessageWindow(Skin skin, String title, com.badlogic.gdx.graphics.Color titleColor, ConfirmDialog.Line[] lines, String button) {
        super(skin);
        this.title = title;
        this.titleColor = titleColor;
        this.lines = lines;
        this.button = button;
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label(title, "large", titleColor)).padBottom(16).row();
        for (ConfirmDialog.Line l : lines) {
            Label lab = label(l.text, "default", l.color);
            lab.setWrap(true);
            lab.setAlignment(Align.center);
            p.add(lab).width(620).padBottom(8).row();
        }
        p.add(nav.add(button, this::close)).width(200).height(52).padTop(14);
    }
}
