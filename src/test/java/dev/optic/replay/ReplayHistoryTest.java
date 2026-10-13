package dev.optic.replay;
import java.nio.file.*;
import java.util.*;
import dev.optic.recording.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ReplayHistoryTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @Test void allDurationsBoundClosedHistoryMetadataAndFiles() throws Exception {
        for(int seconds:new int[]{15,30,60,120,300}) {
            Path videos=directory.resolve("d"+seconds); Path partial=RecordingStorage.reserve(videos,"test",RecordingStorage.Style.DEFAULT);
            var history=new ReplayHistory(Path.of("unused"),partial,videos,new SessionManifest(),seconds,false,false,s -> {});
            try {
                var index=new StringBuilder();
                for(int n=0;n<200;n++) { Path video=ReplayExport.video(history.directory(),n); Files.write(video,new byte[20]); index.append(video.getFileName()+","+(n*2)+","+((n+1)*2)+"\n"); }
                Files.writeString(history.directory().resolve("index.csv"),index);
                waitFor(history,seconds);
                try(var files=Files.list(history.directory())) { assertTrue(files.filter(p -> p.toString().endsWith(".mkv")).count()<=(seconds+8)/2+2); }
                assertEquals(seconds,history.availableSeconds()); assertNull(history.failure());
            } finally { history.discardPrepared(); }
        }
    }
    @Test void bytePressureShortensHistoryWithoutUnboundedGrowth() throws Exception {
        Path partial=RecordingStorage.reserve(directory,"budget",RecordingStorage.Style.DEFAULT);
        var history=new ReplayHistory(Path.of("unused"),partial,directory,new SessionManifest(),300,false,false,s -> {},10000);
        try {
            var index=new StringBuilder();
            for(int n=0;n<20;n++) { Path video=ReplayExport.video(history.directory(),n); Files.write(video,new byte[1000]); index.append(video.getFileName()+","+(n*2)+","+((n+1)*2)+"\n"); }
            Files.writeString(history.directory().resolve("index.csv"),index);
            waitFor(history,1); Thread.sleep(100);
            assertTrue(history.bytes<=5000); assertTrue(history.availableSeconds()<=10); assertFalse(Files.exists(ReplayExport.video(history.directory(),0)));
        } finally { history.discardPrepared(); }
    }
    @Test void untrustedRecoveryPlanCannotSelectAnOutsideSource() throws Exception {
        Path partial=RecordingStorage.reserve(directory.resolve("videos"),"clip",RecordingStorage.Style.DEFAULT);
        Path outside=directory.resolve("outside"); Files.createDirectory(outside); Files.writeString(outside.resolve("keep"),"untouched");
        var props=new Properties(); props.setProperty("source",outside.toString());
        try(var out=Files.newOutputStream(partial.resolveSibling("clip-plan.properties"))) { props.store(out,""); }
        assertThrows(java.io.IOException.class,() -> ReplayRecovery.recover(Path.of("unused"),RecordingStorage.details(directory.resolve("videos")),s -> {}));
        assertEquals("untouched",Files.readString(outside.resolve("keep")));
    }
    private static void waitFor(ReplayHistory history,double seconds) throws Exception {
        long deadline=System.nanoTime()+5_000_000_000L;
        while((history.availableSeconds()<seconds || history.maintenancePasses==0) && history.failure()==null && System.nanoTime()<deadline) Thread.sleep(20);
        if(history.failure()!=null) throw history.failure();
        assertTrue(history.availableSeconds()>=seconds,"History did not publish");
    }
}
