package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/**
 * 게임 화면 위에 뜨는 창 (대장간, 가방, 메뉴, 확인창 …). 창이 열려 있으면 게임 시간은 멈춘다.
 * 가장 위의 창만 키를 받는다. Esc 는 취소다.
 */
public abstract class Modal extends Table {
    protected final Skin skin;
    protected final Nav nav;
    ModalStack owner;
    public Runnable onClosed;

    protected Modal(Skin skin) {
        this.skin = skin;
        this.nav = new Nav(skin);
        setFillParent(true);
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
    }

    /** 창이 열릴 때 내용을 만든다. */
    public abstract void build();

    /** 어둡게 깐 배경 위 가운데 판. */
    protected Table panel() {
        setBackground(skin.getDrawable("dim"));
        Table p = new Table(skin);
        p.setBackground(skin.getDrawable("panel"));
        p.pad(22, 28, 22, 28);
        add(p).center();
        return p;
    }

    protected Label label(String text) {
        return new Label(text, skin);
    }

    protected Label label(String text, String style, Color color) {
        return Ui.label(skin, text, style, color);
    }

    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            onCancel();
            return true;
        }
        return nav.keyDown(keycode);
    }

    protected void onCancel() {
        close();
    }

    public void close() {
        if (owner != null) owner.remove(this);
        if (onClosed != null) onClosed.run();
    }

    /** 내용을 다시 만든다 (포커스 위치는 되도록 유지). */
    public void rebuild() {
        int f = nav.focusIndex();
        clearChildren();
        nav.clear();
        build();
        nav.setFocusIndex(f);
    }
}
