package com.provipvp.recon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R3: beobachtbare Drossel-Entscheidung — Tick-Abstand verhindert Over-Scan. */
class ChunkScanThrottleTest {

    @Test
    void allowsFirstScan() {
        ChunkScanThrottle throttle = new ChunkScanThrottle(null);
        assertTrue(throttle.allowScan(0));
    }

    @Test
    void deniesTooSoon() {
        ChunkScanThrottle t = new ChunkScanThrottle(null);
        t.recordScan(10);
        assertFalse(t.allowScan(11)); // <20 Ticks
    }

    @Test
    void allowsAfterCooldown() {
        ChunkScanThrottle t = new ChunkScanThrottle(null);
        t.recordScan(10);
        assertTrue(t.allowScan(30));
    }
}
