package dev.optic.config;
import java.util.List;
/** The 8-bit display RGB source is SDR. Metadata never implies HDR or extra precision. */
public enum ColorMode {
    LEGACY("Existing conversion"), SDR_SRGB("SDR — explicit sRGB / BT.709");
    public final String label;
    ColorMode(String label) { this.label=label; }
    public List<String> options() {
        return this==LEGACY?List.of():List.of("-vf","scale=in_range=full:out_range=limited:out_color_matrix=bt709,format=yuv420p,setparams=range=limited:color_primaries=bt709:color_trc=iec61966-2-1:colorspace=bt709","-color_range","tv","-colorspace","bt709","-color_primaries","bt709","-color_trc","iec61966-2-1");
    }
}
