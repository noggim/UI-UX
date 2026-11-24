package com.yongsa.ui;

import com.yongsa.screen.GameScreen;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameState;
import com.yongsa.model.Stats;
import com.yongsa.ui.Modal;
import com.yongsa.ui.Ui;

/** 엔딩 (UI-11): 마무리 글과 플레이 기록, 계속하기 (FR-던전-10). */
public class EndingScreen extends MenuScreen {
    private final GameState state;

    public EndingScreen(YongsaGame game, GameState state) {
        super(game);
        this.state = state;
        open(new End());
    }

    private class End extends Modal {
        End() {
            super(game.skin);
        }

        @Override
        protected void onCancel() {
        }

        @Override
        public void build() {
            Table p = panel();
            p.add(label("용사가 다 했다", "title", Ui.GOLD)).padBottom(14).row();
            Label story = label("깨어난 성검이 마왕을 꿰뚫었다.\n던전을 뒤덮던 어둠이 걷히고,\n캠프의 모닥불이 처음으로 밝게 타올랐다.", "default", Ui.TEXT);
            story.setAlignment(Align.center);
            p.add(story).padBottom(20).row();
            Stats s = state.stats;
            Table t = new Table();
            row(t, "플레이 시간", (int) (s.playSeconds / 60) + "분 " + (int) (s.playSeconds % 60) + "초");
            row(t, "강화 시도 / 성공", s.enhanceTries + "번 / " + s.enhanceSuccess + "번");
            row(t, "유지 / 하락", s.enhanceKeep + "번 / " + s.enhanceDown + "번");
            row(t, "파괴된 무기", s.destroyed + "자루");
            row(t, "만든 무기", s.crafted + "자루");
            row(t, "캔 광물", s.oreMined + "개");
            row(t, "쓰러뜨린 몬스터", s.monstersKilled + "마리");
            row(t, "쓰러진 횟수", s.deaths + "번");
            row(t, "흡수해 익힌 검 스킬", state.learnedSkills.size() + "개");
            p.add(t).padBottom(20).row();
            Table b = new Table();
            b.add(nav.add("계속하기", () -> {
                game.setScreen(new GameScreen(game, state));
                EndingScreen.this.dispose();
            })).width(220).height(52).padRight(14);
            b.add(nav.add("타이틀로", () -> {
                game.setScreen(new TitleScreen(game));
                EndingScreen.this.dispose();
            })).width(220).height(52);
            p.add(b);
        }

        private void row(Table t, String k, String v) {
            t.add(label(k, "default", Ui.DIM)).left().padRight(40).padBottom(4);
            t.add(label(v)).right().padBottom(4).row();
        }
    }
}
