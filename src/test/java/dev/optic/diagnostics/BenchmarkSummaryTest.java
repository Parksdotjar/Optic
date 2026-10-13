package dev.optic.diagnostics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BenchmarkSummaryTest {
    @Test void tailsAndLowFpsUseSlowestFramesNotAverage() {
        long[] trace=new long[100]; java.util.Arrays.fill(trace,10_000_000); trace[99]=100_000_000;
        var s=BenchmarkSummary.summarize(trace);
        assertEquals(10,s.low1Fps(),.001); assertEquals(10,s.p99Ms(),.001); assertEquals(1,s.stutters()); assertEquals(1.09,s.seconds(),.001);
    }
    @Test void rejectsInvalidAndTooShortTraces() {
        assertThrows(IllegalArgumentException.class,() -> BenchmarkSummary.summarize(new long[]{1}));
        assertThrows(IllegalArgumentException.class,() -> BenchmarkSummary.summarize(new long[]{1,0}));
        assertThrows(IllegalArgumentException.class,() -> BenchmarkSummary.summarize(new long[]{1,60_000_000_001L}));
    }
}
