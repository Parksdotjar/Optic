package dev.optic.recording;

import java.util.concurrent.atomic.LongAdder;

public final class RecordingMetrics {
    public final LongAdder submitted = new LongAdder(), dropped = new LongAdder(), encoded = new LongAdder(), duplicated = new LongAdder();
    public final LongAdder scaleCopies=new LongAdder(),scaleClears=new LongAdder();
    public final LongAdder captureNanos = new LongAdder(), readbackNanos = new LongAdder(), hookNanos = new LongAdder(), hooks = new LongAdder();
    public final LongAdder transferLatencyNanos=new LongAdder(), completedTransfers=new LongAdder();
    public volatile int queueDepth;
    public final TimingHistogram captureTime=new TimingHistogram(), hookTime=new TimingHistogram(), frameIntervals=new TimingHistogram();
    public final TimingHistogram pollTime=new TimingHistogram();
    public final java.util.concurrent.atomic.LongAccumulator queueHighWater=new java.util.concurrent.atomic.LongAccumulator(Long::max,0);
    public volatile long encoderFrames=-1;
    public volatile double writeBytesPerSecond;
    public volatile long outputBytes;
    public void queue(int size) { queueDepth=size; queueHighWater.accumulate(size); }
    public String report(double seconds, long bytes) {
        return String.format(java.util.Locale.ROOT,
            "capture_avg_ms=%.3f readback_copy_avg_ms=%.3f transfer_observed_avg_ms=%.3f hook_avg_ms=%.3f queue=%d dropped=%d duplicated=%d accepted_fps=%.2f bitrate_mbps=%.2f",
            captureNanos.sum()/1e6/Math.max(1,submitted.sum()), readbackNanos.sum()/1e6/Math.max(1,submitted.sum()),
            transferLatencyNanos.sum()/1e6/Math.max(1,completedTransfers.sum()),
            hookNanos.sum()/1e6/Math.max(1,hooks.sum()), queueDepth, dropped.sum(), duplicated.sum(), encoded.sum()/Math.max(.001,seconds), bytes*8/1e6/Math.max(.001,seconds))
            + String.format(java.util.Locale.ROOT," capture_max_ms=%.3f capture_p95_ms=%.3f hook_p95_ms=%.3f hook_p99_ms=%.3f frame_p50_ms=%.3f frame_p95_ms=%.3f frame_p99_ms=%.3f one_percent_low_fps=%.2f queue_high_water=%d encoder_frames=%d encoder_fps=%.2f write_mib_s=%.2f",
            captureTime.maxMs(),captureTime.percentileMs(.95),hookTime.percentileMs(.95),hookTime.percentileMs(.99),frameIntervals.percentileMs(.5),frameIntervals.percentileMs(.95),frameIntervals.percentileMs(.99),frameIntervals.onePercentLowFps(),queueHighWater.get(),encoderFrames,encoderFrames<0?-1:encoderFrames/Math.max(.001,seconds),writeBytesPerSecond/1024/1024)
            +" poll_p95_ms="+pollTime.percentileMs(.95)+" scale_copies="+scaleCopies.sum()+" scale_clears="+scaleClears.sum()+" unique_fps="+String.format(java.util.Locale.ROOT,"%.2f",Math.max(0,encoded.sum()-duplicated.sum())/Math.max(.001,seconds));
    }
}
