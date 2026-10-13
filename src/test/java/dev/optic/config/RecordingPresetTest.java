package dev.optic.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecordingPresetTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void preV5ConfigurationKeepsExistingRecordingPreferences() throws Exception {
        var file=directory.resolve("optic.json");
        java.nio.file.Files.writeString(file,"{\"schemaVersion\":3,\"resolution\":\"NATIVE\",\"fps\":120,\"encoder\":\"libx264\",\"microphone\":true,\"captureChat\":false,\"recordingTitle\":\"Bounty\"}");
        var c=RecorderConfig.load(file);
        assertEquals(RecordingPreset.CUSTOM,c.preset); assertEquals(VideoQuality.STANDARD,c.quality);
        assertEquals(RecorderConfig.Resolution.NATIVE,c.resolution); assertEquals(120,c.fps); assertEquals("libx264",c.encoder);
        assertTrue(c.microphone); assertFalse(c.captureChat); assertEquals("Bounty",c.recordingTitle);
    }
    @Test void videoPreferencesSurviveSaveAndReload() {
        var c=new RecorderConfig(); RecordingPreset.CREATOR.apply(c); var file=directory.resolve("optic.json");
        c.save(file); var loaded=RecorderConfig.load(file);
        assertEquals(RecordingPreset.CREATOR,loaded.preset); assertEquals(VideoQuality.HIGH,loaded.quality);
        loaded.aspect=dev.optic.capture.ScaleLayout.Aspect.CROP; loaded.save(file);
        var custom=RecorderConfig.load(file); assertEquals(RecordingPreset.CUSTOM,custom.preset);
        assertEquals(dev.optic.capture.ScaleLayout.Aspect.CROP,custom.aspect); assertEquals(VideoQuality.HIGH,custom.quality);
    }
    @Test void presetsOnlyChangeVideoAndResolveConsistently() {
        for(var p:RecordingPreset.values()) {
            var c=new RecorderConfig(); c.microphone=true; c.captureChat=false; c.recordingTitle="Keep";
            p.apply(c); assertTrue(p.matches(c)); assertEquals(p,c.preset);
            assertTrue(c.microphone); assertFalse(c.captureChat); assertEquals("Keep",c.recordingTitle);
        }
    }
    @Test void mismatchedOrUnknownPresetBecomesCustomWithoutResettingUserChoices() {
        var c=new RecorderConfig(); RecordingPreset.CREATOR.apply(c); c.fps=120; c.validate();
        assertEquals(RecordingPreset.CUSTOM,c.preset); assertEquals(120,c.fps);
        c.quality=null; c.aspect=null; c.preset=null; c.validate(); assertNotNull(c.quality); assertNotNull(c.aspect);
    }
    @Test void encoderQualityIsActuallyAppliedToEveryBackend() {
        for(String codec:new String[]{"h264_nvenc","h264_amf","h264_qsv","libx264"}) for(var quality:VideoQuality.values()) {
            var options=dev.optic.encoding.EncoderDetector.options(codec,60,quality);
            assertTrue(options.contains(Integer.toString(quality.quantizer)));
            assertEquals("120",options.get(options.indexOf("-g")+1));
        }
    }
}
