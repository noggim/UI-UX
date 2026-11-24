package com.yongsa.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.yongsa.model.GameState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 저장 칸 하나 (FR-저장-01). 사용자 폴더의 JSON 파일에 쓴다.
 * 임시 파일에 먼저 쓰고 바꿔치기해서, 쓰는 도중 꺼져도 이전 저장이 남는다 (NFR-11).
 */
public class SaveManager {
    public static class LoadResult {
        public final GameState state;
        public final boolean corrupt;

        LoadResult(GameState state, boolean corrupt) {
            this.state = state;
            this.corrupt = corrupt;
        }
    }

    private final Path dir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SaveManager(Path dir) {
        this.dir = dir;
    }

    public static SaveManager inUserHome() {
        String isolated = System.getProperty("yongsa.saveDir");
        if (isolated != null && !isolated.isBlank()) return new SaveManager(Paths.get(isolated));
        return new SaveManager(Paths.get(System.getProperty("user.home"), ".yongsa_game"));
    }

    public Path savePath() {
        return dir.resolve("save.json");
    }

    public boolean hasSave() {
        return Files.exists(savePath());
    }

    public void save(GameState s) throws IOException {
        writeAtomic(savePath(), gson.toJson(s));
    }

    /** 저장이 없으면 state 가 null, 깨졌으면 corrupt 가 true (FR-저장-05). */
    public LoadResult load() {
        if (!hasSave()) return new LoadResult(null, false);
        try {
            String json = new String(Files.readAllBytes(savePath()), StandardCharsets.UTF_8);
            GameState s = gson.fromJson(json, GameState.class);
            if (s == null || s.weapons == null || s.weapons.isEmpty()) return new LoadResult(null, true);
            return new LoadResult(s, false);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return new LoadResult(null, true);
        }
    }

    public void delete() throws IOException {
        Files.deleteIfExists(savePath());
    }

    public Settings loadSettings() {
        Path p = dir.resolve("settings.json");
        try {
            if (Files.exists(p)) {
                Settings s = gson.fromJson(new String(Files.readAllBytes(p), StandardCharsets.UTF_8), Settings.class);
                if (s != null) return s;
            }
        } catch (IOException | JsonParseException e) {
            // 설정이 깨졌으면 기본값으로
        }
        return new Settings();
    }

    public void saveSettings(Settings s) {
        try {
            writeAtomic(dir.resolve("settings.json"), gson.toJson(s));
        } catch (IOException e) {
            // 설정 저장 실패는 게임을 멈출 일이 아니다
        }
    }

    private void writeAtomic(Path target, String json) throws IOException {
        Files.createDirectories(dir);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
