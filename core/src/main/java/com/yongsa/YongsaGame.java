package com.yongsa;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.yongsa.config.Balance;
import com.yongsa.config.Story;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.audio.Bgm;
import com.yongsa.audio.Sfx;
import com.yongsa.model.GameState;
import com.yongsa.save.SaveManager;
import com.yongsa.save.Settings;
import com.yongsa.ui.TitleScreen;
import com.yongsa.ui.Ui;
import com.yongsa.map.MapDef;
import com.yongsa.map.RoomSceneLayout;
import com.yongsa.map.WorldMap;

import java.io.IOException;

/** 게임 전체. 글꼴·그림·소리·수치를 한 번 읽고 화면을 바꿔 가며 쓴다. */
public class YongsaGame extends Game {
    public static final int WIDTH = 1280, HEIGHT = 720;

    public Balance balance;
    public MapDef mapDef;
    public RoomSceneLayout scenes;
    public Story story;
    public Fonts fonts;
    public Art art;
    public Sfx sfx;
    /** 배경음악 (audio/Bgm). */
    public Bgm bgm;
    public Skin skin;
    public SpriteBatch batch;
    public ShapeRenderer shapes;
    public SaveManager saves;
    public Settings settings;

    @Override
    public void create() {
        balance = Balance.parse(Gdx.files.internal("data/balance.json").readString("UTF-8"));
        mapDef = MapDef.parse(Gdx.files.internal("data/map.json").readString("UTF-8"));
        scenes = RoomSceneLayout.parse(Gdx.files.internal("data/natural-room-scenes.json").readString("UTF-8"));
        story = Story.parse(Gdx.files.internal("data/story.json").readString("UTF-8"));
        fonts = new Fonts();
        art = new Art();
        sfx = new Sfx();
        bgm = new Bgm();
        skin = Ui.createSkin(fonts, art);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        saves = SaveManager.inUserHome();
        settings = saves.loadSettings();
        applySettings();
        setScreen(new TitleScreen(this));
    }

    public WorldMap newMap() {
        return new WorldMap(mapDef, scenes, balance.tileSize);
    }

    public void applySettings() {
        sfx.volume = settings.volume;
        bgm.setVolume(settings.volume);
        if (settings.fullscreen && !Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else if (!settings.fullscreen && Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(WIDTH, HEIGHT);
        }
    }

    public void saveSettings() {
        settings.storeKeys();
        saves.saveSettings(settings);
        applySettings();
    }

    /** 저장한다. 실패하면 false. */
    public boolean save(GameState s) {
        try {
            saves.save(s);
            return true;
        } catch (IOException e) {
            Gdx.app.error("save", "저장 실패", e);
            return false;
        }
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        batch.dispose();
        shapes.dispose();
        sfx.dispose();
        bgm.dispose();
        art.dispose();
        fonts.dispose();
        skin.dispose();
    }
}
