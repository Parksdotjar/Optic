package dev.optic.encoding;
import dev.optic.config.*;
import dev.optic.recording.OverloadGuard;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedTest {
    @Test void threeWindowsRequiredAndHealthyWindowResets() {
        var guard=new OverloadGuard();
        assertFalse(guard.sample(1,0,0)); assertFalse(guard.sample(5_000_000_001L,100,20)); assertFalse(guard.sample(10_000_000_001L,200,40));
        assertFalse(guard.sample(15_000_000_001L,300,40));
        assertFalse(guard.sample(20_000_000_001L,400,60)); assertFalse(guard.sample(25_000_000_001L,500,80)); assertTrue(guard.sample(30_000_000_001L,600,100));
    }
    @Test void defaultsPreserveLegacyAndNullConfigSafe() {
        var c=new RecorderConfig(); assertEquals(ColorMode.LEGACY,c.colorMode); assertFalse(c.stopOnSustainedDuplicates);
        c.encoder=null; c.colorMode=null; c.validate(); assertEquals("auto",c.encoder); assertEquals(ColorMode.LEGACY,c.colorMode);
        c.encoder="hevc_nvenc"; c.validate(); assertEquals("hevc_nvenc",c.encoder);
    }
    @Test void advancedOptionsRemainEightBitAndNoBFrames() {
        for(String encoder:AdvancedEncoders.CANDIDATES) {
            var options=EncoderDetector.options(encoder,60); assertEquals("yuv420p",options.get(options.indexOf("-pix_fmt")+1)); assertEquals("0",options.get(options.indexOf("-bf")+1));
        }
        assertTrue(ColorMode.SDR_SRGB.options().contains("iec61966-2-1"));
    }
}
