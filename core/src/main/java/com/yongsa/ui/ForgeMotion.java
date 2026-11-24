package com.yongsa.ui;

import com.yongsa.model.EnhanceService.Outcome;

/** Presentation clock only. The model still rolls, charges and saves exactly once. */
public final class ForgeMotion {
    public enum Phase { IDLE, HAMMER, RESULT }
    private Phase phase = Phase.IDLE;
    private float elapsed, duration;
    private Outcome outcome;
    private boolean awakened;

    public void start(float seconds) {
        if (busy()) throw new IllegalStateException("Forge is already working");
        phase = Phase.HAMMER;
        elapsed = 0;
        duration = Math.max(0.01f, seconds);
        outcome = null;
        awakened = false;
    }

    /** True once when the final strike is reached, including a long/dropped frame. */
    public boolean advance(float delta) {
        if (!busy()) return false;
        elapsed += Math.max(0, delta);
        if (elapsed < duration) return false;
        elapsed = 0;
        if (phase == Phase.HAMMER) {
            phase = Phase.RESULT;
            return true;
        }
        phase = Phase.IDLE;
        return false;
    }

    public void result(Outcome actualOutcome, boolean actualAwakening, float seconds) {
        if (phase != Phase.RESULT) throw new IllegalStateException("Result needs a completed strike");
        outcome = actualOutcome;
        awakened = actualAwakening;
        duration = Math.max(0.01f, seconds);
        elapsed = 0;
    }

    public boolean busy() { return phase != Phase.IDLE; }
    public void clearResult() { if (!busy()) { outcome = null; awakened = false; } }
    public Phase phase() { return phase; }
    public Outcome outcome() { return outcome; }
    public boolean awakened() { return awakened; }
    public float elapsed() { return elapsed; }
    public float progress() { return Math.min(1, elapsed / Math.max(duration, 0.01f)); }
    public int strikes() { return phase == Phase.HAMMER ? Math.min(3, (int)(progress() * 3 + 0.3f)) : 3; }

    public int pose() {
        if (phase == Phase.HAMMER) {
            float beat = (progress() * 3) % 1;
            return beat < 0.16f ? 0 : beat < 0.7f ? 1 : beat < 0.88f ? 2 : 3;
        }
        if (outcome == null) return 0;
        if (outcome == Outcome.SUCCESS) return 4;
        if (outcome == Outcome.DESTROY && phase == Phase.RESULT && progress() < 0.25f) return 3;
        return 5;
    }
}
