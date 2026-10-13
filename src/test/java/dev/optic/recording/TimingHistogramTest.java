package dev.optic.recording;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TimingHistogramTest {
    @Test void measuresTailInsteadOfOnlyAverageAndRemainsBoundedForLongStalls() {
        var histogram=new TimingHistogram();
        for(int i=0;i<99;i++) histogram.add(10_000_000);
        histogram.add(100_000_000);
        assertEquals(10.9,histogram.averageMs(),.01);
        assertEquals(100,histogram.maxMs(),.01);
        assertTrue(histogram.onePercentLowFps()<11);
        histogram.add(3_000_000_000L); assertEquals(3000,histogram.maxMs(),.01);
    }
}
