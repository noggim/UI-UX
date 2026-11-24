package com.yongsa.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.yongsa.YongsaGame;

/** Windows 데스크톱 창으로 실행한다. 기본 1280×720, 크기를 바꿔도 비율을 유지한다 (NFR-01). */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (args.length == 2 && "--visual-check".equals(args[0])) {
            VisualSmokeLauncher.main(new String[]{args[1]});
            return;
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("용사가 다한다");
        config.setWindowedMode(YongsaGame.WIDTH, YongsaGame.HEIGHT);
        config.setForegroundFPS(60);
        config.useVsync(true);
        new Lwjgl3Application(new YongsaGame(), config);
    }
}
