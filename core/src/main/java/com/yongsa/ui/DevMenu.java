package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.model.AbsorbService;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.screen.GameScreen;

/**
 * 개발용 메뉴 (F1). 보스를 다시 싸우게 되살리고, 보스방으로 바로 가고, 광물 무한·무적을 켜고 끈다.
 * 켜고 끈 값은 설정 파일에 저장되어 다음 실행에도 유지된다.
 */
public class DevMenu extends Modal {
    public static final int INFINITE_ORE = 999;

    private final GameScreen screen;

    public DevMenu(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    @Override
    public void build() {
        GameSession g = screen.session;
        GameState s = g.state;
        Table p = panel();
        p.add(label("개발용 메뉴", "large", Ui.GOLD)).colspan(3).padBottom(4).row();
        p.add(label("F1 로 열고 닫는다. 실제 플레이에는 쓰지 않는다.", "small", Ui.DIM)).colspan(3).padBottom(16).row();

        p.add(label("광물 무한", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(screen.game.settings.devInfiniteOre ? "켜짐 (" + INFINITE_ORE + "개 유지)" : "꺼짐", () -> {
            screen.game.settings.devInfiniteOre = !screen.game.settings.devInfiniteOre;
            screen.game.saveSettings();
            if (screen.game.settings.devInfiniteOre) g.devFillOres(INFINITE_ORE);
            rebuild();
        })).width(260).height(46).left().padBottom(8).row();

        p.add(label("무적", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(screen.game.settings.devGodMode ? "켜짐" : "꺼짐", () -> {
            screen.game.settings.devGodMode = !screen.game.settings.devGodMode;
            screen.game.saveSettings();
            g.devGodMode = screen.game.settings.devGodMode;
            rebuild();
        })).width(260).height(46).left().padBottom(8).row();

        p.add(label("이동 영역 보기", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(screen.game.settings.devShowWalkable ? "켜짐 (초록 = 걸을 수 있음)" : "꺼짐", () -> {
            screen.game.settings.devShowWalkable = !screen.game.settings.devShowWalkable;
            screen.game.saveSettings();
            rebuild();
        })).width(260).height(46).left().padBottom(16).row();
        // 강화 숙련도 (발표·테스트용): 한 단계씩 올리거나 마스터로, 0 으로 되돌린다
        com.yongsa.model.EnhanceService es = g.enhance;
        p.add(label("강화 숙련도", "default", Ui.DIM)).left().padRight(24);
        Table mastery = new Table();
        mastery.add(label("Lv " + s.masteryLevel + (es.masteryMaxed(s) ? " (마스터)" : ""), "default", Ui.GOLD)).width(110).left();
        TextButton up = nav.add("+1", () -> {
            es.addMasteryExp(s, es.masteryNeed(s.masteryLevel) - s.masteryExp);
            screen.save(false);
            rebuild();
        });
        up.setDisabled(es.masteryMaxed(s));
        mastery.add(up).width(70).height(42).padRight(6);
        mastery.add(nav.add("마스터", () -> {
            s.masteryLevel = es.masteryMaxLevel();
            s.masteryExp = 0;
            screen.save(false);
            rebuild();
        })).width(90).height(42).padRight(6);
        mastery.add(nav.add("0", () -> {
            s.masteryLevel = 0;
            s.masteryExp = 0;
            screen.save(false);
            rebuild();
        })).width(60).height(42);
        p.add(mastery).colspan(2).left().padBottom(16).row();

        p.add(label("보스 부활 · 바로 가기", "default", Ui.GOLD)).colspan(3).left().padBottom(4).row();
        p.add(label("흡수한 힘과 열린 길은 그대로. 보스방 입구로 가서 걸어 들어가면 싸운다.", "small", Ui.DIM)).colspan(3).left().padBottom(10).row();
        for (String id : AbsorbService.BOSS_ORDER) {
            String name = g.balance.dungeon(id).boss.name;
            p.add(label(name, "default", Ui.TEXT)).left().padRight(24);
            p.add(reviveButton(id, s.bossDefeated(id), g.devRevived(id))).width(260).height(42).left().padBottom(6);
            p.add(warpButton(id, name)).width(150).height(42).left().padLeft(8).padBottom(6).row();
        }
        p.add(label(g.balance.finalBoss.name, "default", Ui.TEXT)).left().padRight(24);
        p.add(reviveButton("FINAL", s.finalBossDefeated, false)).width(260).height(42).left().padBottom(6);
        p.add(warpButton("FINAL", g.balance.finalBoss.name)).width(150).height(42).left().padLeft(8).padBottom(6).row();

        TextButton all = nav.add("모든 보스 부활", () -> {
            int n = 0;
            for (String id : AbsorbService.BOSS_ORDER) if (g.devReviveBoss(id)) n++;
            if (g.devReviveBoss("FINAL")) n++;
            screen.hud.toast(n > 0 ? "보스 " + n + "마리를 되살렸다" : "되살릴 보스가 없다");
            rebuild();
        });
        all.setDisabled(g.fighting());
        p.add(all).colspan(3).width(300).height(46).padTop(8).row();
        if (g.fighting()) p.add(label("싸우는 중에는 되살리거나 옮길 수 없다", "small", Ui.DIM)).colspan(3).padTop(4).row();

        p.add(nav.add("닫기", this::close)).colspan(3).width(200).height(50).padTop(14);
    }

    private TextButton warpButton(String id, String name) {
        TextButton b = nav.add("바로 가기", () -> {
            if (screen.devWarpToBoss(id)) {
                close();
                screen.hud.toast(name + "의 방 입구로 간다");
            }
        });
        b.setDisabled(screen.session.fighting());
        return b;
    }

    private TextButton reviveButton(String id, boolean defeated, boolean waiting) {
        GameSession g = screen.session;
        String text = waiting ? "보스방에서 기다리는 중" : defeated ? "되살리기" : "아직 살아 있음";
        TextButton b = nav.add(text, () -> {
            if (g.devReviveBoss(id)) {
                String name = "FINAL".equals(id) ? g.balance.finalBoss.name : g.balance.dungeon(id).boss.name;
                screen.hud.toast(name + "이(가) 되살아났다");
            }
            rebuild();
        });
        b.setDisabled(!defeated || waiting || g.fighting());
        return b;
    }
}
