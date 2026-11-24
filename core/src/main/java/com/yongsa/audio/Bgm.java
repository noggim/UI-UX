package com.yongsa.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

/**
 * 배경음악 (음악 담당). assets/audio/bgm/&lt;이름&gt;.ogg (또는 .mp3, .wav) 를 반복 재생한다.
 * 파일이 없거나 소리 장치가 없으면 조용히 넘어가므로, 음악 파일이 생기기 전에도 불러도 된다.
 * 예: game.bgm.play("title"), game.bgm.play("camp"), game.bgm.stop().
 */
public class Bgm implements Disposable {
    private static final String DIR = "audio/bgm/";
    private static final String[] EXTS = {".ogg", ".mp3", ".wav"};

    private Music current;
    private String currentName;
    private float volume = 0.7f;

    /** 이름의 곡을 반복 재생한다. 이미 그 곡이면 처음부터 다시 틀지 않는다. */
    public void play(String name) {
        if (name == null) {
            stop();
            return;
        }
        if (name.equals(currentName) && current != null) return;
        stop();
        FileHandle file = find(name);
        if (file == null) return;
        try {
            current = Gdx.audio.newMusic(file);
            current.setLooping(true);
            current.setVolume(volume);
            current.play();
            currentName = name;
        } catch (RuntimeException e) {
            current = null;
            currentName = null;
        }
    }

    public void stop() {
        if (current != null) {
            current.stop();
            current.dispose();
        }
        current = null;
        currentName = null;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (current != null) current.setVolume(volume);
    }

    /** 지금 틀고 있는 곡 이름 (없으면 null). */
    public String playing() {
        return currentName;
    }

    private static FileHandle find(String name) {
        if (Gdx.files == null) return null;
        for (String ext : EXTS) {
            FileHandle f = Gdx.files.internal(DIR + name + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    @Override
    public void dispose() {
        stop();
    }
}
