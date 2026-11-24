package com.yongsa.ui;

import com.yongsa.screen.GameScreen;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameState;
import com.yongsa.ui.Modal;
import com.yongsa.ui.Ui;

/** 시작 소개 (UI-02): 이야기 그림(StoryScreen) 다음에 나오는 설정 글 3장, 건너뛸 수 있다 (FR-공통-01). */
public class IntroScreen extends MenuScreen {
    private static final String[] PAGES = {
            "우물 아래 던전 한가운데, 버려진 캠프에 용사가 홀로 남았다.\n동료도, 상인도, 대장장이도 없다.\n무기를 만드는 일까지 용사가 다 해야 한다.",
            "용사에게는 쓰러뜨린 상대의 힘을 흡수하는 힘이 있다.\n그리고 집안 대대로 내려온 가보, 봉인된 성검이 있다.\n지금은 무딘 쇠붙이와 다를 바 없다.",
            "왼쪽 갱도, 오른쪽 성채, 위쪽 탑.\n세 보스의 힘을 흡수해 봉인을 풀 만큼 강해지면,\n캠프 바닥 문양 아래 심연에서\n깨어난 성검으로 마왕을 쓰러뜨릴 수 있다.",
    };
    private final GameState state;
    private int page;

    public IntroScreen(YongsaGame game, GameState state) {
        super(game);
        this.state = state;
        open(new Page());
    }

    private class Page extends Modal {
        Page() {
            super(game.skin);
        }

        @Override
        public void build() {
            Table p = panel();
            Label text = label(PAGES[page], "large", Ui.TEXT);
            text.setAlignment(Align.center);
            text.setWrap(true);
            p.add(text).width(760).height(200).row();
            p.add(label((page + 1) + " / " + PAGES.length, "small", Ui.DIM)).padBottom(10).row();
            Table b = new Table();
            b.add(nav.add(page == PAGES.length - 1 ? "시작하기" : "다음", this::next)).width(220).height(52).padRight(14);
            b.add(nav.add("건너뛰기 (Esc)", this::skip)).width(220).height(52);
            p.add(b);
        }

        private void next() {
            page++;
            if (page >= PAGES.length) skip();
            else rebuild();
        }

        private void skip() {
            game.setScreen(new GameScreen(game, state));
            IntroScreen.this.dispose();
        }

        @Override
        protected void onCancel() {
            skip();
        }
    }
}
