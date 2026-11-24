package com.yongsa.map;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 방의 출구를 잇는 길목. 그림 길목은 실제 바닥 다각형을 쓰고, 예전 맵은 타일 통로를 쓴다.
 * 이음(link)의 문이 닫히면(던전 봉인, 방 잠금) 통로도 막힌다.
 */
public class Corridor {
    public final Link link;
    /** 바닥·벽 그림 테마: 이어진 던전 (캠프·마을끼리면 CAMP). */
    public final String theme;
    /** 통로 바닥 칸들 (x, y). */
    public final List<int[]> tiles = new ArrayList<>();
    /** 가운데 줄 칸들: doorA 출구에서 doorB 출구까지 차례대로. */
    public final List<int[]> path = new ArrayList<>();
    /** 그림 바닥을 따라 잰 길. 월드 좌표이며 doorA → doorB 순서다. */
    public final List<float[]> centerline = new ArrayList<>();
    public MapDef.PassageScene scene;
    public float originX, originY, widthPx, heightPx;
    public boolean flip;

    public float sceneX(float u) { return originX + (flip ? 1 - u : u) * widthPx; }
    public float sceneY(float v) { return originY + (1 - v) * heightPx; }

    public boolean allowsFoot(float x, float y, float radius) {
        if (scene == null || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(radius) || radius < 0) return false;
        float px = x - originX;
        if (flip) px = widthPx - px;
        WalkMask mask = WalkMask.get(scene.walkMask);
        if (mask != null) return mask.fits(px / widthPx, 1 - (y - originY) / heightPx, radius / widthPx, radius / heightPx);
        return RoomSceneLayout.Scene.fitsPolygon(scene.walkable, px, y - originY, radius, widthPx, heightPx, true);
    }

    Corridor(Link link, String theme) {
        this.link = link;
        this.theme = theme;
    }

    /** 저장용 이름: 이은 두 출구 (예: "CAMP.left>L1.east"). */
    public String key() {
        return link.a.id + "." + link.doorA.name + ">" + link.b.id + "." + link.doorB.name;
    }

    public boolean closed() {
        return link.doorA.isClosed() || link.doorB.isClosed();
    }
}
