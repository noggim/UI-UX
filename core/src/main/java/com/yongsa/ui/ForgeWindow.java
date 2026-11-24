package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.WeaponDef;
import com.yongsa.gfx.Art;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.EnhanceService.Odds;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;

/**
 * 대장간 창 (UI-04). 탭 3개: 제작, 강화, 분해.
 * 강화 탭은 용사의 작업 장면을 크게, 확률·비용을 작은 정보 카드로 보여준다.
 * 파괴 확인과 실제 판정/저장 규칙은 그대로 유지한다.
 */
public class ForgeWindow extends Modal {
    private enum Tab { CRAFT, ENHANCE, DISMANTLE }

    private final GameScreen screen;
    private final GameSession g;
    private final Art art;
    private Tab tab = Tab.ENHANCE;

    private final ForgeMotion motion = new ForgeMotion();
    private Runnable afterHammer;
    private int playedStrikes;
    private String workingDef;
    private boolean workingAwakened;
    private String resultText;
    private Color resultColor = Ui.TEXT;

    public ForgeWindow(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
        this.g = screen.session;
        this.art = screen.game.art;
        addCaptureListener(event -> {
            if (motion.busy() && event instanceof InputEvent) { event.cancel(); return true; }
            return false;
        });
        if (g.state.equipped() == null || !g.balance.weapon(g.state.equipped().defId).enhanceable) {
            boolean anyCraftable = false;
            for (WeaponDef d : g.balance.weapons) if (d.enhanceable && g.forge.craftBlockReason(g.state, d) == null) anyCraftable = true;
            if (anyCraftable || g.state.weapons.size() == 1) tab = Tab.CRAFT;
        }
    }

    private float speed() {
        return screen.game.settings.effectSpeed;
    }

    public boolean isWorking() { return motion.busy(); }

    @Override
    public boolean keyDown(int keycode) {
        if (motion.busy()) return true;
        return super.keyDown(keycode);
    }

    @Override public void close() {
        if (!motion.busy()) super.close();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        boolean wasBusy = motion.busy();
        boolean completed = motion.advance(delta);
        int strikes = motion.strikes();
        if (wasBusy && (motion.phase() == ForgeMotion.Phase.HAMMER || completed)) {
            if (strikes > playedStrikes) screen.game.sfx.play("mine");
            playedStrikes = strikes;
        }
        if (completed && afterHammer != null) {
            Runnable action = afterHammer;
            afterHammer = null;
            action.run();
        } else if (wasBusy && !motion.busy()) rebuild();
    }

    @Override
    public void build() {
        Table p = panel();
        Table head = new Table();
        head.add(label("대장간", "large", Ui.GOLD)).padRight(40);
        tabButton(head, "제작", Tab.CRAFT);
        tabButton(head, "강화", Tab.ENHANCE);
        tabButton(head, "분해 · 흡수", Tab.DISMANTLE);
        head.add(new MasteryBar(g.enhance, g.state, screen.game.fonts.small, art.white())).width(300).height(40).padLeft(20);
        p.add(head).left().padBottom(24).row();

        Table body = new Table();
        switch (tab) {
            case CRAFT: buildCraft(body); break;
            case ENHANCE: buildEnhance(body); break;
            default: buildDismantle(body);
        }
        p.add(body).width(900).height(470).top().row();
        Table foot = new Table();
        TextButton close = nav.add("닫기", this::close);
        close.setDisabled(motion.busy());
        foot.add(close).width(180).height(46);
        foot.add(label("   Esc 닫기 · 방향키/Tab 이동 · Enter 선택", "small", Ui.DIM));
        p.add(foot).padTop(8);
        // 처음 열 때는 탭이 아니라 지금 탭의 첫 번째 할 일 버튼에 초점을 둔다
        if (!opened) {
            opened = true;
            nav.setFocusIndex(nav.firstEnabledFrom(3));
        }
    }

    private boolean opened;

