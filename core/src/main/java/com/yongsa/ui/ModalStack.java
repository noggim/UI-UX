package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;

import java.util.ArrayList;
import java.util.List;

/** 열린 창들. 마지막에 연 창이 맨 위다. */
public class ModalStack {
    private final Stage stage;
    private final List<Modal> open = new ArrayList<>();

    public ModalStack(Stage stage) {
        this.stage = stage;
    }

    public void push(Modal m) {
        m.owner = this;
        m.clearChildren();
        m.nav.clear();
        m.build();
        open.add(m);
        stage.addActor(m);
    }

    void remove(Modal m) {
        open.remove(m);
        m.remove();
        m.owner = null;
    }

    public boolean isEmpty() {
        return open.isEmpty();
    }

    public Modal top() {
        return open.isEmpty() ? null : open.get(open.size() - 1);
    }

    public boolean keyDown(int keycode) {
        Modal t = top();
        return t != null && t.keyDown(keycode);
    }

    public void closeAll() {
        for (Modal m : new ArrayList<>(open)) remove(m);
    }
}
