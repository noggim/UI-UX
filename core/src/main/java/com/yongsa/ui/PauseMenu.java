package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.yongsa.screen.GameScreen;

/** Esc 메뉴 (UI-09). 던전 안에서는 “던전 나가기”가 붙는다. */
public class PauseMenu extends Modal {
    private final GameScreen screen;

    public PauseMenu(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label("일시정지", "large", Ui.GOLD)).padBottom(16).row();
        float w = 300, h = 50;
        p.add(nav.add("계속하기", this::close)).width(w).height(h).padBottom(8).row();
        p.add(nav.add("가방", () -> {
            close();
            screen.openBag();
        })).width(w).height(h).padBottom(8).row();
        p.add(nav.add("저장", () -> {
            screen.save(true);
            close();
        })).width(w).height(h).padBottom(8).row();
        p.add(nav.add("설정", () -> screen.modals.push(new SettingsWindow(screen.game)))).width(w).height(h).padBottom(8).row();
        p.add(nav.add("조작 키", () -> screen.modals.push(new ControlsWindow(screen.game, screen::applyKeyNames,
                screen.session.rules.skillKeyNames(screen.session.state))))).width(w).height(h).padBottom(8).row();
        if (screen.session.inDungeon()) {
            com.badlogic.gdx.scenes.scene2d.ui.TextButton b = nav.add("던전 나가기", () -> {
                if (screen.session.exitDungeon()) {
                    close();
                    screen.hud.banner("용사의 캠프");
                }
            });
            b.setDisabled(!screen.session.canExitDungeon());
            p.add(b).width(w).height(h).padBottom(4).row();
            if (!screen.session.canExitDungeon()) {
                p.add(label("문이 잠긴 전투 중에는 나갈 수 없다", "small", Ui.DIM)).padBottom(8).row();
            } else {
                p.add().padBottom(4).row();
            }
        }
        p.add(nav.add("타이틀로", () -> screen.modals.push(ConfirmDialog.simple(skin, "타이틀로",
                "저장하고 타이틀 화면으로 갑니다.", "타이틀로", () -> {
                    screen.save(false);
                    screen.toTitle();
                })))).width(w).height(h).row();
    }
}
