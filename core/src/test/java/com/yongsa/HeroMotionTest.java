package com.yongsa;

import com.yongsa.combat.Facing;
import com.yongsa.gfx.HeroMotion;
import com.yongsa.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeroMotionTest {
    private GameSession session() {
        var b = TestData.balance();
        var g = new GameSession(b, TestData.map(), NewGame.create(b), 42);
        var camp = g.map.room("CAMP");
        // Choose a measured clear stretch, independent of props around the spawn.
        for (float y = camp.originY; y < camp.originY + camp.heightPx; y += 8)
            for (float x = camp.originX; x < camp.originX + camp.widthPx; x += 8) {
                boolean clear = true;
                for (int offset = 0; offset <= 112; offset += 4)
                    if (!g.map.free(x + offset, y, g.hero.hw, g.hero.hh)) { clear = false; break; }
                if (clear) { g.hero.x = x; g.hero.y = y; return g; }
            }
        throw new AssertionError("No clear test stretch");
    }

    @Test void rollDistanceDoesNotDependOnRenderFrameRate() {
        for (int fps : new int[]{30, 60, 144}) {
            var g = session();
            float x = g.hero.x;
            var roll = new Controls(); roll.moveX = 1; roll.roll = true;
            g.update(1f / fps, roll);
            assertTrue(g.hero.rolling());
            assertEquals(x, g.hero.x, .01f, "No extra walking step on the roll input frame");
            for (int i = 0; i < fps && g.hero.rolling(); i++) g.update(1f / fps, new Controls());
            assertFalse(g.hero.rolling());
            assertEquals(g.balance.attack.rollDistanceTiles * g.balance.tileSize, g.hero.x - x, .08f, "FPS " + fps);
        }
    }

    @Test void rollPoseUsesTravelDirectionAndCancelsMiningPose() {
        var g = session();
        g.hero.facing = Facing.LEFT; g.hero.aimX = -1; g.hero.aimY = 0;
        g.hero.mineSwingT = .22f;
        var input = new Controls(); input.moveX = 1; input.roll = true;
        g.update(1 / 60f, input);
        assertEquals(Facing.RIGHT, g.hero.rollFacing);
        assertEquals(Facing.RIGHT, g.hero.facing, "Keep the landing pose aligned with the roll");
        assertEquals(1, g.hero.aimX, .0001f, "The next unassisted attack must agree with the landing pose");
        assertEquals(0, g.hero.aimY, .0001f);
        assertEquals(0, g.hero.mineSwingT);
        assertFalse(g.hero.moving, "Walking must not keep cycling during a tumble");
    }

    @Test void facingHasEightDirections() {
        assertEquals(Facing.RIGHT, Facing.of(1, 0));
        assertEquals(Facing.UP_RIGHT, Facing.of(1, 1));
        assertEquals(Facing.UP, Facing.of(0, 1));
        assertEquals(Facing.UP_LEFT, Facing.of(-1, 1));
        assertEquals(Facing.LEFT, Facing.of(-1, 0));
        assertEquals(Facing.DOWN_LEFT, Facing.of(-1, -1));
        assertEquals(Facing.DOWN, Facing.of(0, -1));
        assertEquals(Facing.DOWN_RIGHT, Facing.of(1, -1));
        assertEquals(Facing.RIGHT, Facing.of(1, .3f), "Within 22.5 degrees stays straight");
        assertEquals(2, HeroMotion.row(Facing.UP_LEFT), "Diagonals reuse the side sprite row");
        assertTrue(Facing.DOWN_LEFT.leftward());
        assertFalse(Facing.UP_RIGHT.leftward());
    }

    @Test void diagonalInputFacesDiagonallyAtNormalSpeed() {
        var g = session();
        var input = new Controls(); input.moveX = 1; input.moveY = 1;
        g.update(1 / 60f, input);
        assertEquals(Facing.UP_RIGHT, g.hero.facing);
        float r = (float) Math.sqrt(.5);
        assertEquals(r, g.hero.aimX, .0001f);
        assertEquals(r, g.hero.aimY, .0001f);
    }

    @Test void blockedFeetDoNotContinueTheWalkCycle() {
        var g = session();
        var input = new Controls(); input.moveX = 1;
        for (int i = 0; i < 600 && (i == 0 || g.hero.moving); i++) g.update(1 / 60f, input);
        assertFalse(g.hero.moving, "The clear stretch eventually ends at a wall");
        float phase = g.hero.walkTime;
        g.update(1 / 60f, input);
        assertEquals(phase, g.hero.walkTime, .0001f);
    }

    @Test void attackMiningAndSkillRecoverWithoutSnappingToTheHeldSwordAngle() {
        for (Facing facing : Facing.values()) for (int kind = 0; kind < 3; kind++)
            for (float aim : new float[]{-180, -90, 0, 90, 180}) {
            float rest = HeroMotion.attachedSwordAngle(facing);
            assertEquals(rest, normalize(HeroMotion.swordAngle(rest, aim, 0, kind)), .001f);
            assertEquals(rest, normalize(HeroMotion.swordAngle(rest, aim, 1, kind)), .001f);
            for (float seam : new float[]{.18f, .65f}) {
                float before = HeroMotion.swordAngle(rest, aim, seam - .0001f, kind);
                float after = HeroMotion.swordAngle(rest, aim, seam + .0001f, kind);
                assertTrue(Math.abs(normalize(after - before + 180) - 180) < .1f);
            }
        }
    }

    private float normalize(float degrees) { return (degrees % 360 + 360) % 360; }
}
