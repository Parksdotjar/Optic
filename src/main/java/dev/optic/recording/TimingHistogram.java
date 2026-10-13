package dev.optic.recording;

import java.util.concurrent.atomic.*;

/** Fixed storage, 0.1 ms bins up to 1 s. No sample-sized allocation or growing history. */
public final class TimingHistogram {
    private final AtomicLongArray buckets = new AtomicLongArray(10_001);
    private final LongAdder count = new LongAdder(), total = new LongAdder();
    private final LongAccumulator max = new LongAccumulator(Long::max, 0);
    public void add(long nanos) {
        nanos = Math.max(0, nanos); count.increment(); total.add(nanos); max.accumulate(nanos);
        buckets.incrementAndGet((int)Math.min(10_000, nanos / 100_000));
    }
    public double averageMs() { return total.sum() / 1e6 / Math.max(1, count.sum()); }
    public double maxMs() { return max.get() / 1e6; }
    public double percentileMs(double percentile) {
        long n = count.sum(); if (n == 0) return 0;
        long target = Math.max(1, (long)Math.ceil(n * percentile)), sum = 0;
        for (int i = 0; i < buckets.length(); i++) { sum += buckets.get(i); if (sum >= target) return (i + 1) / 10.0; }
        return maxMs();
    }
    /** FPS from the mean of the slowest 1% of frame intervals (histogram approximation). */
    public double onePercentLowFps() {
        long wanted = Math.max(1, (long)Math.ceil(count.sum() * .01)), selected = 0; double ms = 0;
        for (int i = buckets.length() - 1; i >= 0 && selected < wanted; i--) {
            long take = Math.min(buckets.get(i), wanted - selected); selected += take; ms += take * (i + 1) / 10.0;
        }
        return ms == 0 ? 0 : 1000 * selected / ms;
    }
}
