package dev.optic.encoding;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EncoderDetectorTest {
    @Test void allCodecsUseCompatiblePixelFormatAndTwoSecondKeyframes() {
        for(String codec:java.util.List.of("h264_nvenc","h264_amf","h264_qsv","libx264")) {
            var args=EncoderDetector.options(codec,120);
            assertEquals("yuv420p",args.get(args.indexOf("-pix_fmt")+1)); assertEquals("240",args.get(args.indexOf("-g")+1));
        }
    }
}
