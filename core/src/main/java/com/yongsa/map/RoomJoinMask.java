package com.yongsa.map;

import com.yongsa.map.Corridor;
import com.yongsa.map.Door;
import com.yongsa.map.Room;

/** Alpha for a room painting and its foreground, in world coordinates.
 * Door frames keep their silhouettes; only the terrain beyond them blends away. */
final class RoomJoinMask {
    private final Room room;
    private final Door[] doors;
    private final Door[] transitions;
    private final float tile, fade;

    RoomJoinMask(Room room, float tile) {
        this.room = room;
        this.tile = tile;
        this.fade = Math.min(room.widthPx, room.heightPx) * .08f;
        this.doors = room.doors.stream().filter(d -> d.link != null && d.link.corridor != null)
                .toArray(Door[]::new);
        this.transitions = room.doors.stream().filter(d -> d.link != null && d.link.transition)
                .toArray(Door[]::new);
    }

    float alpha(float x, float y) {
        float left = fade, right = fade, top = fade, bottom = fade;
        for (Door d : doors) {
            float across = d.horizontal() ? Math.abs(x - d.footX) : Math.abs(y - d.footY);
            float protect = 1 - smooth((across - d.halfWidth - tile) / tile);
            // A broad edge fade erased the lintel of doors painted near the image
            // border. Keep a short, continuous fade here instead of a hard cut.
            float narrow = fade + (tile * .25f - fade) * protect;
            switch (d.side) {
                case LEFT: if (d.footX - room.originX < 4 * tile) left = Math.min(left, narrow); break;
                case RIGHT: if (room.originX + room.widthPx - d.footX < 4 * tile) right = Math.min(right, narrow); break;
                case UP: if (room.originY + room.heightPx - d.footY < 4 * tile) top = Math.min(top, narrow); break;
                case DOWN: if (d.footY - room.originY < 4 * tile) bottom = Math.min(bottom, narrow); break;
            }
        }
        for (Door d : transitions) {
            // A cave warp has no outdoor corridor, but its painted arch still needs protection.
            // Use the artwork edge, not travel direction: camp side arches face into the screen.
            float vertical = Math.max(d.footY - y, y - d.footY - 4 * tile);
            float sideFade = protectedFade(vertical, tile);
            float endFade = protectedFade(Math.abs(x - d.footX), d.halfWidth + tile);
            if (d.footX - room.originX < 4 * tile) left = Math.min(left, sideFade);
            if (room.originX + room.widthPx - d.footX < 4 * tile) right = Math.min(right, sideFade);
            if (room.originY + room.heightPx - d.footY < 4 * tile) top = Math.min(top, endFade);
            if (d.footY - room.originY < 4 * tile) bottom = Math.min(bottom, endFade);
        }
        float alpha = smooth((x - room.originX) / left) * smooth((room.originX + room.widthPx - x) / right)
                * smooth((y - room.originY) / bottom) * smooth((room.originY + room.heightPx - y) / top);
        for (Door d : doors) alpha *= 1 - replacement(d, x, y, tile);
        return alpha;
    }

    private float protectedFade(float distance, float extent) {
        float protect = 1 - smooth((distance - extent) / tile);
        return fade + (tile * .25f - fade) * protect;
    }

    static float replacement(Door d, float x, float y, float tile) {
        Corridor c = d.link.corridor;
        float edge = Math.min(Math.min(x - c.originX, c.originX + c.widthPx - x),
                Math.min(y - c.originY, c.originY + c.heightPx - y));
        if (edge <= 0) return 0;
        // The threshold is the walking anchor, not the top of the door frame.
        // Preserve the frame and stairs on both sides of that anchor.
        float along = smooth((d.along(x, y) - 2 * tile) / (1.8f * tile));
        float side = smooth((d.halfWidth + 2.5f * tile - d.across(x, y)) / (1.2f * tile));
        // All orientations need this, not just diagonal doors. Otherwise a
        // passage rectangle cuts through timber, lamps or floor in a straight line.
        return along * side * smooth(edge / (1.2f * tile));
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }
}