    private void tabButton(Table head, String text, Tab t) {
        TextButton b = nav.add(text, () -> {
            if (motion.busy()) return;
            tab = t;
            workingDef = null;
            motion.clearResult();
            resultText = null;
            clearChildren();
            nav.clear();
            build();
            nav.setFocusIndex(t.ordinal());
        });
        b.setDisabled(motion.busy());
        if (tab == t) {
            // 지금 탭은 tabOn 배경으로 (포커스의 금색 테두리와 구분)
            nav.markSelected(b);
            b.getLabel().setColor(Ui.GOLD);
        }
        head.add(b).width(120).height(46).padRight(8);
    }

    // ───────────────────────── 제작

    private void buildCraft(Table body) {
        if (workingDef != null && motion.busy()) {
            boolean hammering = motion.phase() == ForgeMotion.Phase.HAMMER;
            buildStage(body, hammering ? "검을 벼리는 중" : "제작 완료");
            Table info = new Table();
            info.add(wrapped(g.balance.weapon(workingDef).name, "large", Ui.TEXT)).width(280).row();
            info.add(Ui.artImage(art.sword(workingDef, false))).size(150).pad(20).row();
            info.add(wrapped(hammering ? "용사가 직접 무기를 만들고 있습니다." : "새 무기를 완성했습니다.",
                    "small", Ui.DIM)).width(280).row();
            body.add(info).width(304).top();
            return;
        }
        GameState s = g.state;
        body.top().left();
        body.add(label("광물에 깃든 힘을 검으로 벼리세요.", "default", Ui.TEXT)).colspan(3).left().padBottom(8).row();
        body.add(label("더 강한 무기는 제작 즉시 장착됩니다.  +10에 도달하면 각성합니다.", "small", Ui.DIM))
                .colspan(3).left().padBottom(22).row();
        for (WeaponDef d : g.balance.weapons) {
            if (!d.enhanceable) continue;
            OreType ore = OreType.valueOf(d.ore);
            Table card = new Table(skin);
            card.setBackground(skin.getDrawable("panelDark"));
            card.pad(18);
            card.add(label(dungeonOf(d.ore), "small", Ui.GOLD)).left().row();
            card.add(Ui.artImage(art.sword(d.id, false))).size(160).pad(8).row();
            card.add(label(d.name, "large", Ui.TEXT)).left().padBottom(8).row();
            card.add(label("공격력 " + d.baseAttack + "  ·  강화마다 +" + d.perLevel, "small", Ui.DIM)).left().padBottom(16).row();
            boolean enough = s.ore(ore) >= d.craftCost;
            card.add(label(ore.displayName + "  " + s.ore(ore) + " / " + d.craftCost,
                    "default", enough ? Ui.TEXT : Ui.ORANGE)).left().padBottom(14).row();
            TextButton button = nav.add("제작하기", () -> craft(d));
            button.setDisabled(g.forge.craftBlockReason(s, d) != null);
            card.add(button).growX().height(46);
            body.add(card).width(288).height(380).padRight(12).top();
        }
        body.row();
        if (resultText != null) body.add(label(resultText, "default", resultColor)).colspan(3).left().padTop(14);
    }

    private String dungeonOf(String ore) {
        for (Balance.DungeonDef d : g.balance.dungeons) {
            if (d.ore.equals(ore)) return com.yongsa.model.NextTask.dungeonWord(d.id) + " 던전";
        }
        return "";
    }

    private void craft(WeaponDef d) {
        if (g.forge.craftBlockReason(g.state, d) != null) return;
        startHammer(d.id, false, () -> {
            Weapon w = g.forge.craft(g.state, d);
            screen.game.sfx.play("craft");
            resultText = d.name + "을 만들었다!" + (w.uid == g.state.equippedUid ? " 바로 장착했다." : "");
            resultColor = Ui.BLUE;
            motion.result(EnhanceService.Outcome.SUCCESS, false, 0.9f / speed());
            screen.save(false);
            rebuild();
        });
    }

    // ───────────────────────── 강화

