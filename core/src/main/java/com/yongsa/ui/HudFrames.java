package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD 장식 그림 (2026-11-22 지용 시안).
 * 판·고리·배지·스킬 칸은 시안 그림에서 잘라 낸 assets/ui/hud/*.png 를 쓴다.
 * 판은 양 끝 장식을 그대로 두고 가운데만 늘리는 가로 3조각(9-패치)이라 폭이 바뀌어도 모양이 같다.
 * 그림 크기는 1280×720 화면 기준 실제 크기라 높이는 늘리지 않고 그린다.
 */
public class HudFrames implements Disposable {
    /** 시안에서 잘라 낸 판 종류. */
    public enum Panel { PROFILE, BOSS, ORE, QUEST, TOAST, TOAST_SKILL }

    private final List<Texture> textures = new ArrayList<>();
    private final NinePatch[] panels = new NinePatch[Panel.values().length];
    private final NinePatch pillKey, pillWide;
    public final TextureRegion portraitRing, portraitDisc, keyRound, badgeBook, badgeSkill, badgeWarn,
            slotGold, slotRed, itemSlot;
    public final TextureRegion diamond, diamondOutline, headerFade;
    /** 알약 왼쪽 키캡 칸 폭 (한 글자 키 / Esc 같은 긴 키). */
    public static final float PILL_KEY_W = 34, PILL_WIDE_W = 43, PILL_H = 33;

    private static final int GOLD = 0xd8b46aff, GOLD_HI = 0xf6dc9aff;

    public HudFrames() {
        panels[Panel.PROFILE.ordinal()] = slice("profile", 19, 19);
        panels[Panel.BOSS.ordinal()] = slice("boss", 52, 52);
        panels[Panel.ORE.ordinal()] = slice("ore", 24, 24);
        panels[Panel.QUEST.ordinal()] = slice("quest", 45, 45);
        panels[Panel.TOAST.ordinal()] = slice("toast_gold", 24, 24);
        panels[Panel.TOAST_SKILL.ordinal()] = slice("toast_purple", 26, 26);
        pillKey = slice("pill_key", 34, 11);
        pillWide = slice("pill_key_wide", 43, 11);
        portraitRing = region("portrait_ring");
        portraitDisc = region("portrait_disc");
        keyRound = region("key_round");
        badgeBook = region("badge_book");
        badgeSkill = region("badge_skill");
        badgeWarn = region("badge_warn");
        slotGold = region("slot_gold");
        slotRed = region("slot_red");
        itemSlot = region("item_slot");
        diamond = diamond();
        diamondOutline = diamondOutline();
        headerFade = fade(0x6a3fa0ff);
    }

    /** 판을 (x, y) 에 폭 w 로 그린다. 높이는 그림 높이 그대로. */
    public void panel(Batch b, Panel kind, float x, float y, float w) {
        NinePatch n = panels[kind.ordinal()];
        n.draw(b, x, y, Math.max(w, n.getTotalWidth()), n.getTotalHeight());
    }

    public float panelHeight(Panel kind) {
        return panels[kind.ordinal()].getTotalHeight();
    }

    /** 키 알약: 왼쪽 키캡 칸 + 이름 + 끝. wide 는 Esc 처럼 긴 키 이름용. */
    public void pill(Batch b, boolean wide, float x, float y, float w) {
        NinePatch n = wide ? pillWide : pillKey;
        n.draw(b, x, y, Math.max(w, n.getTotalWidth()), PILL_H);
    }

    // ───────────────────────── 그림 읽기·만들기

    private TextureRegion region(String name) {
        Texture t = new Texture(Gdx.files.internal("ui/hud/" + name + ".png"));
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textures.add(t);
        return new TextureRegion(t);
    }

    private NinePatch slice(String name, int left, int right) {
        return new NinePatch(region(name), left, right, 0, 0);
    }

    private Texture keep(Pixmap p) {
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        textures.add(t);
        return t;
    }

    private static int rgba(int c, float alphaScale) {
        int a = Math.round((c & 0xff) * Math.max(0, Math.min(1, alphaScale)));
        return (c & 0xffffff00) | a;
    }

    /** 마름모 (일반 안내·장식). */
    private TextureRegion diamond() {
        int S = 32;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float d = Math.abs(x + 0.5f - 16) + Math.abs(y + 0.5f - 16);
                if (d < 9) p.drawPixel(x, y, d < 6 ? GOLD_HI : GOLD);
            }
        return new TextureRegion(keep(p));
    }

    /** 속이 빈 마름모 (아직 못 모은 보스 힘). */
    private TextureRegion diamondOutline() {
        int S = 32;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float d = Math.abs(x + 0.5f - 16) + Math.abs(y + 0.5f - 16);
                if (d < 9 && d >= 6.5f) p.drawPixel(x, y, GOLD);
                else if (d < 6.5f) p.drawPixel(x, y, 0x0a0810a0);
            }
        return new TextureRegion(keep(p));
    }

    /** 왼쪽은 진하고 오른쪽으로 사라지는 띠 (다음 여정 머리줄). */
    private TextureRegion fade(int color) {
        Pixmap p = new Pixmap(64, 1, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int x = 0; x < 64; x++) p.drawPixel(x, 0, rgba(color, 0.75f * (1f - x / 63f)));
        return new TextureRegion(keep(p));
    }

    @Override
    public void dispose() {
        for (Texture t : textures) t.dispose();
        textures.clear();
    }
}
