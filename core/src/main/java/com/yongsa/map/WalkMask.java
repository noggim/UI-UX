package com.yongsa.map;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.imageio.ImageIO;

/**
 * 이동 마스크: 그림과 같은 비율의 흑백 PNG. 흰색(밝고 불투명)이면 걸을 수 있고 나머지는 막힌다.
 * 그림 위에 겹쳐 칠하므로 바닥 도형보다 그림과 어긋나기 어렵다. 있으면 walkable·blocked 도형 대신 쓴다.
 */
public final class WalkMask {
    /** assets 아래 경로 → PNG 바이트. 기본은 작업 폴더의 assets/, 없으면 실행 파일 안. */
    public static Function<String, byte[]> reader = WalkMask::readDefault;
    private static final Map<String, WalkMask> cache = new HashMap<>();

    public final int width, height;
    private final BitSet floor;

    private WalkMask(int width, int height, BitSet floor) {
        this.width = width; this.height = height; this.floor = floor;
    }

    /** null 경로는 마스크 없음. 같은 경로는 한 번만 읽는다. */
    public static synchronized WalkMask get(String path) {
        if (path == null) return null;
        return cache.computeIfAbsent(path, p -> decode(p, reader.apply(p)));
    }

    public static synchronized void clearCache() { cache.clear(); }

    static WalkMask decode(String path, byte[] png) {
        if (png == null) throw new IllegalArgumentException("Missing walk mask: " + path);
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null) throw new IllegalArgumentException("Not a PNG walk mask: " + path);
            int w = img.getWidth(), h = img.getHeight();
            BitSet bits = new BitSet(w * h);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
                if (a >= 128 && r + g + b >= 3 * 128) bits.set(x + y * w);
            }
            return new WalkMask(w, h, bits);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unreadable walk mask: " + path, e);
        }
    }

    /** 그림 좌표(u, v: 0~1, 위가 0)의 칸이 바닥인가. 그림 밖은 막힘. */
    public boolean floor(float u, float v) {
        if (!(u >= 0 && u < 1 && v >= 0 && v < 1)) return false;
        return floor.get((int) (u * width) + (int) (v * height) * width);
    }

    /** 발(가로 반지름 ru, 세로 반지름 rv, 그림 비율)의 가운데와 둘레 여덟 점이 모두 바닥인가. */
    public boolean fits(float u, float v, float ru, float rv) {
        if (!floor(u, v)) return false;
        if (ru <= 0 && rv <= 0) return true;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            if (!floor(u + (float) (Math.cos(a) * ru), v + (float) (Math.sin(a) * rv))) return false;
        }
        return true;
    }

    private static byte[] readDefault(String path) {
        try {
            Path file = Path.of("assets", path);
            if (Files.exists(file)) return Files.readAllBytes(file);
            try (InputStream in = WalkMask.class.getResourceAsStream("/" + path)) {
                return in == null ? null : in.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