    private void buildStage(Table body, String heading) {
        Table scene = new Table(skin);
        scene.setBackground(skin.getDrawable("panelDark"));
        scene.add(label(heading, "small", Ui.GOLD)).left().pad(12, 18, 0, 18).row();
        Weapon equipped = g.state.equipped();
        String id = workingDef != null ? workingDef : equipped.defId;
        boolean awakened = workingDef != null ? workingAwakened : equipped.awakened;
        scene.add(new ForgeScene(art, motion, id, awakened)).width(576).height(380).row();
        String caption = resultText != null ? resultText
                : motion.busy() ? "불꽃을 다듬고, 한 번 더." : "검을 올려두었습니다. 준비되면 시작하세요.";
        Label result = wrapped(caption, resultText != null ? "default" : "small",
                resultText != null ? resultColor : Ui.DIM);
        result.setAlignment(Align.center);
        scene.add(result).width(540).height(54).pad(0, 18, 6, 18);
        body.add(scene).width(576).height(470).top().padRight(20);
    }

    private void buildEnhance(Table body) {
        GameState s = g.state;
        Weapon w = s.equipped();
        WeaponDef d = g.balance.weapon(w.defId);
        EnhanceService es = g.enhance;
        buildStage(body, motion.phase() == ForgeMotion.Phase.HAMMER ? "용사의 작업대 · 망치질 중"
                : motion.outcome() != null ? "용사의 작업대 · 작업 결과" : "용사의 작업대");

        Table right = new Table(skin);
        right.setBackground(skin.getDrawable("panelDark"));
        right.pad(14).top().left();
        Table weapon = new Table();
        weapon.add(Ui.artImage(art.sword(w.defId, w.awakened))).size(84).padRight(8);
        Table description = new Table();
        description.add(wrapped(g.rules.weaponName(w), "default", w.awakened ? Ui.GOLD : Ui.TEXT)).width(172).left().row();
        description.add(label("공격력 " + g.rules.attack(s, w), "small", Ui.DIM)).left().padTop(4);
        weapon.add(description).left();
        right.add(weapon).width(276).left().padBottom(8).row();

        if (!d.enhanceable) {
            right.add(wrapped(d.holy ? "성검은 강화할 수 없습니다. 광물로 검을 제작한 뒤 장착해 주세요."
                    : "강화할 수 없는 무기입니다.", "small", Ui.DIM)).width(276).left().padBottom(12).row();
        } else if (es.isMax(w)) {
            right.add(wrapped("각성 완료 · " + d.abilityName, "default", Ui.GOLD)).width(276).left().padBottom(8).row();
            right.add(wrapped(d.abilityDesc, "small", Ui.DIM)).width(276).left().padBottom(12).row();
        } else {
            OreType ore = es.oreOf(w);
            int cost = es.cost(w.level);
            boolean enough = s.ore(ore) >= cost;
            // 숙련도 줄이 늘어난 만큼 비용과 단계를 한 줄로 (판 높이 470 안에 맞춘다)
            right.add(label("+" + w.level + " → +" + (w.level + 1) + "  ·  " + ore.displayName + " " + cost + "개 (보유 " + s.ore(ore) + ")",
                    "small", enough ? Ui.TEXT : Ui.ORANGE)).left().padBottom(6).row();
            // 성공·유지·하락·파괴 (2026-11-07 대성공 없앰). 숙련도로 바뀐 확률은 원래 값과 함께 보여 준다
            Odds o = es.odds(s, w.level);
            Odds base = es.odds(w.level);
            Table odds = new Table();
            odds.add(label("성공 " + ProbBar.format(o.success) + "%" + reduced(base.success, o.success), "small", Ui.BLUE)).width(138).left();
            odds.add(label("유지 " + ProbBar.format(o.keep) + "%", "small", Ui.GREY)).width(138).left().row();
            if (base.down > 0) odds.add(label("하락 " + ProbBar.format(o.down) + "%" + reduced(base.down, o.down), "small",
                    o.down > 0 ? Ui.ORANGE : Ui.DIM)).width(138).left().padTop(4);
            if (base.destroy > 0) odds.add(label("파괴 " + ProbBar.format(o.destroy) + "%" + reduced(base.destroy, o.destroy), "small",
                    o.destroy > 0 ? Ui.RED : Ui.DIM)).width(138).left().padTop(4);
            odds.row();
            right.add(odds).width(276).left().padBottom(8).row();
            int after = g.rules.attack(s, w) + d.perLevel;
            right.add(label("성공 시 공격력 " + g.rules.attack(s, w) + " → " + after, "small", Ui.TEXT))
                    .left().padBottom(4).row();
            right.add(label("각성까지 " + (g.balance.enhance.maxLevel - w.level) + "단계", "small", Ui.PURPLE))
                    .left().padBottom(4).row();
            com.yongsa.config.Balance.SkillDef sk = d.skill;
            if (sk != null) {
                int lv = g.balance.enhance.skillUnlockLevel;
                right.add(label(w.level >= lv ? "스킬 「" + sk.name + "」 열림 · 흡수 가능" : "+" + lv + " 에 스킬 「" + sk.name + "」 열림",
                        "small", w.level >= lv ? Ui.GOLD : Ui.DIM)).left().padBottom(8).row();
            }
            boolean risk = es.canDestroy(s, w);
            String riskText = risk ? "파괴 시 검 소멸 · " + ore.displayName + " " + es.destroyRefund(w) + "개 반환"
                    : es.canDestroy(w) ? "숙련도 덕분에 파괴되지 않습니다."
                    : "+" + firstDestroyLevel() + "부터 파괴될 수 있습니다.";
            right.add(wrapped(riskText, "small", risk ? Ui.RED : es.canDestroy(w) ? Ui.GOLD : Ui.DIM))
                    .width(276).left().padBottom(10).row();
            TextButton enhance = nav.add(risk ? "강화하기 · 파괴 위험" : "강화하기", () -> {
                if (es.canDestroy(g.state, w)) confirmRisk(w); else doEnhance(w);
            }, risk);
            enhance.setDisabled(motion.busy() || !enough);
            right.add(enhance).width(276).height(48).padBottom(10).row();
        }

        if (s.weapons.size() > 1) {
            right.add(label("다른 무기 장착", "small", Ui.DIM)).left().padBottom(4).row();
            Table swap = new Table();
            for (Weapon other : s.weapons) {
                if (other.uid == s.equippedUid) continue;
                TextButton change = nav.add(g.rules.weaponName(other), () -> {
                    if (motion.busy()) return;
                    g.forge.equip(s, other);
                    resultText = null;
                    workingDef = null;
                    motion.clearResult();
                    rebuild();
                });
                change.setDisabled(motion.busy());
                swap.add(change).height(38).padRight(6);
            }
            com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(swap);
            scroll.setScrollingDisabled(false, true);
            right.add(scroll).width(276).height(42).left().padBottom(6).row();
        }
        if (!s.enhanceLog.isEmpty())
            right.add(wrapped("최근 · " + s.enhanceLog.get(0), "small", Ui.DIM)).width(276).left().row();
        body.add(right).width(304).height(470).top();
    }

