package com.yongsa.map;

import com.google.gson.Gson;

import java.util.List;

/**
 * assets/data/map.json: 통합 맵의 방 배치 (방 배치는 고정, A10).
 * 그림 방은 배경 그림(scene), 가로 크기(width, 타일), 왼쪽 아래(origin)로 배치한다.
 * origin이 없는 예전 배치는 지도 칸(cx, cy)을 쓴다.
 * 이음(link)은 "방.출구" 두 개를 잇는다. 캠프 출구 이름은 LEFT/RIGHT/UP/DOWN.
 */
public class MapDef {
    /** 지도 칸 하나의 크기 (타일). 방은 칸 가운데에 놓이고, 칸끼리는 떨어져 있다. */
    public int slotW, slotH;
    public int minCellX, maxCellX, minCellY, maxCellY;
    /** 캠프 바닥 크기 (벽 제외, 타일). */
    public int[] campSize;
    public List<RoomDef> rooms;
    public List<LinkDef> links;
    /** 완성된 길목 그림과 그림 위에서 직접 잰 바닥 경계. */
    public List<PassageScene> passageScenes;
    /** Local repaints and their floor outlines, spanning a room and passage together. */
    public List<PaintedJointLayout.Joint> joints;

    public static MapDef parse(String json) {
        MapDef def = new Gson().fromJson(json, MapDef.class);
        if (def.joints != null) PaintedJointLayout.validate(def.joints);
        return def;
    }

    public static class RoomDef {
        public String id, type, dungeon;
        public int cx, cy;
        /** 칸 중심에서 이동할 타일 수. 출구 높이를 맞추며 기본값은 0. */
        public int offsetX, offsetY;
        /** 그림 왼쪽 아래의 절대 타일 좌표. 있으면 칸 배치보다 우선한다. */
        public float[] origin;
        /** natural-room-scenes.json 의 장면 id. 없으면 타일 방 (캠프). */
        public String scene;
        /** 그림의 가로 크기 (타일). */
        public float width;
        /** 그림을 좌우로 뒤집어 쓴다. */
        public boolean flip;
        /** Per-placement doorway positions for terrain repaints; the source scene remains reusable. */
        public java.util.Map<String,float[]> portalFeet;
        /** 잡몹방 웨이브 수 (지용 버전): 한 무리를 다 잡으면 다음 무리가 나온다. 없으면 1. */
        public int waves = 1;
    }

    public static class LinkDef {
        public String a, b, gate;
        /** Enter darkness and fade to a separate space, instead of painting an outdoor connector. */
        public boolean transition;
        /** a 문으로만 b 문 앞에 나간다 (b 문은 원래 이음을 그대로 쓴다). */
        public boolean oneWay;
        public PassagePlacement painted;
    }

    public static class PassageScene {
        public String id, image;
        public int pixelWidth, pixelHeight;
        /** 정규화된 그림 좌표: 왼쪽 위 (0,0), 오른쪽 아래 (1,1). */
        public float[] walkable;
        /** 이동 마스크 PNG (assets 아래 경로). 있으면 walkable 대신 쓴다. */
        public String walkMask;
        public List<float[]> path;
    }

    public static class PassagePlacement {
        public String scene;
        public float[] origin;
        public float width;
        public boolean flip, reverse;
        /** Revised walking route in absolute tile coordinates after a local terrain repaint. */
        public List<float[]> route;
    }
}
