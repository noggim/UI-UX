package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.yongsa.enemy.Monster;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Vein;
import com.yongsa.model.Weapon;
import com.yongsa.map.Door;
import com.yongsa.map.Corridor;
import com.yongsa.map.Link;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;

import java.util.ArrayList;
import java.util.List;

/**
 * 플레이 화면 HUD (UI-03): 왼쪽 위 체력과 장착 무기, 오른쪽 위 광물 3종,
 * 아래 스킬 3개, 왼쪽 아래 “다음 할 일”, 보스방에서는 위 가운데 보스 체력.
 * 지도는 M 키로 열고 닫는다 (FR-맵-10).
 */
public class Hud {
    private final GameSession g;
    /** 칸 위에 적는 키 이름 (사용자가 조작 키를 바꾸면 GameScreen 이 바꿔 준다). */
    public String attackKey = "J", rollKey = "K", mapKey = "M", bagKey = "B", moveKeys = "W A S D";
    private final Art art;
    private final Fonts fonts;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final GlyphLayout layout = new GlyphLayout();
    private final List<Toast> toasts = new ArrayList<>();
    private String banner;
    private float bannerT;
    private float shownHp = -1, trailHp = -1, trailHold;
    private float bossShown = -1, bossTrail = -1;
    private Monster lastBoss;
    private float hurtT;
    private float time;
    private boolean mapOpen;
    private float mapAnim;
    private float introT = -1;
    /** 공격 J, 구르기 K, 스킬 칸 넷 (L, U, I, O). 스킬 칸은 열렸을 때만 보인다. */
    private static final int SLOTS = 2 + com.yongsa.model.Rules.SKILL_SLOTS;
    private final float[] readyFlash = new float[SLOTS];
    private final boolean[] wasReady = new boolean[SLOTS];
    private Matrix4 projection;
    private float w, h;

    private static final int MAX_TOASTS = 3;

    private static class Toast {
        String text;
        float t;
    }

    public Hud(GameSession g, Art art, Fonts fonts, SpriteBatch batch, ShapeRenderer shapes) {
        java.util.Arrays.fill(wasReady, true);
        this.g = g;
        this.art = art;
        this.fonts = fonts;
        this.batch = batch;
        this.shapes = shapes;
    }

    public void toast(String text) {
        for (Toast t : toasts) if (t.text.equals(text)) {
            t.t = 0;
            return;
        }
        Toast t = new Toast();
        t.text = text;
        toasts.add(t);
        // 연달아 뜬 안내가 바로 지워지지 않게 셋까지 쌓는다 (예: 침대에서 잔 뒤 두 줄)
        if (toasts.size() > MAX_TOASTS) toasts.remove(0);
    }

    public void banner(String text) {
        banner = text;
        bannerT = 0;
    }

    public void toggleMap() {
        mapOpen = !mapOpen;
    }

    public void closeMap() {
        mapOpen = false;
    }

    public boolean mapOpen() {
        return mapOpen;
    }

    /** 용사가 맞았을 때 화면 가장자리가 붉게 번쩍인다. */
    public void hurt() {
        hurtT = 0.35f;
    }

    /** 보스가 나타날 때 체력 패널을 강조한다. */
    public void bossIntro(String name, boolean finalBoss) {
        introT = 0;
        banner = null;
    }

    public void update(float dt) {
        time += dt;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            toasts.get(i).t += dt;
            if (toasts.get(i).t > 3.2f) toasts.remove(i);
        }
        if (banner != null) {
            bannerT += dt;
            if (bannerT > 2.6f) banner = null;
        }
        if (introT >= 0) {
            introT += dt;
            if (introT > 2.6f) introT = -1;
        }
        hurtT -= dt;
        mapAnim += ((mapOpen ? 1f : 0f) - mapAnim) * Math.min(1f, dt * 14f);

        int maxHp = g.rules.maxHp(g.state);
        int hp = Math.max(0, g.state.hp);
        if (shownHp < 0) shownHp = trailHp = hp;
        shownHp += (hp - shownHp) * Math.min(1f, dt * 14f);
        // 깎인 만큼 하얀 막대가 잠깐 남았다가 따라 줄어든다
        if (hp < trailHp) {
            trailHold -= dt;
            if (trailHold <= 0) trailHp += (hp - trailHp) * Math.min(1f, dt * 5f);
        } else {
            trailHp = hp;
            trailHold = 0.45f;
        }
        if (hp >= trailHp - 0.5f) trailHold = 0.45f;
        shownHp = Math.max(0, Math.min(maxHp, shownHp));

        Monster boss = g.activeBoss();
        if (boss != lastBoss) {
            lastBoss = boss;
            bossShown = 0;
            bossTrail = boss == null ? 0 : boss.hp;
        }
        if (boss != null) {
            bossShown += (boss.hp - bossShown) * Math.min(1f, dt * 4f);
            bossTrail += (boss.hp - bossTrail) * Math.min(1f, dt * 2f);
            if (bossTrail < boss.hp) bossTrail = boss.hp;
        }

