package com.yongsa.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.screen.GameScreen;

/**
 * 성검 봉인 해제 장면 (FR-던전-12). 세 보스의 힘을 흡수해 강해진 용사가 캠프에서 봉인을 푼다.
 * 힘은 성검이 아니라 용사에게 쌓여 있고, 성검은 강해진 용사가 쥘 때 깨어난다.
 */
public class UnsealScene extends Modal {
    private static final String[] LINES = {
            "세 보스에게서 흡수한 힘이 몸 안에서 하나로 모인다.",
            "가보로 물려받은 성검이 손안에서 떨리기 시작했다.",
            "이제 용사는 봉인을 풀 수 있을 만큼 강해졌다.",
            "쇠사슬이 끊어지고, 성검이 깨어났다! 발밑의 문양이 갈라진다…",
    };
    private final GameScreen screen;
    private int step;
    private float t;
    private Label text;
    private Image sword;
    private boolean done;

    public UnsealScene(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label("성검의 봉인", "large", Ui.GOLD)).padBottom(18).row();
        sword = new Image(screen.game.art.get("sword_holy"));
        p.add(sword).size(160).padBottom(18).row();
        text = label(LINES[0], "default", Ui.TEXT);
        text.setWrap(true);
        text.setAlignment(Align.center);
        p.add(text).width(620).height(70).row();
        p.add(nav.add("계속", this::advance)).width(200).height(50).padTop(10);
    }

    private void advance() {
        if (done) {
            close();
            screen.startFall(); // 봉인이 풀리자 땅이 갈라져 심연으로 떨어진다
            return;
        }
        step++;
        if (step >= LINES.length - 1) {
            step = LINES.length - 1;
            screen.session.unseal();
            screen.game.sfx.play("unseal");
            sword.setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                    screen.game.art.get("sword_holy_awakened")));
            text.setColor(Ui.GOLD);
            screen.hud.toast("깨어난 성검을 장착했다. 문양이 갈라진다!");
            screen.save(false);
            done = true;
        } else {
            screen.game.sfx.play("select");
        }
        text.setText(LINES[step]);
    }

    @Override
    protected void onCancel() {
        // 봉인 해제는 건너뛸 수 없다: Esc 도 다음으로 넘긴다
        advance();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        t += delta;
        if (sword != null) {
            float shake = done ? 0 : (float) Math.sin(t * 30) * (1 + step) * 0.8f;
            sword.setOrigin(Align.center);
            sword.setRotation(shake);
            sword.setColor(1, 1, 1, done ? 1 : 0.85f + 0.15f * (float) Math.sin(t * 5));
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
    }
}
