package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.yongsa.gfx.Art;
import com.yongsa.model.EnhanceService.Outcome;

/** Large, in-engine smithing scene, using the same world art as gameplay. */
public final class ForgeScene extends Actor {
    private final Art art;
    private final ForgeMotion motion;
    private final String weaponId;
    private final boolean weaponAwakened;
    private float time;

    public ForgeScene(Art art, ForgeMotion motion, String weaponId, boolean awakened) {
        this.art = art;
        this.motion = motion;
        this.weaponId = weaponId;
        this.weaponAwakened = awakened;
        setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    @Override public void act(float delta) { super.act(delta); time += delta; }

    @Override public void draw(Batch batch, float parentAlpha) {
        float old = batch.getPackedColor();
        float scale = getWidth() / 576f;
        float x = getX(), y = getY();
        batch.setColor(1, 1, 1, parentAlpha);
        batch.draw(art.world("forge_stage"), x, y, getWidth(), getHeight());
        float baseX = x, baseY = y;
        batch.draw(art.get("shadow"), x + 222 * scale, y + 131 * scale, 84 * scale, 18 * scale);

        boolean destroyed = motion.outcome() == Outcome.DESTROY;
        float lift = motion.outcome() == Outcome.SUCCESS ? 10 * scale : 0;
        if (!destroyed) {
            TextureRegion sword = art.worldSword(weaponId, weaponAwakened || motion.awakened());
            batch.draw(sword, baseX + 274 * scale, baseY + (134 * scale) + lift,
                    56 * scale, 56 * scale, 112 * scale, 112 * scale, 1, 1, -45);
        }
        float recoil = destroyed && motion.phase() == ForgeMotion.Phase.RESULT
                ? -MathUtils.sin(motion.progress() * MathUtils.PI) * 12 * scale : 0;
        float slump = motion.outcome() == Outcome.DOWN ? -5 * scale : 0;
        int pose = motion.pose();
        if (motion.outcome() == Outcome.KEEP && motion.phase() == ForgeMotion.Phase.IDLE) pose = 0;
        TextureRegion hero = art.world("smith_" + pose);
        batch.draw(hero, baseX + 132 * scale + recoil,
                baseY + 140 * scale + slump, art.width(hero) * 8 * scale, art.height(hero) * 8 * scale);

        float beat = (motion.progress() * 3) % 1;
        if (motion.phase() == ForgeMotion.Phase.HAMMER && beat >= 0.7f)
            sparks(batch, baseX + 334 * scale, baseY + 184 * scale, (beat - 0.7f) / 0.3f, scale, Ui.ORANGE, parentAlpha);
        if (motion.phase() == ForgeMotion.Phase.RESULT) {
            Color color = motion.outcome() == Outcome.SUCCESS ? (motion.awakened() ? Ui.GOLD : Ui.BLUE)
                    : destroyed ? Ui.RED : Ui.GREY;
            if (motion.outcome() != Outcome.KEEP)
                sparks(batch, baseX + 334 * scale, baseY + 190 * scale, motion.progress(), scale, color, parentAlpha);
            if (motion.awakened()) {
                // 각성: 금빛 불꽃이 두 겹 더 크게 튀고, 검 둘레에 빛 고리가 퍼진다 (대성공 연출을 각성에 옮겼다)
                sparks(batch, baseX + 334 * scale, baseY + 190 * scale, Math.min(1, motion.progress() * 1.4f), scale * 1.8f, Ui.GOLD, parentAlpha);
                ring(batch, baseX + 330 * scale, baseY + 190 * scale + lift, 20 + 70 * motion.progress(), scale, parentAlpha * (1 - motion.progress()));
            }
        }
        // A few slow embers belong to the furnace, away from the probability panel.
        batch.setColor(1, 0.55f, 0.18f, 0.5f * parentAlpha);
        for (int i = 0; i < 3; i++) {
            float t = (time * 0.6f + i / 3f) % 1;
            batch.draw(art.white(), baseX + (463 + i * 6) * scale,
                    baseY + (229 + t * 36) * scale, 2 * scale, 3 * scale);
        }
        batch.setPackedColor(old);
    }

    private void ring(Batch batch, float cx, float cy, float r, float s, float alpha) {
        batch.setColor(1f, 0.86f, 0.4f, alpha);
        for (int i = 0; i < 24; i++) {
            float a = i * 15;
            batch.draw(art.white(), cx + MathUtils.cosDeg(a) * r * s, cy + MathUtils.sinDeg(a) * r * s * 0.6f, 3 * s, 3 * s);
        }
    }

    private void sparks(Batch batch, float x, float y, float p, float s, Color c, float alpha) {
        batch.setColor(c.r, c.g, c.b, (1 - p) * alpha);
        for (int i = 0; i < 9; i++) {
            float angle = 20 + i * 19;
            float distance = (12 + i % 3 * 9) * p * s;
            batch.draw(art.white(), x + MathUtils.cosDeg(angle) * distance,
                    y + MathUtils.sinDeg(angle) * distance - p * p * 14 * s,
                    (i % 2 + 2) * s, (i % 2 + 2) * s);
        }
    }
}
