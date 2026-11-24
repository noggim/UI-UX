package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.config.Balance.WeaponDef;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;

/** 가방 (UI-06): 무기 목록과 장착, 광물 개수, 용사에게 흡수된 보스의 힘. */
public class BagWindow extends Modal {
    private final GameScreen screen;
    private com.badlogic.gdx.scenes.scene2d.ui.ScrollPane detailPane;

    public BagWindow(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (detailPane != null && (keycode == com.badlogic.gdx.Input.Keys.PAGE_DOWN || keycode == com.badlogic.gdx.Input.Keys.PAGE_UP)) {
            detailPane.setScrollY(detailPane.getScrollY() + (keycode == com.badlogic.gdx.Input.Keys.PAGE_DOWN ? 160 : -160));
            detailPane.updateVisualScroll();
            return true;
        }
        if (screen.game.settings.keys().is(com.yongsa.save.KeyBindings.Action.BAG, keycode)) {
            close();
            return true;
        }
        return super.keyDown(keycode);
    }

    @Override
    public void build() {
        GameSession g = screen.session;
        GameState s = g.state;
        Table p = panel();
        p.add(label("가방", "large", Ui.GOLD)).colspan(3).left().padBottom(8).row();
        p.add(label("여정에서 모은 장비와, 용사에게 남은 힘", "small", Ui.DIM)).colspan(3).left().padBottom(24).row();

        Table preview = new Table(skin);
        preview.setBackground(skin.getDrawable("panelDark"));
        preview.pad(18);
        preview.add(label("현재 장착", "small", Ui.GOLD)).padBottom(14).row();
        Weapon current = s.equipped();
        if (current != null) {
            preview.add(Ui.artImage(screen.game.art.sword(current.defId, current.awakened))).size(172).padBottom(18).row();
            preview.add(label(g.rules.weaponName(current), "default", Ui.TEXT)).padBottom(12).row();
            preview.add(label("공격력  " + g.rules.attack(s), "large", Ui.GOLD)).row();
        }
        p.add(preview).width(224).height(354).top().padRight(20);

        Table weapons = new Table();
        weapons.top().left();
        weapons.add(label("무기 (눌러서 장착)", "default", Ui.DIM)).left().colspan(2).padBottom(8).row();
        for (Weapon w : s.weapons) {
            boolean equipped = w.uid == s.equippedUid;
            weapons.add(Ui.artImage(screen.game.art.sword(w.defId, w.awakened))).size(50).padRight(8);
            String text = g.rules.weaponName(w) + "\n공격력 " + g.rules.attack(s, w) + (equipped ? "  ·  장착 중" : "");
            TextButton b = nav.add(text, () -> {
                g.forge.equip(s, w);
                g.onWeaponsChanged();
                screen.game.sfx.play("select");
                rebuild();
            });
            b.getLabel().setColor(equipped ? Ui.GOLD : Ui.TEXT);
            weapons.add(b).width(300).height(68).left().padBottom(8).row();
        }
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane list = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(weapons);
        list.setScrollingDisabled(true, false);
        p.add(list).width(370).height(354).top().padRight(24);

        Table side = new Table();
        side.top().left();
        side.add(label("광물", "default", Ui.DIM)).left().colspan(2).padBottom(8).row();
        for (OreType t : OreType.values()) {
            side.add(Ui.artImage(screen.game.art.gem(t))).size(42).padRight(8);
            side.add(label(t.displayName + "  " + s.ore(t) + "개")).left().padBottom(6).row();
        }
        side.add(label("흡수한 보스 힘 " + s.bossesDefeated.size() + "/3", "default", Ui.DIM)).left().colspan(2).padTop(18).padBottom(8).row();
        if (s.bossesDefeated.isEmpty()) {
            side.add(label("아직 없다", "small", Ui.DIM)).left().colspan(2).row();
        }
        for (String id : s.bossesDefeated) {
            side.add(wrapped("· " + g.balance.dungeon(id).boss.name + "의 힘 (최대 체력)", Ui.GREEN)).width(280).left().colspan(2).padBottom(8).row();
        }
        side.add(label("스킬 칸", "default", Ui.DIM)).left().colspan(2).padTop(18).padBottom(8).row();
        com.yongsa.model.Rules.SkillSlot[] slots = g.rules.skillSlots(s);
        boolean any = false;
        for (int i = 0; i < slots.length; i++) {
            com.yongsa.model.Rules.SkillSlot k = slots[i];
            if (k == null) continue;
            any = true;
            String how = k.learned ? "흡수해서 익힘 · 어떤 검이든" : i == com.yongsa.model.Rules.HOLY_SLOT ? "깨어난 성검을 들면"
                    : g.balance.weapon(k.weaponId).name + "을 들면";
            side.add(wrapped(g.skillKeys[i] + "  " + k.def.name + (k.awakened ? " (각성)" : "") + "\n   " + how,
                    k.usable ? Ui.GOLD : Ui.DIM)).width(280).left().colspan(2).padBottom(8).row();
        }
        if (!any) side.add(wrapped("검을 +" + g.balance.enhance.skillUnlockLevel + " 까지 강화하면 스킬 칸이 열린다", Ui.DIM))
                .width(280).left().colspan(2).row();
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane details = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(side);
        details.setScrollingDisabled(true, false);
        detailPane = details;
        p.add(details).top().width(330).height(354).row();
        p.add(nav.add("닫기", this::close)).colspan(3).width(200).height(46).padTop(24).row();
        p.add(label("방향키 / Tab  무기 선택   ·   PgUp / PgDn  오른쪽 정보 넘기기", "small", Ui.DIM)).colspan(3).padTop(12);
        nav.setFocusIndex(Math.max(0, s.weapons.indexOf(s.equipped())));
    }

    private com.badlogic.gdx.scenes.scene2d.ui.Label wrapped(String text, com.badlogic.gdx.graphics.Color color) {
        com.badlogic.gdx.scenes.scene2d.ui.Label value = label(text, "small", color);
        value.setWrap(true);
        return value;
    }
}
