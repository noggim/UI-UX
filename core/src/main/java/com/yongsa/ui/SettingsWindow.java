package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.yongsa.YongsaGame;

/** 설정 (UI-10): 소리 크기, 창 모드, 연출 속도. 바로 적용되고 저장된다. 타이틀에서 열면 조작 키도 여기서 본다. */
public class SettingsWindow extends Modal {
    private final YongsaGame game;
    /** 조작 키 창을 여는 일. null 이면 줄을 감춘다 (게임 중에는 Esc 메뉴에 따로 있다). */
    private final Runnable openControls;

    public SettingsWindow(YongsaGame game) {
        this(game, null);
    }

    public SettingsWindow(YongsaGame game, Runnable openControls) {
        super(game.skin);
        this.game = game;
        this.openControls = openControls;
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label("설정", "large", Ui.GOLD)).colspan(3).padBottom(18).row();

        p.add(label("소리 크기", "default", Ui.DIM)).left().padRight(24);
        Table vol = new Table();
        vol.add(nav.add("−", () -> setVolume(game.settings.volume - 0.1f))).width(56).height(46);
        vol.add(label(Math.round(game.settings.volume * 100) + "%")).width(90).center();
        vol.add(nav.add("+", () -> setVolume(game.settings.volume + 0.1f))).width(56).height(46);
        p.add(vol).left().padBottom(10).row();

        p.add(label("화면", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(game.settings.fullscreen ? "전체 화면" : "창 모드", () -> {
            game.settings.fullscreen = !game.settings.fullscreen;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        p.add(label("연출 속도", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(game.settings.effectSpeed > 1 ? "빠르게" : "보통", () -> {
            game.settings.effectSpeed = game.settings.effectSpeed > 1 ? 1f : 2f;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        p.add(label("화면 흔들림", "default", Ui.DIM)).left().padRight(24);
        // 버튼 글자는 지금 상태 (“켜기”는 누르면 켠다는 뜻으로 읽혀서 “켜짐/꺼짐”으로)
        p.add(nav.add(game.settings.screenShake ? "켜짐" : "꺼짐", () -> {
            game.settings.screenShake = !game.settings.screenShake;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        p.add(label("화면 번쩍임", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(game.settings.screenFlash ? "켜짐" : "꺼짐", () -> {
            game.settings.screenFlash = !game.settings.screenFlash;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        if (openControls != null) {
            p.add(label("조작 키", "default", Ui.DIM)).left().padRight(24);
            p.add(nav.add("보기 · 바꾸기", openControls)).width(220).height(46).left().padBottom(10).row();
        }

        p.add(nav.add("닫기", this::close)).colspan(2).width(200).height(50).padTop(14);
    }

    /** 소리 크기 줄(−, + 버튼)에 포커스가 있으면 왼쪽/오른쪽 키로 바로 줄이고 키운다. */
    @Override
    public boolean keyDown(int keycode) {
        boolean onVolume = nav.focusIndex() <= 1;
        if (onVolume && (keycode == com.badlogic.gdx.Input.Keys.LEFT || keycode == com.badlogic.gdx.Input.Keys.RIGHT)) {
            int f = nav.focusIndex();
            setVolume(game.settings.volume + (keycode == com.badlogic.gdx.Input.Keys.RIGHT ? 0.1f : -0.1f));
            nav.setFocusIndex(f);
            return true;
        }
        return super.keyDown(keycode);
    }

    private void setVolume(float v) {
        game.settings.volume = Math.max(0, Math.min(1, Math.round(v * 10) / 10f));
        game.saveSettings();
        game.sfx.play("select");
        rebuild();
    }
}