    /** 파괴 확률이 처음 0보다 커지는 단계 (balance.json 강화표에서 계산). */
    private int firstDestroyLevel() {
        for (int lv = 0; lv < g.balance.enhance.maxLevel; lv++) if (g.enhance.odds(lv).destroy > 0) return lv;
        return g.balance.enhance.maxLevel;
    }

    private void confirmRisk(Weapon w) {
        EnhanceService es = g.enhance;
        Odds o = es.odds(g.state, w.level);
        OreType ore = es.oreOf(w);
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(g.rules.weaponName(w) + "을 강화합니다.", null),
                new ConfirmDialog.Line("성공 " + ProbBar.format(o.success)
                        + "%  ·  유지 " + ProbBar.format(o.keep)
                        + (o.down > 0 ? "%  ·  하락 " + ProbBar.format(o.down) : "") + "%", Ui.TEXT),
                new ConfirmDialog.Line("파괴 " + ProbBar.format(o.destroy) + "%", Ui.RED),
                new ConfirmDialog.Line("파괴되면 무기가 사라지고 " + ore.displayName + " " + es.destroyRefund(w) + "개만 돌려받습니다.", Ui.DIM),
        };
        screen.modals.push(new ConfirmDialog(skin, "파괴될 수 있습니다", lines, "그래도 강화", true, () -> doEnhance(w)));
    }

    private Label wrapped(String text, String style, Color color) {
        Label value = label(text, style, color);
        value.setWrap(true);
        return value;
    }

    private void doEnhance(Weapon w) {
        if (motion.busy()) return;
        if (g.enhance.blockReason(g.state, w) != null) return;
        resultText = null;
        startHammer(w.defId, w.awakened, () -> {
            EnhanceService.Result r = g.enhance.enhance(g.state, w, g.random);
            motion.result(r.outcome, r.awakened, (r.awakened ? g.balance.enhance.awakenSeconds : 1.05f) / speed());
            screen.save(false); // 강화 직후 자동 저장: 꺼도 결과를 되돌릴 수 없다 (FR-대장간-15)
            switch (r.outcome) {
                case SUCCESS:
                    resultText = "성공! +" + r.levelBefore + " → +" + r.levelAfter;
                    resultColor = Ui.BLUE;
                    screen.game.sfx.play(r.awakened ? "awaken" : "success");
                    if (r.awakened) resultText += " · " + g.balance.weapon(w.defId).awakenedName + " 각성!";
                    break;
                case KEEP:
                    resultText = "유지. 단계가 그대로다";
                    resultColor = Ui.GREY;
                    screen.game.sfx.play("keep");
                    break;
                case DOWN:
                    resultText = "하락… +" + r.levelBefore + " → +" + r.levelAfter;
                    resultColor = Ui.ORANGE;
                    screen.game.sfx.play("down");
                    break;
                default:
                    resultText = "용사가 실수했다! 무기가 파괴되었다";
                    resultColor = Ui.RED;
                    screen.game.sfx.play("destroy");
                    screen.hud.toast(r.refundOre.displayName + " " + r.refund + "개를 돌려받았다. 봉인된 성검을 들었다");
            }
            if (r.masteryGained > 0) resultText += "   (숙련 +" + r.masteryGained + ")";
            if (r.masteryLeveledUp()) {
                screen.hud.toast("강화 숙련도가 올랐다! Lv " + r.masteryAfter + masteryNote(r.masteryAfter));
                screen.game.sfx.play("awaken");
            }
            g.onWeaponsChanged();
            rebuild();
        });
    }

    /** 숙련도 레벨이 올랐을 때 눈에 띄는 변화 한마디. */
    private String masteryNote(int lv) {
        com.yongsa.config.Balance.MasteryCfg m = g.balance.enhance.mastery;
        if (lv == m.destroyZeroLevel) return " · 이제 무기가 파괴되지 않는다";
        if (lv == m.downZeroLevel) return " · 이제 단계가 떨어지지 않는다";
        if (lv >= m.maxLevel) return " · 마스터! 성공 확률 +" + ProbBar.format(g.enhance.successBonus(lv)) + "%p";
        if (g.enhance.successBonus(lv) > g.enhance.successBonus(lv - 1)) return " · 성공 확률 +" + ProbBar.format(g.enhance.successBonus(lv)) + "%p";
        return " · 하락·파괴 확률이 줄었다";
    }

    /** 숙련도로 줄어든 확률이면 원래 값을 함께 (예: " (원래 21)"). */
    private static String reduced(double base, double now) {
        return Math.abs(base - now) < 1e-9 ? "" : " (" + ProbBar.format(base) + ")";
    }

    // ───────────────────────── 분해

    private void buildDismantle(Table body) {
        GameState s = g.state;
        body.top();
        body.add(label("안 쓰는 무기를 분해하면 광물 " + g.balance.enhance.dismantleRefund
                + "개를 돌려받는다. 장착한 무기와 성검은 분해할 수 없다.", "small", Ui.DIM)).colspan(3).left().padBottom(14).row();
        boolean any = false;
        for (Weapon w : s.weapons) {
            if (g.forge.dismantleBlockReason(s, w) != null) continue;
            any = true;
            OreType ore = OreType.valueOf(g.balance.weapon(w.defId).ore);
            body.add(new Image(art.sword(w.defId, w.awakened))).size(48).padRight(12);
            body.add(label(g.rules.weaponName(w) + "  →  " + ore.displayName + " " + g.balance.enhance.dismantleRefund + "개"))
                    .width(520).left();
            // 분해는 되돌릴 수 없으므로 흡수·파괴 위험과 같은 빨간 확인창
            body.add(nav.add("분해", () -> screen.modals.push(new ConfirmDialog(skin, "분해",
                    new ConfirmDialog.Line[]{new ConfirmDialog.Line(g.rules.weaponName(w) + "을 분해합니다. 되돌릴 수 없습니다.", null)},
                    "분해하기", true, () -> {
                        int n = g.forge.dismantle(s, w);
                        resultText = ore.displayName + " " + n + "개를 돌려받았다";
                        resultColor = Ui.BLUE;
                        screen.game.sfx.play("down");
                        screen.save(false);
                        rebuild();
                    })))).width(120).height(46).padBottom(10).row();
        }
        if (!any) body.add(label("분해할 수 있는 무기가 없다", "default", Ui.DIM)).colspan(3).padTop(30).row();

        // 흡수: 스킬이 열린 검(+5 이상)을 흡수하면 검은 사라지고 스킬은 어떤 검을 들어도 쓴다
        body.add(label("흡수 · 스킬이 열린 검(+" + g.balance.enhance.skillUnlockLevel + " 이상)을 흡수하면 검은 사라지고, 그 스킬은 어떤 검을 들어도 쓸 수 있다.",
                "small", Ui.PURPLE)).colspan(3).left().padTop(22).padBottom(10).row();
        boolean anyAbsorb = false;
        for (Weapon w : s.weapons) {
            if (g.rules.isHoly(w) || g.balance.weapon(w.defId).skill == null) continue;
            String why = g.absorb.skillAbsorbBlockReason(s, w);
            if (why != null && !why.startsWith("이미")) continue;
            anyAbsorb = true;
            com.yongsa.config.Balance.SkillDef sk = g.balance.weapon(w.defId).skill;
            body.add(new Image(art.sword(w.defId, w.awakened))).size(48).padRight(12);
            body.add(label(g.rules.weaponName(w) + "  →  스킬 「" + sk.name + "」" + (w.awakened ? " (각성)" : "")
                    + (why != null ? "  · " + why : ""), "default", why == null ? Ui.TEXT : Ui.DIM)).width(520).left();
            TextButton absorbButton = nav.add("흡수", () -> confirmAbsorb(w));
            absorbButton.setDisabled(why != null);
            body.add(absorbButton).width(120).height(46).padBottom(10).row();
        }
        if (!anyAbsorb) body.add(label("흡수할 수 있는 검이 없다 (검을 +" + g.balance.enhance.skillUnlockLevel + " 까지 강화하자)",
                "default", Ui.DIM)).colspan(3).padTop(6).row();
        if (resultText != null) body.add(label(resultText, "default", resultColor)).colspan(3).padTop(14).row();
    }

    private void confirmAbsorb(Weapon w) {
        com.yongsa.config.Balance.SkillDef sk = g.balance.weapon(w.defId).skill;
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(g.rules.weaponName(w) + "을 흡수합니다.", null),
                new ConfirmDialog.Line("검은 사라지고, 「" + sk.name + "」" + (w.awakened ? " (각성)" : "") + "을 어떤 검을 들어도 쓸 수 있다.", Ui.PURPLE),
                new ConfirmDialog.Line("공격력은 오르지 않는다. 되돌릴 수 없다.", Ui.DIM),
        };
        screen.modals.push(new ConfirmDialog(skin, "검 흡수", lines, "흡수하기", true, () -> {
            g.absorb.absorbSkill(g.state, w);
            g.onWeaponsChanged();
            resultText = "「" + sk.name + "」을 익혔다! 이제 어떤 검을 들어도 쓸 수 있다";
            resultColor = Ui.PURPLE;
            screen.game.sfx.play("awaken");
            screen.save(false);
            rebuild();
        }));
    }

    // ───────────────────────── 연출

    private void startHammer(String weaponId, boolean awakened, Runnable then) {
        if (motion.busy()) return;
        workingDef = weaponId;
        workingAwakened = awakened;
        resultText = null;
        playedStrikes = 0;
        motion.start(Math.max(1.35f, g.balance.enhance.hammerSeconds) / speed());
        afterHammer = then;
        rebuild();
    }

    @Override
    public void rebuild() {
        int f = nav.focusIndex();
        clearChildren();
        nav.clear();
        build();
        nav.setFocusIndex(f);
    }
}
