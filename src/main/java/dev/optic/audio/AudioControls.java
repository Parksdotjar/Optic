package dev.optic.audio;

/** Small, shared controls and decimated diagnostics; UI never touches sample buffers. */
public final class AudioControls {
    public volatile double gain = 1;
    public volatile boolean muted;
    public volatile double peak;
    public volatile long clippingUntil, droppedFrames, reconnects;
    public volatile String status = "Idle";
    public void configure(double gain, boolean muted) { this.gain=Math.max(0,Math.min(4,gain)); this.muted=muted; }
    public void reset() { peak=0; clippingUntil=0; droppedFrames=0; reconnects=0; status="Idle"; }
}
