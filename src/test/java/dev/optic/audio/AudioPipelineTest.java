package dev.optic.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioPipelineTest {
    @TempDir Path directory;
    @Test void reconnectGapRemainsSilenceAndGainClamps() throws Exception {
        var controls=new AudioControls(); controls.gain=2;
        Path file=directory.resolve("track.pcm"); byte[] pcm=new byte[960];
        for(int i=0;i<480;i++) { pcm[i*2]=0x20; pcm[i*2+1]=0x4e; }
        try(var writer=new PcmTrackWriter(file,1,1_000_000_000L,controls)) {
            writer.offer(pcm,480,1_000_000_000L); writer.offer(pcm,480,1_030_000_000L);
        }
        byte[] result=Files.readAllBytes(file); assertEquals(3840,result.length);
        assertEquals(255,result[0]&255); assertEquals(127,result[1]&255);
        for(int i=960;i<2880;i++) assertEquals(0,result[i]);
        assertEquals(0,controls.clippingUntil,"Brief peaks should not spam clipping warnings");
    }
    @Test void muteWritesSilenceWithoutRemovingTime() throws Exception {
        var controls=new AudioControls(); controls.muted=true; Path file=directory.resolve("mute.pcm");
        byte[] pcm=new byte[960]; java.util.Arrays.fill(pcm,(byte)100);
        try(var writer=new PcmTrackWriter(file,1,0,controls)) { writer.offer(pcm,480,0); }
        assertArrayEquals(new byte[960],Files.readAllBytes(file));
    }
    @Test void pauseDropsSamplesAndCollapsesTheirTimestampGap() throws Exception {
        var controls=new AudioControls(); Path file=directory.resolve("pause.pcm");
        byte[] first=new byte[960], during=new byte[960], resumed=new byte[960];
        java.util.Arrays.fill(first,(byte)1); java.util.Arrays.fill(during,(byte)2); java.util.Arrays.fill(resumed,(byte)3);
        try(var writer=new PcmTrackWriter(file,1,1_000_000_000L,controls)) {
            writer.offer(first,480,1_000_000_000L);
            writer.setPaused(true,1_010_000_000L);
            writer.offer(during,480,2_000_000_000L);
            writer.setPaused(false,2_010_000_000L);
            writer.offer(resumed,480,2_010_000_000L);
        }
        byte[] result=Files.readAllBytes(file);
        assertEquals(1920,result.length);
        assertEquals(1,result[0]); assertEquals(3,result[960]);
    }
    @Test void syntheticFourHourClockDriftStaysWithinTwentyMilliseconds() {
        for(double ppm:new double[]{-2000,-500,500,2000}) {
            double rate=48000*(1+ppm/1e6); var clock=new AudioClock(); clock.reset(1_000_000_000L);
            double outputFrames=0, worst=0;
            for(long block=1;block<=1_440_000;block++) {
                double seconds=block*480d/rate; clock.advance(480,1_000_000_000L+(long)(seconds*1e9));
                outputFrames+=480*48000/clock.rate();
                worst=Math.max(worst,Math.abs(outputFrames/48000-seconds));
            }
            assertTrue(worst<.020,"Drift "+ppm+" ppm, error "+worst);
        }
    }
    @Test void resamplingPreservesContinuityAndBoundedOutput() {
        var resampler=new LinearResampler(); byte[] input=new byte[960], output=new byte[1024];
        for(int i=0;i<480;i++) { input[i*2]=(byte)0xe8; input[i*2+1]=3; }
        int total=0;
        for(int b=0;b<1000;b++) { int n=resampler.process(input,480,48024,output); total+=n; for(int i=0;i<n;i++) assertEquals(1000,(short)((output[i*2]&255)|(output[i*2+1]<<8))); }
        assertEquals(480000/1.0005,total,2);
    }
}
