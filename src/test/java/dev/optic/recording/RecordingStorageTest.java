package dev.optic.recording;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordingStorageTest {
    @TempDir Path root;
    @Test void numberedNamesAdvancePastVideosAndReservedSessions() throws Exception {
        Path videos=root.resolve("recordings"); Files.createDirectory(videos);
        Files.writeString(videos.resolve("Bounty SMP Part 3.mp4"),"existing");
        Path first=RecordingStorage.reserve(videos,"Bounty SMP",RecordingStorage.Style.PART);
        Path second=RecordingStorage.reserve(videos,"Bounty SMP",RecordingStorage.Style.PART);
        assertEquals("Bounty SMP Part 4.partial.mp4",first.getFileName().toString());
        assertEquals("Bounty SMP Part 5.partial.mp4",second.getFileName().toString());
        assertEquals(root.resolve("recordings-details"),first.getParent().getParent());
        try(var files=Files.list(videos)) { assertEquals(1,files.count()); }
    }
    @Test void concurrentAllocationsCannotChooseSameName() throws Exception {
        Path videos=root.resolve("recordings");
        try(var workers=Executors.newFixedThreadPool(4)) {
            var tasks=new java.util.ArrayList<Future<Path>>();
            for(int i=0;i<12;i++) tasks.add(workers.submit(() -> RecordingStorage.reserve(videos,"Bounty",RecordingStorage.Style.VERSION)));
            var unique=new java.util.HashSet<Path>(); for(var task:tasks) assertTrue(unique.add(task.get()));
        }
    }
    @Test void unsafeTitleCannotEscapeTheDetailsDirectory() throws Exception {
        Path videos=root.resolve("recordings");
        var partial=RecordingStorage.reserve(videos,"../Bounty: SMP?",RecordingStorage.Style.SCENE);
        assertEquals(RecordingStorage.details(videos),partial.getParent().getParent());
        assertEquals("Recording",RecordingStorage.cleanTitle("CON"));
        var manifest=new SessionManifest(); manifest.outputDirectory=videos.toString(); manifest.outputFile="Bounty Scene 1.mp4"; manifest.save(partial);
        assertEquals(videos.resolve("Bounty Scene 1.mp4"),RecordingStorage.target(partial));
        manifest.outputFile="../escape.mp4"; manifest.save(partial);
        assertThrows(java.io.IOException.class,() -> RecordingStorage.target(partial));
    }
}
