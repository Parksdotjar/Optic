package dev.optic.replay;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class SegmentedPcmTest {
    @org.junit.jupiter.api.io.TempDir Path root;
    @Test void closesAtExactSampleBoundaryAndPublishesPartialOnStop() throws Exception {
        var storage=new SegmentedPcm(root); int chunk=48000*2*2;
        try(var out=storage.open(root.resolve("ignored"),1)) {
            out.write(new byte[chunk+10]); assertEquals(chunk,Files.size(SegmentedPcm.path(root,"mic",0,".pcm")));
            assertFalse(Files.exists(SegmentedPcm.path(root,"mic",1,".pcm")));
        }
        assertEquals(10,Files.size(SegmentedPcm.path(root,"mic",1,".pcm")));
    }
    @Test void delayedAudioCannotRecreateExpiredChunks() throws Exception {
        try(var out=new SegmentedPcm(root,() -> 3).open(root.resolve("ignored"),2)) { out.write(new byte[48000*4*2*4]); }
        assertFalse(Files.exists(SegmentedPcm.path(root,"game",0,".pcm")));
        assertTrue(Files.exists(SegmentedPcm.path(root,"game",3,".pcm")));
    }
}
