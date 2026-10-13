package dev.optic.audio;

/** Smooth a device sample clock against monotonic host time; reject scheduling jitter. */
public final class AudioClock {
    private long start, samples;
    private double rate=48000;
    public void reset(long now) { start=now; samples=0; rate=48000; }
    public double rate() { return rate; }
    public long timestamp() { return start+Math.round(samples*1e9/rate); }
    public void advance(int frames,long observedEnd) {
        samples+=frames;
        double seconds=(observedEnd-start)/1e9;
        if(seconds>2) {
            double observed=samples/seconds;
            if(observed>47520 && observed<48480) rate+=Math.max(-.1,Math.min(.1,(observed-rate)*.005));
        }
    }
}
