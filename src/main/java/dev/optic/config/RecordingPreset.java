package dev.optic.config;

/** Video-only conveniences. Never change audio sources, visibility choices, paths or naming. */
public enum RecordingPreset {
    PERFORMANCE("Performance",RecorderConfig.Resolution.P1080,30,VideoQuality.COMPACT),
    BALANCED("Balanced",RecorderConfig.Resolution.P1080,60,VideoQuality.STANDARD),
    CREATOR("Creator",RecorderConfig.Resolution.P1440,60,VideoQuality.HIGH),
    CINEMATIC("Cinematic",RecorderConfig.Resolution.P1440,30,VideoQuality.VERY_HIGH),
    CUSTOM("Custom",null,0,null);
    public final String label;
    private final RecorderConfig.Resolution resolution;
    private final int fps;
    private final VideoQuality quality;
    RecordingPreset(String label,RecorderConfig.Resolution resolution,int fps,VideoQuality quality) { this.label=label; this.resolution=resolution; this.fps=fps; this.quality=quality; }
    public void apply(RecorderConfig c) {
        c.preset=this;
        if(this==CUSTOM) return;
        c.resolution=resolution; c.fps=fps; c.quality=quality; c.encoder="auto"; c.aspect=dev.optic.capture.ScaleLayout.Aspect.FIT;
    }
    public boolean matches(RecorderConfig c) { return this==CUSTOM || (c.resolution==resolution && c.fps==fps && c.quality==quality && "auto".equals(c.encoder) && c.aspect==dev.optic.capture.ScaleLayout.Aspect.FIT); }
}