        float[] left = cooldowns();
        for (int i = 0; i < SLOTS; i++) {
            boolean ready = left[i] <= 0;
            if (ready && !wasReady[i] && i > 1) readyFlash[i] = 0.4f; // 공격·구르기는 빛내지 않는다 (2026-11-22 지용)
            wasReady[i] = ready;
            readyFlash[i] -= dt;
        }
    }

    private float[] cooldowns() {
        float[] out = new float[SLOTS];
        out[0] = Math.max(0, g.hero.attackCd);
        out[1] = Math.max(0, g.hero.rollCd);
        for (int i = 2; i < SLOTS; i++) out[i] = Math.max(0, g.hero.skillCd[i - 2]);
        return out;
    }

    /** 개발용 (F1 이동 영역 보기): 용사 발밑을 덮는 마스크 파일을 화면 아래에 적는다. */
    public boolean showWalkMasks;
    /** 설정 “화면 번쩍임”: 끄면 피격·저체력 붉은 테두리를 그리지 않는다. */
    public boolean flashEnabled = true;

    public void render(Matrix4 projection, float w, float h) {
        this.projection = projection;
        this.w = w;
        this.h = h;
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
        GameState s = g.state;
        int maxHp = g.rules.maxHp(s);
        Weapon weapon = s.equipped();
        Monster boss = g.activeBoss();
        batch.begin();
        batch.setColor(0, 0, 0, 0.28f);
        batch.draw(art.vignette, 0, 0, w, h);
        float low = s.hp < maxHp * 0.3f ? 0.22f + 0.12f * MathUtils.sin(time * 6f) : 0f;
        float red = Math.max(low, Math.max(0, hurtT) / 0.35f * 0.65f);
        if (red > 0 && flashEnabled) {
            batch.setColor(0.8f, 0.05f, 0.05f, red);
            batch.draw(art.vignette, 0, 0, w, h);
        }
        // ───── 왼쪽 위 프로필 (시안: 원형 초상화 + 은테 판 + 칼 칸)
        float pcx = 59, pcy = h - 56; // 초상화 원 가운데
        float ph0 = frames.panelHeight(HudFrames.Panel.PROFILE), py0 = h - 9 - ph0;
        batch.setColor(Color.WHITE);
        frames.panel(batch, HudFrames.Panel.PROFILE, pcx, py0, 24 + PROFILE_W - pcx);
        batch.draw(frames.portraitDisc, pcx - 36, pcy - 36, 72, 72);
        // 시안처럼 원 안에 상반신: 용사 그림 위쪽 절반을 크게, 원 밖으로 나가는 부분(망토)은 잘라 낸 그림
        com.badlogic.gdx.graphics.g2d.TextureRegion hero = art.get("hero_down");
        if (portraitBust == null || portraitSrc != hero) {
            portraitSrc = hero;
            portraitBust = circleBust(hero);
        }
        batch.draw(portraitBust, pcx - BUST_W / 2f - BUST_SHIFT, pcy - 37, BUST_W, BUST_W * portraitBust.getRegionHeight() / portraitBust.getRegionWidth());
        batch.draw(frames.portraitRing, pcx - 43, pcy - 43, 86, 86);
        float tx = 111; // 글자·체력바 시작
        float right = 24 + PROFILE_W - 26;
        text(fonts.normal, "용사", tx, h - 26, Ui.GOLD);
        String hp = Math.max(0, s.hp) + " / " + maxHp;
        layout.setText(fonts.normal, hp);
        text(fonts.normal, hp, right - layout.width, h - 26, Ui.TEXT);
        float barW = right - tx, hpY = h - 58; // 이름 글자 아래 8px 띄움
        rect(tx - 1, hpY - 1, barW + 2, 9, HP_EDGE);
        rect(tx, hpY, barW, 7, HP_BG);
        rect(tx, hpY, barW * MathUtils.clamp(trailHp / maxHp, 0, 1), 7, Ui.GOLD);
        rect(tx, hpY, barW * MathUtils.clamp(shownHp / maxHp, 0, 1), 7, HP_FILL);
        rect(tx, hpY + 4, barW * MathUtils.clamp(shownHp / maxHp, 0, 1), 2, HP_SHINE);
        if (weapon != null) {
            boolean awakened = weapon.awakened;
            float ss = 42, sx = tx - 2, sy = h - 103; // 칼 칸
            if (awakened) {
                batch.setColor(0.85f, 0.6f, 1f, 0.45f + 0.2f * MathUtils.sin(time * 3f));
                batch.draw(art.glow, sx - 10, sy - 10, ss + 20, ss + 20);
            }
            batch.setColor(awakened ? AWAKE_TINT : Color.WHITE);
            batch.draw(frames.itemSlot, sx, sy, ss + 1, ss);
            batch.setColor(Color.WHITE);
            batch.draw(art.sword(weapon.defId, awakened), sx + 5, sy + 5, ss - 10, ss - 10);
            float wx = sx + ss + 9;
            text(fonts.small, g.rules.weaponName(weapon), wx, sy + 40, awakened ? Ui.PURPLE : Ui.TEXT);
            // “보스의 힘 3/3” 대신 무엇을 모으는지(흡수한 보스 힘), 다 모으면 무엇이 되는지
            String atk = "공격력 " + g.rules.attack(s);
            layout.setText(fonts.small, atk);
            float atkEnd = wx + layout.width;
            text(fonts.small, atk, wx, sy + 17, STAT);
            if (s.unsealed) {
                layout.setText(fonts.small, "성검 해방");
                text(fonts.small, "성검 해방", right - layout.width, sy + 17, Ui.PURPLE);
            } else {
                // 흡수한 보스 힘: 마름모 셋 (채운 것 = 쓰러뜨린 보스). 다 모이면 금색 글자
                int got = s.bossesDefeated.size();
                float pip = 22, pipsW = 3 * pip - 10;
                batch.setColor(Color.WHITE);
                for (int k = 0; k < 3; k++)
                    batch.draw(k < got ? frames.diamond : frames.diamondOutline, right - pipsW + k * (pip - 5), sy, pip, pip);
                layout.setText(fonts.small, "보스 힘");
                // 이름은 늘 적는다. 공격력 글자와 8px 안쪽으로 붙으면 짧게 “힘”
                String powLabel = right - pipsW - 4 - layout.width >= atkEnd + 8 ? "보스 힘" : "힘";
                layout.setText(fonts.small, powLabel);
                text(fonts.small, powLabel, right - pipsW - 4 - layout.width, sy + 17, got >= 3 ? Ui.GOLD : Ui.DIM);
            }
        }

        // ───── 오른쪽 위 광물 + 키 안내 알약
        float oreH = frames.panelHeight(HudFrames.Panel.ORE);
        batch.setColor(Color.WHITE);
        frames.panel(batch, HudFrames.Panel.ORE, w - 342, h - 15 - oreH, 327);
        // 광물 셋은 판 안쪽을 같은 폭 칸으로 나누고, 칸마다 [그림+숫자] 묶음을 가운데에 둔다.
        // 숫자가 길어 칸을 넘으면 그 숫자만 글자를 줄인다 (자릿수가 늘어도 간격이 같게)
        OreType[] ores = OreType.values();
        float cellX = w - 342 + 22, cellW = (327 - 44) / (float) ores.length, gem = 36, gap = 4;
        for (int k = 0; k < ores.length; k++) {
            String n = String.valueOf(s.ore(ores[k]));
            layout.setText(fonts.normal, n);
            float room = cellW - gem - gap - 6, scale = Math.min(1f, room / layout.width);
            float tw = layout.width * scale, gx = cellX + k * cellW + (cellW - gem - gap - tw) / 2f;
            batch.setColor(Color.WHITE);
            batch.draw(art.gem(ores[k]), gx, h - 63, gem, gem);
            float oldX = fonts.normal.getData().scaleX, oldY = fonts.normal.getData().scaleY;
            fonts.normal.getData().setScale(oldX * scale, oldY * scale);
            // 줄인 글자도 세로 가운데가 같게
            text(fonts.normal, n, gx + gem + gap, h - 36 - (1 - scale) * fonts.normal.getCapHeight() / scale / 2f, Ui.TEXT);
            fonts.normal.getData().setScale(oldX, oldY);
        }
        float kx = w - 29;
        kx = keyHintLeft(kx, h - 100, "Esc", "메뉴");
        kx = keyHintLeft(kx - 7, h - 100, mapKey, "지도");
        keyHintLeft(kx - 7, h - 100, bagKey, "가방");
        if (showWalkMasks) {
            float my = 130;
            for (String m : g.map.walkMasksAt(g.hero.x, g.hero.y)) {
                text(fonts.small, m, 24, my, Ui.GOLD);
                my += 24;
            }
        }

        // ───── 가운데 위: 구역 이름 또는 보스 체력
        Room room = g.heroRoom();
        boolean village = room != null && room.type == RoomType.VILLAGE;
        String zone = room == null ? corridorZone() : village ? g.balance.village.name
                : room.dungeonId == null ? "용사의 캠프" : g.balance.dungeon(room.dungeonId).name;
        if (boss == null) {
            String title = banner == null ? zone : banner;
            layout.setText(fonts.normal, title);
            float zw = layout.width;
            shadowed(fonts.normal, title, w / 2 - zw / 2, h - 30, Ui.GOLD);
            batch.setColor(Color.WHITE);
            batch.draw(frames.diamond, w / 2 - zw / 2 - 26, h - 50, 16, 16);
            batch.draw(frames.diamond, w / 2 + zw / 2 + 10, h - 50, 16, 16);
            String sub = village ? (g.villagersFree() ? "모두가 돌아왔다" : "던전 밖 · 모두 수정 속에 잠들어 있다")
                    : g.inCamp() ? "정비하고, 다음 여정을 준비하세요"
                    : room != null && room.type == RoomType.TUNNEL ? "어두운 동굴 통로" : room == null ? "통로" : "탐험 중";
            layout.setText(fonts.small, sub);
            shadowed(fonts.small, sub, w / 2 - layout.width / 2, h - 60, Ui.DIM);
        } else {
            float bh = frames.panelHeight(HudFrames.Panel.BOSS);
            float bw = BOSS_W, bx0 = w / 2 - bw / 2, by0 = h - 19 - bh;
            batch.setColor(boss.finalBoss ? AWAKE_TINT : Color.WHITE);
            frames.panel(batch, HudFrames.Panel.BOSS, bx0, by0, bw);
            if (introT >= 0) rect(bx0 + 40, by0 + 12, (bw - 80) * Math.min(1, introT / 0.5f), 2, Ui.GOLD);
            centered(fonts.large, boss.def.name, w / 2, h - 34, boss.finalBoss ? Ui.PURPLE : Ui.GOLD);
            float barX = bx0 + 26, barLen = bw - 52, barY = h - 74;
            rect(barX - 1, barY - 1, barLen + 2, 12, HP_EDGE);
            rect(barX, barY, barLen, 10, BOSS_BG);
            rect(barX, barY, barLen * MathUtils.clamp(bossTrail / boss.maxHp, 0, 1), 10, Ui.GOLD);
            rect(barX, barY, barLen * MathUtils.clamp(bossShown / boss.maxHp, 0, 1), 10, boss.finalBoss ? Ui.PURPLE : BOSS_FILL);
            rect(barX, barY + 7, barLen * MathUtils.clamp(bossShown / boss.maxHp, 0, 1), 2, HP_SHINE);
            if (boss.shield > 0 && boss.shieldMax > 0) {
                // 방패: 체력바 위 밝은 막대. 다 깎기 전에는 체력이 닳지 않는다
                rect(barX, barY + 13, barLen, 4, SHIELD_BG);
                rect(barX, barY + 13, barLen * MathUtils.clamp((float) boss.shield / boss.shieldMax, 0, 1), 4, SHIELD_FILL);
            }
            centered(fonts.small, boss.shield > 0 ? "방패 " + boss.shield + "  ·  " + boss.hp + " / " + boss.maxHp
                    : boss.hp + " / " + boss.maxHp, w / 2, h - 75, Ui.TEXT);
        }

        // ───── 왼쪽 아래: 다음 여정 (폭 고정, 긴 글은 줄바꿈. 스킬 칸 6개와도 안 겹친다)
        String task = g.nextTask();
        float taskW = TASK_W;
        batch.setColor(Color.WHITE);
        frames.panel(batch, HudFrames.Panel.QUEST, 18, 15, taskW);
        batch.setColor(0.42f, 0.25f, 0.63f, 1f);
        batch.draw(frames.headerFade, 44, 76, taskW * 0.72f, 24);
        batch.setColor(Color.WHITE);
        batch.draw(frames.keyRound, 23, 61, 50, 50);
        batch.draw(frames.badgeBook, 26, 64, 44, 44);
        text(fonts.small, "다음 여정", 82, 94, Ui.GOLD);
        layout.setText(fonts.normal, task, Ui.TEXT, taskW - 60, com.badlogic.gdx.utils.Align.left, true);
        fonts.normal.setColor(Ui.TEXT);
        // 한 줄이면 판 아래쪽 가운데, 두 줄이면 위로 올려 판 안에 담는다
        float taskY = 60 + Math.max(0, layout.height - fonts.normal.getLineHeight()) / 2f;
        fonts.normal.draw(batch, task, 40, taskY, taskW - 60, com.badlogic.gdx.utils.Align.left, true);
        hintBand(18, 140, moveKeys, "이동", g.interactKey, "상호작용");

        // ───── 오른쪽 아래: 공격·구르기·스킬 칸 (시안 칸 그림 104×103, 왼쪽 위 원에 키)
        float[] left = cooldowns();
        com.yongsa.model.Rules.SkillSlot[] skills = g.rules.skillSlots(g.state);
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        shown.add(0);
        shown.add(1);
        // 지금 쓸 수 있는 스킬(그 검을 들었거나 흡수해 익힌 것)만 칸을 보인다 (2026-11-10 피드백, 조작 키 창과 같은 규칙)
        for (int i = 0; i < skills.length; i++) if (skills[i] != null && skills[i].usable) shown.add(2 + i);
        final float SLOT = 104, SLOT_H = 103, SY = 16;
        for (int k = 0; k < shown.size(); k++) {
            int i = shown.get(k);
            float x = w - 24 - SLOT - (shown.size() - 1 - k) * 108;
            com.yongsa.model.Rules.SkillSlot sk = i >= 2 ? skills[i - 2] : null;
            boolean locked = sk != null && !sk.usable;
            // 공격·구르기·잠긴 스킬 = 금 칸, 스킬 = 붉은 칸, 각성 스킬 = 금 칸을 보라로 물들임
            boolean redSlot = sk != null && !locked && !sk.awakened;
            boolean purple = sk != null && !locked && sk.awakened;
            batch.setColor(purple ? AWAKE_TINT : Color.WHITE);
            batch.draw(redSlot ? frames.slotRed : frames.slotGold, x, SY, SLOT, SLOT_H);
            if (readyFlash[i] > 0) {
                batch.setColor(1f, 0.85f, 0.5f, readyFlash[i] / 0.4f * 0.8f);
                batch.draw(art.glow, x - 10, SY - 10, SLOT + 20, SLOT_H + 20);
            }
            float total = i == 0 ? g.balance.attack.basicCooldown : i == 1 ? g.balance.attack.rollTime + g.balance.attack.rollCooldown
                    : sk.def.cooldown;
            if (left[i] > 0) rect(x + 9, SY + 9, SLOT - 18, (SLOT_H - 18) * Math.min(1, left[i] / total), COOL_SHADE);
            // 아이콘: 칸 가운데 54px, 위 오른쪽에 상태 글자(익힘·검 필요)가 있으면 46px 로 줄여 겹치지 않게
            boolean hasStatus = sk != null && !sk.usable; // “익힘” 글자는 쓰지 않는다 (2026-11-22 지용)
            float is = hasStatus ? 42 : 54, ix = x + 54 - is / 2f, iy = SY + (hasStatus ? 49 : 32);
            batch.setColor(Color.WHITE);
            if (i == 0 && weapon != null) batch.draw(art.sword(weapon.defId, weapon.awakened), ix, iy, is, is);
            if (i == 1) batch.draw(art.get("hero_side_walk"), ix + 10, iy + 2, 34, 48);
            if (sk != null) {
                // 스킬 아이콘(sprites/skill/icon_<kind>.png, 없으면 그 검 그림) 뒤로 빛, 지금 못 쓰면 어둡게
                batch.setColor(sk.usable ? Color.WHITE : SKILL_LOCKED);
                com.badlogic.gdx.graphics.Texture icon = skillIcon(sk.def.kind);
                if (icon != null) batch.draw(icon, ix, iy, is, is);
                else batch.draw(art.sword(sk.weaponId, sk.awakened), ix, iy, is, is);
                batch.setColor(Color.WHITE);
            }
            String key = i == 0 ? attackKey : i == 1 ? rollKey : g.skillKeys[i - 2];
            String name = i == 0 ? "베기" : i == 1 ? "구르기" : sk.def.name;
            // 키: 칸 그림의 왼쪽 위 원 안 (원 가운데 = 칸 왼쪽 24, 위에서 24). 긴 키 이름은 작은 글자
            layout.setText(fonts.small, key);
            float kcx = x + 24, kcy = SY + SLOT_H - 24;
            if (layout.width > 26) {
                batch.setColor(Color.WHITE);
                frames.pill(batch, true, kcx - 14, kcy - HudFrames.PILL_H / 2f, Math.max(HudFrames.PILL_WIDE_W + 11, layout.width + 24));
                text(fonts.small, key, kcx - 14 + 11, kcy + 7, KEY_TEXT);
            } else {
                centered(fonts.small, key, kcx, kcy + 8, KEY_TEXT);
            }
            String status = locked ? "검 필요" : null;
            if (status != null) {
                layout.setText(fonts.small, status);
                // 칸 위쪽 모서리 장식·키 배지와 겹치지 않게 아이콘과 이름 사이에
                centered(fonts.small, status, x + SLOT / 2f, SY + 47, sk.learned ? Ui.PURPLE : Ui.DIM);
            }
            centered(fonts.small, name, x + SLOT / 2f, SY + (status != null ? 29 : 30), locked ? Ui.DIM : Ui.TEXT);
            if (i >= 1 && left[i] > 0.1f) centered(fonts.normal, String.format(java.util.Locale.ROOT, "%.1f", left[i]), x + 54, SY + 66, Ui.TEXT);
        }

        // ───── 안내 (토스트): 시안 판 그림. 스킬 안내 = 보라 판 + 스킬 배지, 경고 = 느낌표 배지, 무기 = 칼
        // 함께 떠 있는 안내는 가장 긴 것의 폭으로 맞춰 아이콘이 한 줄로 선다
        float toastW = 380;
        for (Toast t : toasts) {
            layout.setText(fonts.normal, t.text);
            toastW = Math.max(toastW, Math.min(820, layout.width + 120));
        }
        for (int ti = 0; ti < toasts.size(); ti++) {
            Toast toast = toasts.get(ti);
            float alpha = Math.min(1, (3.2f - toast.t) / 0.5f);
            float cy = 188 + (toasts.size() - 1 - ti) * 52; // 판 가운데 높이
            float tw = toastW;
            float tx0 = (w - tw) / 2;
            // 안내 종류는 글로 고른다: 스킬·봉인·숙련 = 보라, 잠김·실패·위험 = 느낌표, 나머지 = 금테
            boolean skillNote = toast.text.contains("스킬") || toast.text.contains("봉인") || toast.text.contains("숙련");
            boolean warn = !skillNote && (toast.text.contains("잠겼") || toast.text.contains("못") || toast.text.contains("위험"));
            HudFrames.Panel kind = skillNote ? HudFrames.Panel.TOAST_SKILL : HudFrames.Panel.TOAST;
            float th = frames.panelHeight(kind);
            // 보라 판은 시안처럼 조금 좁게
            float pw = skillNote ? tw - 40 : tw, px = (w - pw) / 2;
            batch.setColor(1, 1, 1, alpha);
            frames.panel(batch, kind, px, cy - th / 2f, pw);
            boolean weaponNote = !warn && !skillNote && weapon != null && (toast.text.contains("무기") || toast.text.contains("검"));
            if (skillNote) batch.draw(frames.badgeSkill, px + 14, cy - 22, 44, 44);
            else if (warn) batch.draw(frames.badgeWarn, tx0 + 31, cy - 18, 36, 36);
            else {
                // 시안처럼 아이콘은 늘 원 배지 안에
                batch.draw(frames.keyRound, tx0 + 30, cy - 19, 38, 38);
                if (weaponNote) batch.draw(art.sword(weapon.defId, weapon.awakened), tx0 + 35, cy - 14, 28, 28);
                else batch.draw(frames.diamond, tx0 + 37, cy - 12, 24, 24);
            }
            float textL = tx0 + 74;
            centered(fonts.normal, toast.text, textL + (tx0 + tw - 30 - textL) / 2f, cy + 9, new Color(1, 1, 1, alpha));
        }
        batch.setColor(Color.WHITE);
        batch.end();
        if (mapAnim > 0.01f) drawMap();
        fonts.normal.setColor(Color.WHITE);
        fonts.small.setColor(Color.WHITE);
    }

    private void rect(float x, float y, float width, float height, Color color) {
        batch.setColor(color);
        batch.draw(art.white(), x, y, width, height);
    }

    // 매 프레임 new Color 를 만들지 않도록 HUD 색은 상수로 둔다
    private static final Color CARD_BG = new Color(0x101820ed), CARD_TOP = new Color(0x806c4bca), CARD_BOTTOM = new Color(0x39454fca);
    private static final Color COOL_SHADE = new Color(0, 0, 0, 0.5f), SKILL_LOCKED = new Color(0.35f, 0.35f, 0.4f, 1f);
    /** 다음 여정 판 폭 (시안은 396px 이지만 긴 안내가 두 줄로 넘치지 않게 넓힘. 스킬 칸 6개의 왼쪽 끝 606 보다 안쪽). */
    private static final float TASK_W = 540;
    /** 왼쪽 위 프로필 패널 오른쪽 끝 = 24 + PROFILE_W (보스 체력 판 왼쪽 끝 414 보다 안쪽). */
    private static final float PROFILE_W = 376;
    private static final Color HP_BG = new Color(0x40232aff), HP_FILL = new Color(0xe5524fff), HP_EDGE = new Color(0x0a0810ff),
            HP_SHINE = new Color(1f, 1f, 1f, 0.22f), BOSS_BG = new Color(0x39232aff), BOSS_FILL = new Color(0xf0504aff),
            SHIELD_BG = new Color(0x2a2418ff), SHIELD_FILL = new Color(1f, 0.82f, 0.55f, 1f);
    /** 각성·마지막 보스: 시안 금테 그림을 보라로 물들이는 색. */
    private static final Color STAT = new Color(0xe0d8c8ff), KEY_TEXT = new Color(0xf0e6c8ff);
    private static final Color AWAKE_TINT = new Color(0.82f, 0.66f, 1f, 1f);
    private static final Color BAND = new Color(0.04f, 0.03f, 0.08f, 0.12f), BAND_LINE = new Color(0.85f, 0.7f, 0.42f, 0.1f);
    /** 시안 보스 체력 판 폭 452. 프로필 판 오른쪽 끝(24+PROFILE_W) 안쪽으로 들어오지 않는다. */
    private static final float BOSS_W = 452;
    private final HudFrames frames = new HudFrames();
    private com.badlogic.gdx.graphics.g2d.TextureRegion portraitBust, portraitSrc;
    private com.badlogic.gdx.graphics.Texture portraitTex;
    /** 초상화 상반신 그리는 폭, 왼쪽으로 미는 양(망토가 오른쪽으로 뻗어 얼굴이 원 가운데 오게), 보이는 원 반지름. */
    private static final float BUST_W = 88, BUST_SHIFT = 6, BUST_R = 36;

    /**
     * 용사 그림 위쪽 절반을 잘라, 화면에서 초상화 원(반지름 BUST_R) 밖에 놓일 픽셀을 투명하게 만든 그림.
     * 그림 파일을 다시 읽어 한 번만 만든다. 읽을 수 없으면 자르지 않은 위쪽 절반을 쓴다.
     */
    private com.badlogic.gdx.graphics.g2d.TextureRegion circleBust(com.badlogic.gdx.graphics.g2d.TextureRegion hero) {
        int rw = hero.getRegionWidth(), rh = Math.round(hero.getRegionHeight() * 0.5f);
        com.badlogic.gdx.graphics.g2d.TextureRegion plain = new com.badlogic.gdx.graphics.g2d.TextureRegion(hero, 0, 0, rw, rh);
        try {
            com.badlogic.gdx.graphics.TextureData td = hero.getTexture().getTextureData();
            if (!td.isPrepared()) td.prepare();
            com.badlogic.gdx.graphics.Pixmap src = td.consumePixmap();
            com.badlogic.gdx.graphics.Pixmap out = new com.badlogic.gdx.graphics.Pixmap(rw, rh, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            out.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
            float k = BUST_W / rw; // 그림 1px 이 화면에서 차지하는 크기
            float bustH = rh * k;
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    // 화면에서 원 가운데 기준 위치: 그림 왼쪽 끝 = -BUST_W/2 - BUST_SHIFT, 그림 아래 끝 = -37
                    float sx = -BUST_W / 2f - BUST_SHIFT + (x + 0.5f) * k, sy = -37 + bustH - (y + 0.5f) * k;
                    float edge = BUST_R - (float) Math.sqrt(sx * sx + sy * sy);
                    if (edge <= 0) continue;
                    int c = src.getPixel(hero.getRegionX() + x, hero.getRegionY() + y);
                    int a = Math.round((c & 0xff) * Math.min(1f, edge));
                    out.drawPixel(x, y, (c & 0xffffff00) | a);
                }
            }
            if (td.disposePixmap()) src.dispose();
            if (portraitTex != null) portraitTex.dispose();
            portraitTex = new com.badlogic.gdx.graphics.Texture(out);
            portraitTex.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            out.dispose();
            return new com.badlogic.gdx.graphics.g2d.TextureRegion(portraitTex);
        } catch (RuntimeException e) {
            return plain;
        }
    }

    /** 시안 원형 키캡에 키 이름을 담아 (x, 가운데 y=cy) 에 그리고 오른쪽 끝 x 를 돌려준다. 긴 이름은 알약 키캡. */
    private float keyRoundAt(float x, float cy, String key) {
        layout.setText(fonts.small, key);
        batch.setColor(Color.WHITE);
        if (layout.width <= 22) {
            batch.draw(frames.keyRound, x, cy - 18, 36, 36);
            text(fonts.small, key, x + 18 - layout.width / 2f, cy + 8, KEY_TEXT);
            return x + 36;
        }
        float kw = layout.width + 26;
        frames.pill(batch, true, x, cy - HudFrames.PILL_H / 2f, Math.max(kw + 11, HudFrames.PILL_WIDE_W + 11));
        text(fonts.small, key, x + (kw - layout.width) / 2f, cy + 8, KEY_TEXT);
        return x + kw;
    }

    /** 왼쪽 아래 조작 안내: 옅은 띠 위에 “[W] A S D 이동   [E] 상호작용” (시안처럼 이동 묶음은 첫 키만 원). */
    private void hintBand(float x, float cy, String moveKeys, String moveLabel, String interactKey, String interactLabel) {
        // 이동 키는 글자만, 상호작용 키는 원 키캡
        String[] mk = moveKeys.split(" ");
        float keys = 0;
        layout.setText(fonts.small, String.join(" ", mk));
        keys = layout.width + 8;
        layout.setText(fonts.small, moveLabel);
        float t1 = layout.width;
        float k2 = keyWidth(interactKey);
        layout.setText(fonts.small, interactLabel);
        float t2 = layout.width;
        float total = 4 + keys + 10 + t1 + 22 + k2 + 10 + t2 + 16;
        rect(x, cy - 17, total, 34, BAND);
        rect(x + 18, cy + 16, total - 36, 1, BAND_LINE);
        rect(x + 18, cy - 17, total - 36, 1, BAND_LINE);
        float cx = x + 4;
        shadowed(fonts.small, String.join(" ", mk), cx + 6, cy + 8, KEY_TEXT); // 이동 키는 원 키캡 없이 글자만 (2026-11-22 지용)
        cx += keys + 10;
        shadowed(fonts.small, moveLabel, cx, cy + 8, Ui.TEXT);
        cx = keyRoundAt(cx + t1 + 22, cy, interactKey) + 10;
        shadowed(fonts.small, interactLabel, cx, cy + 8, Ui.TEXT);
    }

    private float keyWidth(String key) {
        layout.setText(fonts.small, key);
        return layout.width <= 22 ? 36 : layout.width + 26;
    }

    /** 오른쪽 끝(rightX)에 맞춰 시안 키 알약 “(키) 이름” 을 그리고 그 왼쪽 끝 x 를 돌려준다. */
    private float keyHintLeft(float rightX, float cy, String key, String name) {
        layout.setText(fonts.small, key);
        float kw = layout.width;
        boolean wide = kw > 22;
        float keyCell = wide ? Math.max(HudFrames.PILL_WIDE_W, kw + 24) : HudFrames.PILL_KEY_W;
        layout.setText(fonts.small, name);
        float pw = keyCell + 6 + layout.width + 15;
        float x = rightX - pw;
        batch.setColor(Color.WHITE);
        frames.pill(batch, wide, x, cy - HudFrames.PILL_H / 2f, pw);
        text(fonts.small, key, x + (wide ? keyCell : HudFrames.PILL_KEY_W + 2) / 2f - kw / 2f, cy + 8, KEY_TEXT);
        text(fonts.small, name, x + keyCell + 6, cy + 8, Ui.TEXT);
        return x;
    }

    public void dispose() {
        frames.dispose();
        if (portraitTex != null) portraitTex.dispose();
    }

    private void card(float x, float y, float width, float height) {
        rect(x, y, width, height, CARD_BG);
        rect(x, y + height - 1, width, 1, CARD_TOP);
        rect(x, y, width, 1, CARD_BOTTOM);
    }

    private void text(BitmapFont font, String value, float x, float y, Color color) {
        font.setColor(color);
        batch.setColor(Color.WHITE);
        font.draw(batch, value, x, y);
    }

    private void centered(BitmapFont font, String value, float x, float y, Color color) {
        layout.setText(font, value);
        text(font, value, x - layout.width / 2, y, color);
    }


    /** 전체 화면 검은 막 (화면 전환용). */
    public void renderFade(float alpha) {
        if (alpha <= 0.001f) return;
        batch.setProjectionMatrix(projection);
        batch.begin();
        batch.setColor(0, 0, 0, alpha);
        batch.draw(art.white(), 0, 0, w, h);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void shadowed(BitmapFont font, String text, float x, float y, Color c) {
        font.setColor(0, 0, 0, 0.8f);
        font.draw(batch, text, x + 2, y - 2);
        font.setColor(c);
        font.draw(batch, text, x, y);
    }


    // ───────────────────────── 지도 (M)

    private void drawMap() {
        float a = mapAnim;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        boolean inVillage = g.inVillage();
        for (Room r : g.map.rooms) {
            if (hiddenOnMap(r, inVillage)) continue;
            minX = Math.min(minX, r.x0);
            maxX = Math.max(maxX, r.x1);
            minY = Math.min(minY, r.y0);
            maxY = Math.max(maxY, r.y1);
        }
        for (Corridor c : g.map.corridors) {
            if (hiddenOnMap(c.link.a, inVillage) || hiddenOnMap(c.link.b, inVillage)) continue;
            for (int[] t : c.tiles) {
                minX = Math.min(minX, t[0]);
                maxX = Math.max(maxX, t[0]);
                minY = Math.min(minY, t[1]);
                maxY = Math.max(maxY, t[1]);
            }
        }
        float pw = 900, ph = 560;
        float px = (w - pw) / 2f, py = (h - ph) / 2f - 10 + (1f - a) * 20f;
        float k = Math.min((pw - 80) / (maxX - minX + 1), (ph - 130) / (maxY - minY + 1));
        float ox = px + (pw - (maxX - minX + 1) * k) / 2f - minX * k;
        float oy = py + 60 + (ph - 130 - (maxY - minY + 1) * k) / 2f - minY * k;

        Room here = g.heroRoom();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.55f * a);
        shapes.rect(0, 0, w, h);
        shapes.setColor(0.08f, 0.07f, 0.1f, 0.94f * a);
        shapes.rect(px, py, pw, ph);
        shapes.setColor(0.55f, 0.46f, 0.27f, a);
        shapes.rect(px, py, pw, 2);
        shapes.rect(px, py + ph - 2, pw, 2);
        shapes.rect(px, py, 2, ph);
        shapes.rect(px + pw - 2, py, 2, ph);

        // 통로: 지나가 본 길만 보인다 (2026-10-14 해성: 가 본 곳만 지도에). 봉인된 길은 보랏빛.
        for (Corridor c : g.map.corridors) {
            Link l = c.link;
            if (hiddenOnMap(l.a, inVillage) || hiddenOnMap(l.b, inVillage)) continue;
            if (!g.state.seenCorridors.contains(c.key())) continue;
            if (l.gateClosed) shapes.setColor(0.65f, 0.45f, 1f, a);
            else shapes.setColor(0.42f, 0.4f, 0.46f, a);
            for (int[] t : c.tiles) shapes.rect(ox + t[0] * k, oy + t[1] * k, k + 0.6f, k + 0.6f);
        }
        // 방
        for (Room r : g.map.rooms) {
            boolean visited = known(r);
            if (hiddenOnMap(r, inVillage)) continue; // 심연은 길로 이어지지 않는다
            if (!visited) continue; // 가 본 방만 보인다
            Color c = roomColor(r, visited);
            float rx = ox + r.x0 * k, ry = oy + r.y0 * k, rw = (r.x1 - r.x0 + 1) * k, rh = (r.y1 - r.y0 + 1) * k;
            if (r.scene == null) {
                if (r == here) {
                    float pulse = 0.5f + 0.5f * MathUtils.sin(time * 5f);
                    shapes.setColor(1f, 0.85f, 0.35f, (0.6f + 0.4f * pulse) * a);
                    shapes.rect(rx - 4, ry - 4, rw + 8, rh + 8);
                }
                shapes.setColor(0.03f, 0.03f, 0.05f, a);
                shapes.rect(rx - 1, ry - 1, rw + 2, rh + 2);
                shapes.setColor(c.r, c.g, c.b, c.a * a);
                shapes.rect(rx, ry, rw, rh);
                shapes.setColor(1f, 1f, 1f, 0.07f * a);
                shapes.rect(rx, ry + rh - 4, rw, 4);
            } else {
                // 그림 방은 실제 바닥 모양대로 칸을 칠한다
                float pad = r == here ? 3f : 1.2f;
                if (r == here) {
                    float pulse = 0.5f + 0.5f * MathUtils.sin(time * 5f);
                    shapes.setColor(1f, 0.85f, 0.35f, (0.6f + 0.4f * pulse) * a);
                } else {
                    shapes.setColor(0.03f, 0.03f, 0.05f, a);
                }
                for (int ty = r.y0; ty <= r.y1; ty++)
                    for (int tx = r.x0; tx <= r.x1; tx++)
                        if (r.floor(tx, ty)) shapes.rect(ox + tx * k - pad, oy + ty * k - pad, k + pad * 2, k + pad * 2);
                shapes.setColor(c.r, c.g, c.b, c.a * a);
                for (int ty = r.y0; ty <= r.y1; ty++)
                    for (int tx = r.x0; tx <= r.x1; tx++)
                        if (r.floor(tx, ty)) shapes.rect(ox + tx * k, oy + ty * k, k + 0.6f, k + 0.6f);
            }
            if (r.type == RoomType.ORE && visited) {
                List<Vein> veins = g.veinsOf(r);
                if (veins != null) {
                    for (Vein v : veins) {
                        shapes.setColor(oreColor(v.ore).r, oreColor(v.ore).g, oreColor(v.ore).b, a);
                        shapes.rect(ox + v.tileX * k - 1, oy + v.tileY * k - 1, Math.max(3, k) + 2, Math.max(3, k) + 2);
                    }
                }
            }
            float mcx = ox + (r.centerTileX() + 0.5f) * k, mcy = oy + (r.centerTileY() + 0.5f) * k;
            if (r.type == RoomType.BOSS && !g.state.bossDefeated(r.dungeonId)) {
                // 아직 살아 있는 보스: 굵은 X
                shapes.setColor(1f, 0.85f, 0.8f, a);
                shapes.rectLine(mcx - 9, mcy - 9, mcx + 9, mcy + 9, 4);
                shapes.rectLine(mcx - 9, mcy + 9, mcx + 9, mcy - 9, 4);
            } else if (g.hasChest(r)) {
                // 열지 않은 보물상자
                shapes.setColor(0.2f, 0.12f, 0.04f, a);
                shapes.rect(mcx - 10, mcy - 8, 20, 16);
                shapes.setColor(1f, 0.8f, 0.3f, a);
                shapes.rect(mcx - 8, mcy - 6, 16, 12);
                shapes.setColor(0.45f, 0.28f, 0.1f, a);
                shapes.rect(mcx - 8, mcy, 16, 2);
            }
            if (g.hasPortal(r)) {
                float cx = ox + g.portalX(r) / g.balance.tileSize * k, cy = oy + g.portalY(r) / g.balance.tileSize * k;
                shapes.setColor(0.55f, 0.45f, 1f, a);
                shapes.rect(cx - 8, cy - 7, 16, 18);
                shapes.setColor(0.85f, 0.8f, 1f, a);
                shapes.rect(cx - 4, cy - 7, 8, 13);
            }
        }
        // 용사 위치
        float hx = ox + g.hero.x / g.balance.tileSize * k, hy = oy + g.hero.y / g.balance.tileSize * k;
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 8f);
        shapes.setColor(1f, 1f, 1f, 0.35f * pulse * a);
        shapes.circle(hx, hy, 10 + 4 * pulse, 20);
        shapes.setColor(0.1f, 0.1f, 0.1f, a);
        shapes.circle(hx, hy, 6, 16);
        shapes.setColor(1f, 0.95f, 0.7f, a);
        shapes.circle(hx, hy, 4.5f, 16);

        // 범례
        String[] labels = {"캠프", "방", "광물방", "보스방", "보물상자", "봉인된 길"};
        Color[] colors = {new Color(0.85f, 0.6f, 0.3f, 1f), new Color(0.5f, 0.48f, 0.56f, 1f), new Color(0.35f, 0.5f, 0.75f, 1f),
                new Color(0.8f, 0.25f, 0.2f, 1f), new Color(1f, 0.8f, 0.3f, 1f), new Color(0.65f, 0.45f, 1f, 1f)};
        float lx = px + 40;
        for (int i = 0; i < labels.length; i++) {
            shapes.setColor(colors[i].r, colors[i].g, colors[i].b, a);
            shapes.rect(lx, py + 26, 16, 16);
            layout.setText(fonts.small, labels[i]);
            lx += 16 + 10 + layout.width + 28;
        }
        shapes.end();

        batch.begin();
        fonts.large.setColor(1f, 0.85f, 0.4f, a);
        fonts.large.draw(batch, "지도", px + 30, py + ph - 20);
        fonts.small.setColor(Ui.DIM.r, Ui.DIM.g, Ui.DIM.b, a);
        layout.setText(fonts.small, mapKey + "  닫기");
        fonts.small.draw(batch, mapKey + "  닫기", px + pw - 30 - layout.width, py + ph - 26);
        // 던전 이름
        String[] ids = {"LEFT", "RIGHT", "TOP"};
        for (String id : ids) {
            float sx = 0, sy = 0;
            int n = 0;
            for (Room r : g.map.rooms) {
                if (!id.equals(r.dungeonId) || r.type == RoomType.FINAL || hiddenOnMap(r, inVillage)) continue;
                sx += ox + (r.x0 + r.x1) / 2f * k;
                sy += oy + r.y1 * k;
                n++;
            }
            if (n == 0) continue;
            String name = g.balance.dungeon(id).name;
            layout.setText(fonts.small, name);
            fonts.small.setColor(0.9f, 0.85f, 0.75f, 0.9f * a);
            fonts.small.draw(batch, name, sx / n - layout.width / 2f, sy / n + 24);
        }
        lx = px + 40;
        for (String label : labels) {
            fonts.small.setColor(Ui.TEXT.r, Ui.TEXT.g, Ui.TEXT.b, a);
            fonts.small.draw(batch, label, lx + 26, py + 40);
            layout.setText(fonts.small, label);
            lx += 16 + 10 + layout.width + 28;
        }
        batch.end();
        fonts.large.setColor(Color.WHITE);
        fonts.small.setColor(Color.WHITE);
    }

    /** 통로에 있을 때 위쪽 이름: 이어진 던전, 캠프와 마을 사이면 마을 가는 길. */
    private String corridorZone() {
        Corridor c = g.map.corridorAtWorld(g.hero.x, g.hero.y);
        if (c == null) return "던전 입구";
        if (c.link.a.type == RoomType.VILLAGE || c.link.b.type == RoomType.VILLAGE) return g.balance.village.name + " 가는 길";
        if ("CAMP".equals(c.theme)) return "용사의 캠프";
        return g.balance.dungeon(c.theme).name;
    }

    /** 심연은 땅 밑이라 그 안에 있을 때만 지도에 나온다. */
    private boolean hiddenOnMap(Room r, boolean inVillage) {
        // Cave interiors occupy a separate space; show a local map while inside.
        Room here = g.heroRoom();
        if (here != null && here.type == RoomType.TUNNEL) return r != here;
        if (r.type == RoomType.TUNNEL) return true;
        return r.type == RoomType.FINAL && r != g.heroRoom();
    }

    private boolean known(Room r) {
        return r.type == RoomType.CAMP || g.state.room(r.id).visited;
    }

    private static final Color UNSEEN = new Color(0.2f, 0.2f, 0.25f, 0.9f);

    private Color roomColor(Room r, boolean visited) {
        if (!visited && r.type != RoomType.BOSS && r.type != RoomType.FINAL) return UNSEEN;
        switch (r.type) {
            case CAMP: return new Color(0.85f, 0.6f, 0.3f, 1f);
            case ORE: return new Color(0.35f, 0.5f, 0.75f, 1f);
            case BOSS:
            case FINAL:
                if (!visited) return new Color(0.45f, 0.14f, 0.12f, 0.75f);
                return r.type == RoomType.FINAL ? new Color(0.6f, 0.25f, 0.8f, 1f) : new Color(0.8f, 0.25f, 0.2f, 1f);
            default:
                return g.state.room(r.id).cleared ? new Color(0.5f, 0.48f, 0.56f, 1f) : new Color(0.4f, 0.36f, 0.42f, 1f);
        }
    }

    private static Color oreColor(OreType t) {
        switch (t) {
            case BLACKIRON: return new Color(0.75f, 0.82f, 1f, 1f);
            case BLOODMOON: return new Color(1f, 0.4f, 0.5f, 1f);
            default: return new Color(0.55f, 0.9f, 1f, 1f);
        }
    }

    /** 스킬 아이콘 그림 (GPT 요청, docs/ART_REQUESTS.md 0-5). 없으면 null 이고 검 그림을 쓴다. 게임 내내 쓰므로 한 번만 읽는다. */
    private static final java.util.Map<String, com.badlogic.gdx.graphics.Texture> SKILL_ICONS = new java.util.HashMap<>();

    private static com.badlogic.gdx.graphics.Texture skillIcon(String kind) {
        if (kind == null) return null;
        if (SKILL_ICONS.containsKey(kind)) return SKILL_ICONS.get(kind);
        com.badlogic.gdx.files.FileHandle f = com.badlogic.gdx.Gdx.files.internal("sprites/skill/icon_" + kind + ".png");
        com.badlogic.gdx.graphics.Texture t = null;
        if (f.exists()) {
            t = new com.badlogic.gdx.graphics.Texture(f, true); // 큰 그림을 칸 크기로 줄여 그리므로 밉맵
            t.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        }
        SKILL_ICONS.put(kind, t);
        return t;
    }
}
