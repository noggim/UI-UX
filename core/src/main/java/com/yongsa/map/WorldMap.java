package com.yongsa.map;

import com.yongsa.model.GameState;
import com.yongsa.map.MapDef.LinkDef;
import com.yongsa.map.MapDef.RoomDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 캠프와 던전 3개가 들어 있는 통합 맵 하나 (FR-맵-03, 04).
 * 방과 길목은 각각 완성된 그림 한 장이며, 그림의 바닥 경계(다각형)가 이동 경계다.
 * 출구 위치에 맞춰 장면을 연결한다. 동굴 입구와 심연 계단은 어둠 속에서 별도 공간으로 옮겨 간다.
 * 예전 JSON에는 지도 칸 배치와 자동 타일 통로를 지원한다.
 */
public class WorldMap {
    public static final int VOID = 0, FLOOR = 1, WALL = 2, DOOR = 3, CORRIDOR = 4;
    /** 방 배치가 바뀌면 올린다. 저장 파일의 방 기록(광맥 자리)·이어하기 위치가 다르면 버린다. */
    public static final int VERSION = 10;

    public final int width, height;
    public final int tileSize;
    private final int[] tiles;
    private final Door[] doorAt;
    private final Room[] roomAt;
    private final boolean[] blocked;
    /** 통로 바닥과 그 벽 칸이 어느 통로에 속하나. */
    private final Corridor[] corridorAt;

    /** Session-owned availability; geometry stays independent of rendering. */
    public java.util.function.Predicate<Room> returnDoorOpen = r -> false;
    public final List<Room> rooms = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    public final List<Corridor> corridors = new ArrayList<>();
    public final List<PaintedJointLayout.Joint> paintedJoints = new ArrayList<>();
    private final Map<PaintedJointLayout.Joint, Door> jointDoors = new HashMap<>();
    private final Map<String, Room> byId = new HashMap<>();
    private final MapDef def;
    private final RoomSceneLayout scenes;

    public WorldMap(MapDef def, RoomSceneLayout scenes, int tileSize) {
        this.def = def;
        this.scenes = scenes;
        this.tileSize = tileSize;
        int cellsX = def.maxCellX - def.minCellX + 1;
        int cellsY = def.maxCellY - def.minCellY + 1;
        width = cellsX * def.slotW;
        height = cellsY * def.slotH;
        tiles = new int[width * height];
        doorAt = new Door[width * height];
        roomAt = new Room[width * height];
        blocked = new boolean[width * height];
        corridorAt = new Corridor[width * height];

        for (RoomDef rd : def.rooms) {
            if (rd.scene == null) buildTileRoom(rd);
            else buildSceneRoom(rd);
        }
        for (LinkDef ld : def.links) buildLink(ld);
        buildCorridors();
        buildPaintedJoints();
        alignPaintedArrivals();
        buildCampProps();
    }

    private void alignPaintedArrivals() {
        if(paintedJoints.isEmpty())return;
        var closed=new ArrayList<Boolean>();
        for(var l:links){closed.add(l.gateClosed);l.gateClosed=false;}
        try {
            for(var j:paintedJoints) {
                Door d=jointDoors.get(j);
                search:
                for(float distance:new float[]{48,64,32,80,96,24})for(float across:new float[]{0,16,-16,32,-32,48,-48}) {
                    float x=d.footX-d.dirX*distance-d.dirY*across;
                    float y=d.footY-d.dirY*distance+d.dirX*across;
                    if(roomAt((int)(x/tileSize),(int)(y/tileSize))!=d.room||!free(x,y,12,12)||d.triggers(x,y))continue;
                    boolean clear=true;
                    int n=Math.max(1,(int)(Math.hypot(x-d.footX,y-d.footY)/2));
                    for(int i=0;i<=n&&clear;i++)clear=free(x+(d.footX-x)*i/n,y+(d.footY-y)*i/n,10,10);
                    if(clear){d.arriveX=x;d.arriveY=y;break search;}
                }
            }
            for(var l:links) {
                l.doorA.outsideX=(int)(l.doorB.arriveX/tileSize);l.doorA.outsideY=(int)(l.doorB.arriveY/tileSize);
                if(!l.oneWay){l.doorB.outsideX=(int)(l.doorA.arriveX/tileSize);l.doorB.outsideY=(int)(l.doorA.arriveY/tileSize);}
            }
        }finally{for(int i=0;i<links.size();i++)links.get(i).gateClosed=closed.get(i);}
    }

