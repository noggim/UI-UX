package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.save.SaveManager;
import com.yongsa.ui.ConfirmDialog;
import com.yongsa.ui.ControlsWindow;
import com.yongsa.ui.MessageWindow;
import com.yongsa.ui.Modal;
import com.yongsa.ui.SettingsWindow;
import com.yongsa.ui.Ui;
import com.yongsa.screen.GameScreen;

/** 타이틀 (UI-01): 이어하기, 시작하기, 설정(조작 키 포함), 종료하기. 배경은 제목이 그려진 키 아트(title_keyart). */
public class TitleScreen extends MenuScreen {

    public TitleScreen(YongsaGame game) {
        super(game);
        open(new TitleMenu());
    }

    private class TitleMenu extends Modal {
        TitleMenu() {
            super(game.skin);
        }

        @Override
        protected void onCancel() {
            // 타이틀에서 Esc 는 아무것도 하지 않는다
        }

        @Override
        public void build() {
            // 제목은 배경 그림에 들어 있다. 메뉴는 그 아래 가운데에 둔다.
            nav.style = "titleMenu";
            Table col = new Table();
            col.defaults().width(260).height(42).padBottom(8);
            boolean hasSave = game.saves.hasSave();
            TextButton cont = nav.add("이어하기", TitleScreen.this::continueGame);
            cont.setDisabled(!hasSave);
            col.add(cont).row();
            col.add(nav.add("시작하기", TitleScreen.this::newGame)).row();
            col.add(nav.add("설정", () -> modals.push(new SettingsWindow(game,
                    () -> modals.push(new ControlsWindow(game, null)))))).row();
            col.add(nav.add("종료하기", () -> Gdx.app.exit())).padBottom(0).row();
            com.badlogic.gdx.scenes.scene2d.ui.Label hint = label("방향키로 이동  ·  Enter 선택", "small", HINT);
            hint.setAlignment(com.badlogic.gdx.utils.Align.center);
            col.add(hint).height(20).padTop(18).padBottom(0).row();
            // 그림의 제목 장식(마름모)이 화면 가운데보다 10px 왼쪽에 있어 메뉴도 그 축에 맞춘다
            add(col).expand().bottom().padBottom(52).padRight(20);
            nav.setFocusIndex(hasSave ? 0 : 1);
        }
    }

    private static final Color HINT = new Color(0xa89cb0cc);

    @Override
    protected void drawBackground(SpriteBatch b) {
        if (!game.art.hasHd("title_keyart")) {
            super.drawBackground(b);
            return;
        }
        float W = com.yongsa.YongsaGame.WIDTH;
        b.setColor(Color.WHITE);
        b.draw(game.art.get("title_keyart"), 0, 0, W, com.yongsa.YongsaGame.HEIGHT);
        // 메뉴 뒤 바닥만 살짝 어둡게 (위로 갈수록 옅어진다)
        for (int y = 0; y < 300; y += 3) {
            float a = 0.5f * (1f - y / 300f);
            b.setColor(0.04f, 0.03f, 0.06f, a * a * 2f);
            b.draw(game.art.white(), 0, y, W, 3);
        }
        b.setColor(Color.WHITE);
    }

    private void continueGame() {
        SaveManager.LoadResult r = game.saves.load();
        if (r.state == null) {
            modals.push(new MessageWindow(game.skin, "저장을 읽지 못했습니다", Ui.RED, new ConfirmDialog.Line[]{
                    new ConfirmDialog.Line("저장 파일이 깨졌습니다. 새 게임을 시작해 주세요.", null)}, "확인"));
            return;
        }
        start(r.state, false);
    }

    private void newGame() {
        if (game.saves.hasSave()) {
            modals.push(new ConfirmDialog(game.skin, "새 게임", new ConfirmDialog.Line[]{
                    new ConfirmDialog.Line("저장된 게임이 있습니다.", null),
                    new ConfirmDialog.Line("새 게임을 시작하면 지금 저장은 지워집니다.", Ui.RED)},
                    "새로 시작", true, () -> start(NewGame.create(game.balance), true)));
        } else {
            start(NewGame.create(game.balance), true);
        }
    }

    private void start(GameState s, boolean intro) {
        if (intro) {
            game.save(s);
            // 이야기 그림 3장 → 소개 글 → 게임
            game.setScreen(new StoryScreen(game, game.story.opening, () -> game.setScreen(new IntroScreen(game, s))));
        } else {
            game.setScreen(new GameScreen(game, s));
        }
        dispose();
    }
}
