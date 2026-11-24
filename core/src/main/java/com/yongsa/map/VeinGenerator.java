package com.yongsa.map;

import com.yongsa.model.OreType;
import com.yongsa.model.Vein;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 광물방의 광맥을 무작위로 놓는다 (FR-채집-03, 04, 09).
 * 그림 광물방은 그림 속 광석 덩어리 앞에만 놓는다 ({@link #generateAtSpots}).
 * 자리가 정해지지 않은 방은 후보 칸(벽에서 한 칸, 문 앞 두 칸을 비운 바닥) 중에서 뽑고,
 * 광맥끼리 붙지 않게 한다. 뽑은 뒤 입구에서 넓이 우선 탐색(BFS)으로
 * 모든 문과 모든 광맥에 닿는지 검사하고, 막히면 다시 뽑는다.
 */
public final class VeinGenerator {
    private VeinGenerator() {
    }

    public static List<Vein> generate(Room room, Random rnd, int count, OreType ore,
                                      int minAmount, int maxAmount, int hits, int guarantee) {
        List<int[]> candidates = candidates(room);
        count = Math.min(count, candidates.size());
        for (int attempt = 0; attempt < 400; attempt++) {
            // 작은 방이라 다 못 놓으면 조금씩 줄인다
            if (attempt > 0 && attempt % 60 == 0 && count > 1) count--;
            Collections.shuffle(candidates, rnd);
            List<int[]> picked = new ArrayList<>();
            for (int[] c : candidates) {
                if (picked.size() >= count) break;
                if (touches(picked, c)) continue;
                picked.add(c);
            }
            if (picked.size() < count) continue;
            if (!reachable(room, picked)) continue;
            List<Vein> veins = new ArrayList<>();
            int total = 0;
            for (int[] p : picked) {
                int amount = minAmount + rnd.nextInt(maxAmount - minAmount + 1);
                total += amount;
                veins.add(new Vein(p[0], p[1], ore, amount, hits));
            }
            // 첫 광물방 보장: 합계가 모자라면 아직 최대가 아닌 광맥을 하나씩 올린다
            while (total < guarantee) {
                List<Vein> low = new ArrayList<>();
                for (Vein v : veins) if (v.amount < maxAmount) low.add(v);
                if (low.isEmpty()) break;
                low.get(rnd.nextInt(low.size())).amount++;
                total++;
            }
            return veins;
        }
        throw new IllegalStateException("광맥을 놓을 자리가 없습니다: " + room.id);
    }

    /**
     * 그림 광물방: 광맥은 그림 속 광석 덩어리(scene.oreSpots) 바로 앞 바닥에만 놓는다.
     * 자리 중 count 곳만 골라 total 개를 나눠 담는다. 광석 덩어리는 그림에 있으니 칸을 막지 않는다.
     */
    public static List<Vein> generateAtSpots(Room room, Random rnd, int count, OreType ore,
                                             int total, int hits, int tileSize) {
        List<float[]> spots = new ArrayList<>(room.scene.oreSpots);
        Collections.shuffle(spots, rnd);
        List<Vein> veins = new ArrayList<>();
        List<int[]> used = new ArrayList<>();
        for (float[] spot : spots) {
            if (veins.size() >= count) break;
            float wx = room.sceneX(spot[0]), wy = room.sceneY(spot[1]);
            int[] best = null;
            float bestD = 5.5f * tileSize; // 광석 덩어리에서 너무 먼 바닥이면 그 자리는 쓰지 않는다
            for (int y = room.y0; y <= room.y1; y++) {
                for (int x = room.x0; x <= room.x1; x++) {
                    if (!room.inside(x, y) || nearDoor(room, x, y)) continue;
                    int[] c = {x, y};
                    if (touches(used, c)) continue;
                    float d = (float) Math.hypot((x + 0.5f) * tileSize - wx, (y + 0.5f) * tileSize - wy);
                    if (d < bestD) {
                        bestD = d;
                        best = c;
                    }
                }
            }
            if (best == null) continue;
            used.add(best);
            Vein v = new Vein(best[0], best[1], ore, 0, hits);
            v.spotX = wx;
            v.spotY = wy;
            veins.add(v);
        }
        spread(veins, total, rnd);
        return veins;
    }

    /** total 개를 광맥에 고르게 나눈다 (나머지는 무작위로 한 개씩). */
    public static void spread(List<Vein> veins, int total, Random rnd) {
        if (veins.isEmpty()) return;
        for (Vein v : veins) v.amount = total / veins.size();
        List<Vein> order = new ArrayList<>(veins);
        Collections.shuffle(order, rnd);
        for (int i = 0; i < total % veins.size(); i++) order.get(i).amount++;
    }

    /** 바닥 경계에서 한 칸 떨어지고, 출구 앞 두 칸을 비운 바닥 칸. */
    static List<int[]> candidates(Room room) {
        List<int[]> out = new ArrayList<>();
        for (int y = room.y0 + 1; y <= room.y1 - 1; y++) {
            for (int x = room.x0 + 1; x <= room.x1 - 1; x++) {
                if (!room.insideWithMargin(x, y, 1)) continue;
                if (nearDoor(room, x, y)) continue;
                out.add(new int[]{x, y});
            }
        }
        return out;
    }

    private static boolean nearDoor(Room room, int x, int y) {
        for (Door d : room.doors) {
            if (Math.abs(d.insideX - x) <= 2 && Math.abs(d.insideY - y) <= 2) return true;
            if (Math.abs(d.tilesX[0] - x) <= 2 && Math.abs(d.tilesY[0] - y) <= 2) return true;
        }
        return false;
    }

    private static boolean touches(List<int[]> picked, int[] c) {
        for (int[] p : picked) {
            if (Math.abs(p[0] - c[0]) <= 1 && Math.abs(p[1] - c[1]) <= 1) return true;
        }
        return false;
    }

    /** 광맥을 막힌 칸으로 보고, 첫 문 안쪽에서 시작해 이어진 모든 문과 광맥 옆에 닿는지 본다. */
    public static boolean reachable(Room room, List<int[]> veins) {
        int w = room.x1 - room.x0 + 1, h = room.y1 - room.y0 + 1;
        boolean[] block = new boolean[w * h];
        for (int[] v : veins) block[(v[0] - room.x0) + (v[1] - room.y0) * w] = true;
        boolean[] seen = new boolean[w * h];
        List<int[]> starts = new ArrayList<>();
        for (Door d : room.doors) {
            if (d.dead()) continue;
            int[] s = nearestFloor(room, d.insideX, d.insideY);
            if (s != null) starts.add(s);
        }
        if (starts.isEmpty()) return true;
        int[] first = starts.get(0);
        if (block[(first[0] - room.x0) + (first[1] - room.y0) * w]) return false;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(first);
        seen[(first[0] - room.x0) + (first[1] - room.y0) * w] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!room.floor(nx, ny)) continue;
                int i = (nx - room.x0) + (ny - room.y0) * w;
                if (seen[i] || block[i]) continue;
                seen[i] = true;
                q.add(new int[]{nx, ny});
            }
        }
        for (int[] s : starts) {
            if (!seen[(s[0] - room.x0) + (s[1] - room.y0) * w]) return false;
        }
        for (int[] v : veins) {
            boolean ok = false;
            for (int[] d : dirs) {
                int nx = v[0] + d[0], ny = v[1] + d[1];
                if (room.floor(nx, ny) && seen[(nx - room.x0) + (ny - room.y0) * w]) ok = true;
            }
            if (!ok) return false;
        }
        return true;
    }

    /** 가장 가까운 바닥 칸 (그 칸이 바닥이면 그대로). */
    static int[] nearestFloor(Room room, int x, int y) {
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++)
                for (int dx = -r; dx <= r; dx++)
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == r && room.floor(x + dx, y + dy)) return new int[]{x + dx, y + dy};
        }
        return null;
    }
}