    private void buildPaintedJoints() {
        if (def.joints == null) return;
        for (var j : def.joints) {
            if (!j.matches(this)) continue;
            Door door = room(j.room).doors.stream().filter(d -> Math.abs(d.footX-j.door[0])<.02f
                    && Math.abs(d.footY-j.door[1])<.02f).findFirst().orElseThrow();
            paintedJoints.add(j);jointDoors.put(j,door);
            // A repaired opening can be wider than the old painted doorway.
            // Both the barrier artwork and combat collision must cover its entire floor.
            float reach=Math.max(j.rect[2],j.rect[3]);
            float[] edges=new float[2];
            for(int side=0;side<2;side++)for(float across=0;across<=reach;across+=2) {
                int sign=side==0?-1:1;
                float px=door.footX-door.dirY*across*sign,py=door.footY+door.dirX*across*sign;
                if(!j.owns(px,py)||!j.allowsFoot(px,py,0))break;
                edges[side]=across+2;
            }
            if(edges[0]+edges[1]>0) {
                door.halfWidth=(edges[0]+edges[1])/2;
                door.openingOffset=(edges[1]-edges[0])/2;
            }
            int x0=(int)Math.floor(j.rect[0]/tileSize), y0=(int)Math.floor(j.rect[1]/tileSize);
            int x1=(int)Math.ceil((j.rect[0]+j.rect[2])/tileSize), y1=(int)Math.ceil((j.rect[1]+j.rect[3])/tileSize);
            for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                float px=(x+.5f)*tileSize,py=(y+.5f)*tileSize;
                if(!inBounds(x,y)||!j.owns(px,py))continue;
                boolean floor=j.allowsFoot(px,py,0);
                set(x,y,floor?(roomAt(x,y)==null?CORRIDOR:FLOOR):WALL);
                corridorAt[idx(x,y)]=floor?door.link.corridor:null;
            }
        }
    }

    /** 개발용: 이 자리를 덮는 그림들의 이동 마스크 경로 (방, 통로, 연결부 순). 칠할 파일을 찾을 때 쓴다. */
    public List<String> walkMasksAt(float x,float y) {
        List<String> out=new ArrayList<>();
        for(Room r:rooms)
            if(r.scene!=null && x>=r.originX && x<=r.originX+r.widthPx && y>=r.originY && y<=r.originY+r.heightPx)
                out.add(r.id+" "+r.scene.walkMask+(r.flip?" (좌우 반전)":""));
        Corridor c=corridorAtWorld(x,y);
        if(c!=null && c.scene!=null) out.add("통로 "+c.scene.walkMask+(c.flip?" (좌우 반전)":""));
        for(var j:paintedJoints) if(j.alpha(x,y)>0) out.add("연결부 "+j.walkMask);
        return out;
    }

    private PaintedJointLayout.Joint jointAt(float x,float y) {
        // Match painter's order where neighboring repair rectangles overlap.
        for(int i=paintedJoints.size()-1;i>=0;i--) {
            var j=paintedJoints.get(i);if(j.owns(x,y))return j;
        }
        return null;
    }

    private int ox(int cx) {
        return (cx - def.minCellX) * def.slotW;
    }

    private int oy(int cy) {
        return (cy - def.minCellY) * def.slotH;
    }

    private void addRoom(Room room) {
        if (byId.put(room.id, room) != null) throw new IllegalArgumentException("같은 방 id: " + room.id);
        rooms.add(room);
    }

    // ───────────────────────── 타일 방 (캠프)

    private void buildTileRoom(RoomDef rd) {
        RoomType type = RoomType.valueOf(rd.type);
        int w = def.campSize[0] + 2, h = def.campSize[1] + 2;
        int x0 = ox(rd.cx) + (def.slotW - w) / 2 + rd.offsetX;
        int y0 = oy(rd.cy) + (def.slotH - h) / 2 + rd.offsetY;
        Room room = new Room(rd.id, type, rd.dungeon, rd.cx, rd.cy, x0, y0, x0 + w - 1, y0 + h - 1);
        addRoom(room);
        for (int y = room.y0; y <= room.y1; y++) {
            for (int x = room.x0; x <= room.x1; x++) {
                boolean edge = x == room.x0 || x == room.x1 || y == room.y0 || y == room.y1;
                set(x, y, edge ? WALL : FLOOR);
                roomAt[idx(x, y)] = room;
            }
        }
    }

    /** 캠프 벽 가운데에 3칸짜리 문과 짧은 통로 끝을 낸다. 통로 끝까지 걸어가면 이어진 방으로 넘어간다. */
    private Door makeTileDoor(Room r, Door.Side side) {
        int cx, cy;
        switch (side) {
            case LEFT: cx = r.x0; cy = (r.y0 + r.y1) / 2; break;
            case RIGHT: cx = r.x1; cy = (r.y0 + r.y1) / 2; break;
            case DOWN: cx = (r.x0 + r.x1) / 2; cy = r.y0; break;
            default: cx = (r.x0 + r.x1) / 2; cy = r.y1; break;
        }
        int[] tx = new int[3], ty = new int[3];
        boolean horizontalWall = side == Door.Side.UP || side == Door.Side.DOWN;
        for (int i = 0; i < 3; i++) {
            tx[i] = horizontalWall ? cx - 1 + i : cx;
            ty[i] = horizontalWall ? cy : cy - 1 + i;
        }
        int dx = side == Door.Side.LEFT ? -1 : side == Door.Side.RIGHT ? 1 : 0;
        int dy = side == Door.Side.DOWN ? -1 : side == Door.Side.UP ? 1 : 0;
        int T = tileSize;
        Door d = new Door(r, side.name(), side, tx, ty, cx - dx, cy - dy, cx + dx, cy + dy,
                (cx + 0.5f) * T, (cy + 0.5f) * T, dx, dy);
        d.halfWidth = 1.5f * T;
        d.triggerDepth = 0.55f * T;
        d.arriveX = (cx - dx + 0.5f) * T;
        d.arriveY = (cy - dy + 0.5f) * T;
        r.doors.add(d);
        for (int i = 0; i < 3; i++) {
            set(tx[i], ty[i], DOOR);
            doorAt[idx(tx[i], ty[i])] = d;
            // 문 밖 두 칸짜리 통로 끝 (어둠 속으로 이어지는 것처럼 보인다)
            for (int k = 1; k <= 2; k++) set(tx[i] + dx * k, ty[i] + dy * k, FLOOR);
        }
        for (int k = 1; k <= 2; k++) {
            int px = horizontalWall ? 1 : 0, py = horizontalWall ? 0 : 1;
            set(cx + dx * k - px * 2, cy + dy * k - py * 2, WALL);
            set(cx + dx * k + px * 2, cy + dy * k + py * 2, WALL);
        }
        return d;
    }

    // ───────────────────────── 그림 방

    private void buildSceneRoom(RoomDef rd) {
        RoomSceneLayout.Scene sc = scenes.scene(rd.scene);
        if (sc == null) throw new IllegalArgumentException("없는 장면: " + rd.scene);
        int T = tileSize;
        float w = rd.width * T, h = sc.height(w);
        float ox = Math.round((ox(rd.cx) + rd.offsetX) * T + (def.slotW * T - w) / 2f);
        float oy = Math.round((oy(rd.cy) + rd.offsetY) * T + (def.slotH * T - h) / 2f);
        if (rd.origin != null) {
            ox = rd.origin[0] * T;
            oy = rd.origin[1] * T;
        }
        int x0 = (int) Math.floor(ox / T), x1 = (int) Math.ceil((ox + w) / T) - 1;
        int y0 = (int) Math.floor(oy / T), y1 = (int) Math.ceil((oy + h) / T) - 1;
        Room room = new Room(rd.id, RoomType.valueOf(rd.type), rd.dungeon, rd.cx, rd.cy, x0, y0, x1, y1,
                sc, ox, oy, w, h, rd.flip);
        room.waves = Math.max(1, rd.waves);
        room.buildMasks(T);
        addRoom(room);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, room.floor(x, y) ? FLOOR : WALL);
                roomAt[idx(x, y)] = room;
            }
        }
        for (RoomSceneLayout.Portal p : sc.portals) {
            RoomSceneLayout.Portal placed=p;
            if(rd.portalFeet!=null && rd.portalFeet.containsKey(p.id)) {
                placed=new RoomSceneLayout.Portal();placed.id=p.id;placed.kind=p.kind;placed.facing=p.facing;
                placed.foot=rd.portalFeet.get(p.id);
                if(placed.foot==null||placed.foot.length!=2)throw new IllegalArgumentException("Invalid placed portal: "+rd.id+"."+p.id);
                normalized(placed.foot[0]);normalized(placed.foot[1]);
            }
            room.doors.add(makeSceneDoor(room, placed));
        }
    }

    private Door makeSceneDoor(Room room, RoomSceneLayout.Portal p) {
        int T = tileSize;
        float fx = room.sceneX(p.foot[0]), fy = room.sceneY(p.foot[1]);
        float[] dir = facing(p.facing, room.flip);
        float dx = dir[0], dy = dir[1];
        Door.Side side = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? Door.Side.LEFT : Door.Side.RIGHT)
                : (dy < 0 ? Door.Side.DOWN : Door.Side.UP);

        // 출구 폭: 출구 자리에서 옆으로 바닥이 끝나는 데까지
        float half = 0;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float s = 0;
            while (s < 4 * T && room.allowsFoot(fx - dy * s * sgn, fy + dx * s * sgn, 0)) s += 2;
            half = Math.max(half, s);
        }
        // 출구 안으로 걸어 들어갈 수 있는 깊이
        float reach = -T;
        for (float s = 0; s < 5 * T; s += 2) {
            if (!room.allowsFoot(fx + dx * s, fy + dy * s, 10)) break;
            reach = s;
        }
        float trigger = Math.max(-0.5f * T, Math.min(1.0f * T, reach - 0.4f * T));

        // 들어올 때 서는 자리: 출구에서 방 안쪽으로 두 칸쯤, 발이 들어가는 곳
        float ax = room.spawnX(T), ay = room.spawnY(T);
        float[] dists = {2f, 1.6f, 2.5f, 3f, 1.3f, 3.6f, 4.3f};
        float[] offs = {0f, 0.5f, -0.5f, 1f, -1f};
        search:
        for (float k : dists) {
            for (float o : offs) {
                float x = fx - dx * k * T - dy * o * T, y = fy - dy * k * T + dx * o * T;
                if (room.allowsFoot(x, y, 14)) {
                    ax = x;
                    ay = y;
                    break search;
                }
            }
        }
        int inX = (int) Math.floor(ax / T), inY = (int) Math.floor(ay / T);
        int ftx = (int) Math.floor(fx / T), fty = (int) Math.floor(fy / T);
        Door d = new Door(room, p.id, side, new int[]{ftx}, new int[]{fty}, inX, inY, inX, inY, fx, fy, dx, dy);
        d.halfWidth = Math.max(0.8f * T, Math.min(3f * T, half));
        d.triggerDepth = trigger;
        d.arriveX = ax;
        d.arriveY = ay;
        return d;
    }

    /** 그림 속 방향 이름 → 월드 방향 (위가 +y). */
    static float[] facing(String f, boolean flip) {
        float x = 0, y = 0;
        String s = f == null ? "DOWN" : f;
        if (s.contains("LEFT")) x = -1;
        if (s.contains("RIGHT")) x = 1;
        if (s.contains("UP")) y = 1;
        if (s.contains("DOWN")) y = -1;
        if (flip) x = -x;
        float l = (float) Math.sqrt(x * x + y * y);
        if (l < 0.01f) return new float[]{0, -1};
        return new float[]{x / l, y / l};
    }

    // ───────────────────────── 이음

    private void buildLink(LinkDef ld) {
        Door da = doorFor(ld.a, false), db = doorFor(ld.b, ld.oneWay);
        Link link = new Link(da.room, db.room, ld.gate);
        link.doorA = da;
        link.doorB = db;
        link.oneWay = ld.oneWay;
        link.transition = ld.transition;
        da.link = link;
        if (!ld.oneWay) db.link = link;
        links.add(link);
        // 그림 문의 '바깥'은 이어진 방에서 서는 칸이다 (잠긴 방에서 저장하면 거기서 이어 한다)
        if (da.room.scene != null) {
            da.outsideX = (int) Math.floor(db.arriveX / tileSize);
            da.outsideY = (int) Math.floor(db.arriveY / tileSize);
        }
        if (db.room.scene != null && !ld.oneWay) {
            db.outsideX = (int) Math.floor(da.arriveX / tileSize);
            db.outsideY = (int) Math.floor(da.arriveY / tileSize);
        }
    }

    private Door doorFor(String ref, boolean shared) {
        int dot = ref.indexOf('.');
        if (dot < 0) throw new IllegalArgumentException("이음은 '방.출구' 꼴이어야 한다: " + ref);
        Room r = byId.get(ref.substring(0, dot));
        String name = ref.substring(dot + 1);
        if (r == null) throw new IllegalArgumentException("없는 방: " + ref);
        if (r.scene == null) return makeTileDoor(r, Door.Side.valueOf(name));
        for (Door d : r.doors) {
            if (!d.name.equals(name)) continue;
            if (d.link != null && !shared) throw new IllegalArgumentException("이미 이어진 출구: " + ref);
            return d;
        }
        throw new IllegalArgumentException("그림에 없는 출구: " + ref);
    }

    // ───────────────────────── 통로

    private static final int[] DX = {-1, 1, 0, 0}, DY = {0, 0, -1, 1};
    /** 통로 가운데 줄끼리 이만큼은 떨어진다 (벽이 서로 붙지 않게). */
    private static final int CORRIDOR_GAP = 4;
    /** 통로는 방 그림에서 이만큼 떨어져 돈다. */
    private static final int ROOM_MARGIN = 2;
    /** 방 그림과 그 둘레 여백 칸 (통로를 짓는 동안만 쓴다). */
    private boolean[] roomZone;

    /**
     * 그림 방끼리의 이음마다 3칸 폭 통로를 판다: 출구에서 나가는 방향으로 그림 밖까지 곧게 나간 뒤,
     * 다른 방과 이미 판 통로를 비켜 꺾임이 적은 길로 짝 출구 앞까지 간다.
     */
    private void buildCorridors() {
        if (def.passageScenes != null && !def.passageScenes.isEmpty()) {
            buildPaintedCorridors();
            return;
        }
        List<Link> todo = new ArrayList<>();
        for (Link l : links)
            if (!l.oneWay && !l.transition && l.doorA.room.scene != null && l.doorB.room.scene != null) todo.add(l);
        int[] owner = new int[width * height];
        java.util.Arrays.fill(owner, -1);
        roomZone = new boolean[width * height];
        for (Room r : rooms)
            for (int y = r.y0 - ROOM_MARGIN; y <= r.y1 + ROOM_MARGIN; y++)
                for (int x = r.x0 - ROOM_MARGIN; x <= r.x1 + ROOM_MARGIN; x++)
                    if (inBounds(x, y)) roomZone[idx(x, y)] = true;
        List<List<int[]>> mouthsA = new ArrayList<>(), mouthsB = new ArrayList<>();
        for (int i = 0; i < todo.size(); i++) {
            List<int[]> a = mouth(todo.get(i).doorA), b = mouth(todo.get(i).doorB);
            mouthsA.add(a);
            mouthsB.add(b);
            reserve(owner, a, i);
            reserve(owner, b, i);
        }
        for (int i = 0; i < todo.size(); i++) {
            Link l = todo.get(i);
            List<int[]> a = mouthsA.get(i), b = mouthsB.get(i);
            int[] s = a.get(a.size() - 1), e = b.get(b.size() - 1);
            List<int[]> path = route(s[0], s[1], dirIndex(l.doorA.side), e[0], e[1], owner, i);
            if (path == null) {
                System.err.println("통로를 낼 수 없다: " + l.doorA.room.id + "." + l.doorA.name + " - " + l.doorB.room.id + "." + l.doorB.name);
                continue;
            }
            List<int[]> line = new ArrayList<>(a);
            line.addAll(path.subList(1, path.size() - 1));
            List<int[]> back = new ArrayList<>(b);
            Collections.reverse(back);
            line.addAll(back);
            String theme = l.a.dungeonId != null ? l.a.dungeonId : l.b.dungeonId != null ? l.b.dungeonId : "CAMP";
            Corridor c = new Corridor(l, theme);
            c.path.addAll(line);
            for (int[] p : line) carve(c, p[0], p[1]);
            reserve(owner, line, i);
            l.corridor = c;
            corridors.add(c);
        }
        // 통로 옆 빈 곳은 벽
        for (Corridor c : corridors) {
            for (int[] p : c.tiles) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int x = p[0] + dx, y = p[1] + dy;
                        if (!inBounds(x, y) || tile(x, y) != VOID) continue;
                        set(x, y, WALL);
                        corridorAt[idx(x, y)] = c;
                    }
                }
            }
        }
    }

    /** 완성된 그림을 그대로 놓고, 그 그림의 바닥 다각형에서 지도 칸을 만든다. */
    private void buildPaintedCorridors() {
        Map<String, MapDef.PassageScene> art = new HashMap<>();
        for (MapDef.PassageScene s : def.passageScenes) {
            if (s.id == null || s.image == null || s.pixelWidth <= 0 || s.pixelHeight <= 0
                    || s.walkable == null || s.walkable.length < 6 || s.walkable.length % 2 != 0
                    || s.path == null || s.path.size() < 2 || art.put(s.id, s) != null)
                throw new IllegalArgumentException("Invalid passage scene: " + s.id);
            for (float v : s.walkable) normalized(v);
            for (float[] p : s.path) {
                if (p == null || p.length != 2) throw new IllegalArgumentException("Invalid passage anchor: " + s.id);
                normalized(p[0]); normalized(p[1]);
            }
        }
        for (int i = 0; i < links.size(); i++) {
            Link l = links.get(i);
            if (l.oneWay || l.transition) continue;
            MapDef.PassagePlacement p = def.links.get(i).painted;
            if (p == null || !art.containsKey(p.scene) || p.origin == null || p.origin.length != 2
                    || !Float.isFinite(p.origin[0]) || !Float.isFinite(p.origin[1])
                    || !Float.isFinite(p.width) || p.width <= 0)
                throw new IllegalArgumentException("Missing/invalid painted passage: " + def.links.get(i).a);
            String theme = l.a.dungeonId != null ? l.a.dungeonId : l.b.dungeonId != null ? l.b.dungeonId : "CAMP";
            Corridor c = new Corridor(l, theme);
            c.scene = art.get(p.scene);
            c.originX = p.origin[0] * tileSize; c.originY = p.origin[1] * tileSize;
            c.widthPx = p.width * tileSize; c.heightPx = c.widthPx * c.scene.pixelHeight / c.scene.pixelWidth;
            c.flip = p.flip;
            for (float[] point : c.scene.path) c.centerline.add(new float[]{c.sceneX(point[0]), c.sceneY(point[1])});
            if (p.reverse) Collections.reverse(c.centerline);
            if(p.route!=null) {
                if(p.route.size()<2)throw new IllegalArgumentException("Missing repaired route: "+c.key());
                c.centerline.clear();
                for(float[] point:p.route) {
                    if(point==null||point.length!=2||!Float.isFinite(point[0])||!Float.isFinite(point[1]))
                        throw new IllegalArgumentException("Invalid repaired route: "+c.key());
                    c.centerline.add(new float[]{point[0]*tileSize,point[1]*tileSize});
                }
            }
            float[] first = c.centerline.get(0), last = c.centerline.get(c.centerline.size() - 1);
            if (Math.hypot(first[0] - l.doorA.footX, first[1] - l.doorA.footY) > 1
                    || Math.hypot(last[0] - l.doorB.footX, last[1] - l.doorB.footY) > 1)
                throw new IllegalArgumentException("Passage exits do not meet room exits: " + c.key());
            // Legacy debug views use a tile centerline. Actual movement uses the floor polygon.
            for (int k = 1; k < c.centerline.size(); k++) {
                float[] a = c.centerline.get(k - 1), b = c.centerline.get(k);
                int steps = Math.max(1, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]) / (tileSize / 2f)));
                for (int j = 0; j <= steps; j++) {
                    int x = (int) Math.floor((a[0] + (b[0] - a[0]) * j / steps) / tileSize);
                    int y = (int) Math.floor((a[1] + (b[1] - a[1]) * j / steps) / tileSize);
                    if (c.path.isEmpty() || c.path.get(c.path.size() - 1)[0] != x || c.path.get(c.path.size() - 1)[1] != y)
                        c.path.add(new int[]{x, y});
                }
            }
            int x0 = (int) Math.floor(c.originX / tileSize), y0 = (int) Math.floor(c.originY / tileSize);
            int x1 = (int) Math.ceil((c.originX + c.widthPx) / tileSize), y1 = (int) Math.ceil((c.originY + c.heightPx) / tileSize);
            if (x0 < 0 || y0 < 0 || x1 > width || y1 > height)
                throw new IllegalArgumentException("Passage outside map: " + c.key());
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                if (!c.allowsFoot((x + .5f) * tileSize, (y + .5f) * tileSize, 0)) continue;
                Room r = roomAt(x, y);
                if (r != null && r != l.a && r != l.b)
                    throw new IllegalArgumentException("Passage crosses unrelated room: " + c.key() + " / " + r.id);
                if (corridorAt[idx(x, y)] != null)
                    throw new IllegalArgumentException("Painted passages overlap: " + c.key());
                if (r != null && r.floor(x, y)) continue;
                set(x, y, CORRIDOR);
                corridorAt[idx(x, y)] = c;
                c.tiles.add(new int[]{x, y});
            }
            l.corridor = c;
            // Walking passages start at the painted lip; unlike teleport stubs,
            // their first curve need not extend straight beyond that point.
            l.doorA.triggerDepth = Math.min(0, l.doorA.triggerDepth);
            l.doorB.triggerDepth = Math.min(0, l.doorB.triggerDepth);
            corridors.add(c);
        }
    }

    private static void normalized(float v) {
        if (!Float.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Invalid passage coordinate");
    }

    private static int dirIndex(Door.Side side) {
        switch (side) {
            case LEFT: return 0;
            case RIGHT: return 1;
            case DOWN: return 2;
            default: return 3;
        }
    }

    /** 출구 자리에서 나가는 방향으로 방 그림(과 둘레 여백) 밖 첫 칸까지 곧게 가는 칸들. */
    private List<int[]> mouth(Door d) {
        int dir = dirIndex(d.side);
        int x = (int) Math.floor(d.footX / tileSize), y = (int) Math.floor(d.footY / tileSize);
        List<int[]> line = new ArrayList<>();
        while (inBounds(x, y)) {
            line.add(new int[]{x, y});
            if (!nearRoom(d.room, x, y, ROOM_MARGIN)) break;
            x += DX[dir];
            y += DY[dir];
        }
        return line;
    }

    private static boolean nearRoom(Room r, int x, int y, int m) {
        return x >= r.x0 - m && x <= r.x1 + m && y >= r.y0 - m && y <= r.y1 + m;
    }

    /** 다른 통로가 이 줄 가까이 지나지 못하게 표시한다 (방 그림 안쪽 칸은 방이 막는다). */
    private void reserve(int[] owner, List<int[]> line, int who) {
        for (int[] p : line) {
            for (int dy = -CORRIDOR_GAP; dy <= CORRIDOR_GAP; dy++) {
                for (int dx = -CORRIDOR_GAP; dx <= CORRIDOR_GAP; dx++) {
                    int x = p[0] + dx, y = p[1] + dy;
                    if (inBounds(x, y) && owner[idx(x, y)] < 0) owner[idx(x, y)] = who;
                }
            }
        }
    }

    /** 통로 가운데 줄이 지날 수 있는 칸인가: 맵 가장자리·방 그림 둘레·다른 통로 곁이 아니다. */
    private boolean routable(int x, int y, int[] owner, int who) {
        if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) return false;
        int o = owner[idx(x, y)];
        if (o >= 0 && o != who) return false;
        return !roomZone[idx(x, y)];
    }

    /** 꺾일 때마다 값을 더 치르는 최단 길 (가운데 줄 칸들, 시작·끝 포함). 길이 없으면 null. */
    private List<int[]> route(int sx, int sy, int sdir, int ex, int ey, int[] owner, int who) {
        final int turn = 6;
        int n = width * height;
        int[] cost = new int[n * 4];
        int[] prev = new int[n * 4];
        java.util.Arrays.fill(cost, Integer.MAX_VALUE);
        PriorityQueue<int[]> open = new PriorityQueue<>((p, q) -> Integer.compare(p[0], q[0]));
        int start = idx(sx, sy) * 4 + sdir;
        cost[start] = 0;
        prev[start] = -1;
        open.add(new int[]{Math.abs(ex - sx) + Math.abs(ey - sy), start});
        int goal = -1;
        while (!open.isEmpty()) {
            int[] top = open.poll();
            int st = top[1];
            int i = st / 4, dir = st % 4, x = i % width, y = i / width;
            if (top[0] - (Math.abs(ex - x) + Math.abs(ey - y)) > cost[st]) continue;
            if (x == ex && y == ey) {
                goal = st;
                break;
            }
            for (int nd = 0; nd < 4; nd++) {
                if ((nd ^ 1) == dir) continue; // 되돌아가지 않는다
                int nx = x + DX[nd], ny = y + DY[nd];
                boolean end = nx == ex && ny == ey;
                if (!end && !routable(nx, ny, owner, who)) continue;
                int ns = idx(nx, ny) * 4 + nd;
                int c = cost[st] + 1 + (nd == dir ? 0 : turn);
                if (c >= cost[ns]) continue;
                cost[ns] = c;
                prev[ns] = st;
                open.add(new int[]{c + Math.abs(ex - nx) + Math.abs(ey - ny), ns});
            }
        }
        if (goal < 0) return null;
        List<int[]> path = new ArrayList<>();
        for (int st = goal; st >= 0; st = prev[st]) path.add(new int[]{(st / 4) % width, (st / 4) / width});
        Collections.reverse(path);
        return path;
    }

    /** 가운데 칸 둘레 3×3 을 통로 바닥으로 판다. 이어진 두 방 그림 안에서는 그림의 바닥이 아닌 칸만. */
    private void carve(Corridor c, int cx, int cy) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx + dx, y = cy + dy;
                if (!inBounds(x, y)) continue;
                int t = tile(x, y);
                if (t == CORRIDOR) continue;
                Room r = roomAt[idx(x, y)];
                if (r != null) {
                    if ((r != c.link.a && r != c.link.b) || r.scene == null || r.floor(x, y)) continue;
                } else if (t != VOID) {
                    continue;
                }
                set(x, y, CORRIDOR);
                corridorAt[idx(x, y)] = c;
                c.tiles.add(new int[]{x, y});
            }
        }
    }

    /** 이 칸이 속한 통로 (바닥 또는 그 벽). 없으면 null. */
    public Corridor corridorAt(int x, int y) {
        return inBounds(x, y) ? corridorAt[idx(x, y)] : null;
    }

    /** 바닥 가장자리도 정확히 찾는다. 칸 가운데가 벽이어도 그 칸 일부는 길일 수 있다. */
    public Corridor corridorAtWorld(float x, float y) {
        var joint=jointAt(x,y);
        if(joint!=null)return joint.allowsFoot(x,y,0)?jointDoors.get(joint).link.corridor:null;
        for (Corridor c : corridors) {
            if (c.scene != null && x >= c.originX && x <= c.originX + c.widthPx
                    && y >= c.originY && y <= c.originY + c.heightPx && c.allowsFoot(x, y, 0)) return c;
        }
        Corridor c = corridorAt((int) Math.floor(x / tileSize), (int) Math.floor(y / tileSize));
        return c != null && c.scene == null ? c : null;
    }

    /** 통로 바닥 칸인가. */
    public boolean corridor(int x, int y) {
        return tile(x, y) == CORRIDOR;
    }

    private void buildCampProps() {
        Room camp = byId.get("CAMP");
        if (camp.scene != null) {
            for (RoomSceneLayout.Facility f : camp.scene.facilities) {
                Prop p = new Prop(f.kind, camp.sceneX(f.center[0]), camp.sceneY(f.center[1]),
                        f.widthTiles * tileSize, f.heightTiles * tileSize, tileSize, f.baked);
                if (f.sleepAt != null) {
                    p.sleepX = camp.sceneX(f.sleepAt[0]);
                    p.sleepY = camp.sceneY(f.sleepAt[1]);
                }
                props.add(p);
            }
            // Visible solid bases are already traced in scene.blocked; do not expand to whole tiles.
            return;
        }
        // 캠프 가운데 모닥불 가까이에 둬서 들어오자마자 한 화면에 보이게 한다
        int cy = (int) camp.centerTileY();
        props.add(new Prop(Prop.Kind.FORGE, camp.x0 + 5, cy + 1, 2, 2));
        props.add(new Prop(Prop.Kind.BED, camp.x1 - 6, cy + 1, 2, 2));
        props.add(new Prop(Prop.Kind.CAMPFIRE, (int) camp.centerTileX(), (int) camp.centerTileY() - 1, 1, 1));
        // 캠프 아래쪽 바닥의 봉인 문양: 세 보스의 힘을 모아 성검을 풀면 땅이 갈라져 심연(마왕)으로 떨어진다
        props.add(new Prop(Prop.Kind.SEAL, (int) camp.centerTileX() - 1, camp.y0 + 2, 2, 2));
        for (Prop p : props) {
            if (p.walkable()) continue;
            for (int x = p.tileX; x < p.tileX + p.w; x++)
                for (int y = p.tileY; y < p.tileY + p.h; y++) setBlocked(x, y, true);
        }
    }

    /** 던전 입구를 진행 상황에 맞춰 연다 (FR-던전-01, FR-맵-08). */
    public void updateGates(GameState s) {
        for (Link l : links) {
            if (l.gate == null) continue;
            l.gateClosed = !gateOpen(s, l.gate);
        }
    }

    public static boolean gateOpen(GameState s, String gate) {
        switch (gate) {
            case "LEFT": return true;
            case "RIGHT": return s.bossDefeated("LEFT");
            case "TOP": return s.bossDefeated("RIGHT");
            case "FINAL": return s.unsealed; // 캠프 아래 계단 ↔ 심연
            default: return false;
        }
    }

    private int idx(int x, int y) {
        return x + y * width;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void set(int x, int y, int t) {
        if (inBounds(x, y)) tiles[idx(x, y)] = t;
    }

    public int tile(int x, int y) {
        return inBounds(x, y) ? tiles[idx(x, y)] : VOID;
    }

    public Door door(int x, int y) {
        return inBounds(x, y) ? doorAt[idx(x, y)] : null;
    }

    public Room roomAt(int x, int y) {
        return inBounds(x, y) ? roomAt[idx(x, y)] : null;
    }

    public Room room(String id) {
        return byId.get(id);
    }

    public void setBlocked(int x, int y, boolean b) {
        if (inBounds(x, y)) blocked[idx(x, y)] = b;
    }

    public boolean isBlockedOverlay(int x, int y) {
        return inBounds(x, y) && blocked[idx(x, y)];
    }

    /**
     * 지나갈 수 없는 칸인가 (벽, 빈 곳, 닫힌 문, 광맥·시설). 투사체·바닥 공격처럼 칸 단위로 보는 곳에서 쓴다.
     * 그림 방은 칸 가운데가 바닥 경계 안이면 바닥이다.
     */
    public boolean solid(int x, int y) {
        var joint=jointAt((x+.5f)*tileSize,(y+.5f)*tileSize);
        if(joint!=null) {
            Door d=jointDoors.get(joint);
            return !joint.allowsFoot((x+.5f)*tileSize,(y+.5f)*tileSize,0)
                    || (d.isClosed() && d.blocks((x+.5f)*tileSize,(y+.5f)*tileSize,0)) || isBlockedOverlay(x,y);
        }
        int t = tile(x, y);
        if (t == VOID || t == WALL) return true;
        if (t == DOOR && doorAt[idx(x, y)].isClosed()) return true;
        if (t == CORRIDOR) return corridorAt[idx(x, y)].closed();
        return blocked[idx(x, y)];
    }

    /**
     * 발 사각형(가로 반 hw, 세로 반 hh)이 이 자리에 설 수 있는가.
     * 그림 방에서는 바닥 경계 다각형으로 정확히 보고, 닫힌 출구와 광맥 칸도 막는다.
     */
    public boolean free(float x, float y, float hw, float hh) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(hw) || !Float.isFinite(hh) || hw < 0 || hh < 0) return false;
        Room doorRoom = roomAt((int)Math.floor(x / tileSize), (int)Math.floor(y / tileSize));
        if (doorRoom != null && doorRoom.scene != null && doorRoom.scene.returnDoor != null) {
            float dx = Math.abs(x - doorRoom.sceneX(doorRoom.scene.returnDoor[0]));
            float dy = y - doorRoom.sceneY(doorRoom.scene.returnDoor[1]);
            // Only stone feet and the closed barrier obstruct movement, never the tall arch silhouette.
            if (Math.abs(dx - 55) < 15 + hw && Math.abs(dy - 12) < 8 + hh) return false;
            if (!returnDoorOpen.test(doorRoom) && dx < 39 + hw && Math.abs(dy - 14) < 3 + hh) return false;
        }
        if (def.passageScenes != null && !def.passageScenes.isEmpty()) return paintedFree(x, y, hw, hh);
        int T = tileSize;
        int tx0 = (int) Math.floor((x - hw) / T), tx1 = (int) Math.floor((x + hw - 0.01f) / T);
        int ty0 = (int) Math.floor((y - hh) / T), ty1 = (int) Math.floor((y + hh - 0.01f) / T);
        Room r = roomAt((int) Math.floor(x / T), (int) Math.floor(y / T));
        if (r != null && r.scene != null) {
            float radius = Math.max(hw, hh);
            if (!r.allowsFoot(x, y, radius) && !corridorFoot(r, tx0, ty0, tx1, ty1)) return false;
            for (int ty = ty0; ty <= ty1; ty++)
                for (int tx = tx0; tx <= tx1; tx++)
                    if (isBlockedOverlay(tx, ty)) return false;
            for (Door d : r.doors) if (d.isClosed() && d.blocks(x, y, radius)) return false;
            return true;
        }
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++)
                if (solid(tx, ty)) return false;
        return true;
    }

    private boolean paintedFree(float x, float y, float hw, float hh) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(hw) || !Float.isFinite(hh) || hw < 0 || hh < 0) return false;
        float radius = Math.max(hw, hh);
        int tx0 = (int) Math.floor((x - hw) / tileSize), tx1 = (int) Math.floor((x + hw) / tileSize);
        int ty0 = (int) Math.floor((y - hh) / tileSize), ty1 = (int) Math.floor((y + hh) / tileSize);
        for (int ty = ty0; ty <= ty1; ty++) for (int tx = tx0; tx <= tx1; tx++)
            if (isBlockedOverlay(tx, ty)) return false;
        Room r = roomAt((int) Math.floor(x / tileSize), (int) Math.floor(y / tileSize));
        if (r != null) for (Door d : r.doors) if (d.isClosed() && d.blocks(x, y, radius)) return false;
        var joint=jointAt(x,y);
        if(joint!=null) {
            Door door=jointDoors.get(joint);
            if(door.isClosed() && door.blocks(x,y,radius))return false;
            if(door.link.corridor.closed() && (r!=door.room || door.along(x,y)>=0))return false;
            return joint.allowsFoot(x,y,radius);
        }
        // The soft edge is a blend of two floors. Do not switch back to an old
        // wall while the new paving is still dominant, or close an existing
        // route early by treating the entire feather strip as opaque artwork.
        // Camp facility footprints remain governed by the original camp scene.
        if(r==null || r.type!=RoomType.CAMP)for(int i=paintedJoints.size()-1;i>=0;i--) {
            var edge=paintedJoints.get(i);
            if(edge.alpha(x,y)<.5f)continue;
            // A dominant repaint can also replace old floor with rock. Falling
            // through to a room/corridor below would expose an isolated floor island.
            if(!edge.allowsFoot(x,y,0))return false;
            Door door=jointDoors.get(edge);
            if(!door.link.corridor.closed() && edge.allowsFoot(x,y,radius))return true;
            break; // Respect the uppermost visible painting, not an older floor underneath.
        }
        if (r != null && r.allowsFoot(x, y, radius)) return true;
        for (Corridor c : corridors) {
            if (x + radius < c.originX || x - radius > c.originX + c.widthPx
                    || y + radius < c.originY || y - radius > c.originY + c.heightPx || c.closed()) continue;
            if (c.allowsFoot(x, y, radius)) return true;
            // At a doorway the foot straddles two polygons. Check their union only
            // in that small overlap; never let the coarse minimap cells open a wall.
            Door d = null;
            for (Door end : new Door[]{c.link.doorA, c.link.doorB})
                if (Math.hypot(x - end.footX, y - end.footY) < 2 * tileSize) { d = end; break; }
            if (d == null || !onJoinedFloor(c, d.room, x, y)) continue;
            boolean fits = true;
            for (int i = 0; i < 32 && fits; i++) {
                double angle = i * Math.PI / 16;
                fits = onJoinedFloor(c, d.room, x + radius * (float) Math.cos(angle), y + radius * (float) Math.sin(angle));
            }
            if (fits) return true;
        }
        return false;
    }

    private static boolean onJoinedFloor(Corridor c, Room r, float x, float y) {
        return r.allowsFoot(x, y, 0) || c.allowsFoot(x, y, 0);
    }

    /**
     * 그림 방 출구와 통로가 만나는 곳: 발이 통로 칸이나 그림 바닥 칸에만 걸치고, 바로 곁에 통로 칸이 있다.
     * (그림 바닥 경계는 출구에서 끝나므로 경계 바로 안쪽 칸에서도 통로로 넘어갈 수 있어야 한다.)
     */
    private boolean corridorFoot(Room r, int tx0, int ty0, int tx1, int ty1) {
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                if (tile(tx, ty) == CORRIDOR) {
                    if (corridorAt[idx(tx, ty)].closed()) return false;
                } else if (!r.floor(tx, ty)) {
                    return false;
                }
            }
        }
        for (int ty = ty0 - 1; ty <= ty1 + 1; ty++)
            for (int tx = tx0 - 1; tx <= tx1 + 1; tx++)
                if (tile(tx, ty) == CORRIDOR && !corridorAt[idx(tx, ty)].closed()) return true;
        return false;
    }

    public Prop propAt(int tx, int ty) {
        for (Prop p : props) if (p.covers(tx, ty)) return p;
        return null;
    }
}
