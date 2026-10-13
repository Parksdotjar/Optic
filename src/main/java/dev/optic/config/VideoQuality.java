package dev.optic.config;

/** Encoder-specific quantizers are conveniences, not perceptually identical quality across hardware. */
public enum VideoQuality {
    COMPACT(28,"Compact"), STANDARD(23,"Standard"), HIGH(19,"High"), VERY_HIGH(16,"Very high");
    public final int quantizer;
    public final String label;
    VideoQuality(int quantizer,String label) { this.quantizer=quantizer; this.label=label; }
}
