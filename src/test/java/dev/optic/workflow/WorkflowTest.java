package dev.optic.workflow;
import dev.optic.recording.*;
import dev.optic.config.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {
    @org.junit.jupiter.api.io.TempDir Path root;
    @BeforeEach void nestedRoot() throws Exception { root=root.resolve("videos"); Files.createDirectory(root); }
    private RecordingLibrary.Item video(String name) throws Exception {
        Path p=RecordingStorage.reserveTemplate(root,name); var m=new SessionManifest(); m.sessionId=UUID.randomUUID().toString(); m.state="complete"; m.fps=60; m.durationSeconds=4; m.createdUtc="2026-10-11T00:00:00Z"; m.outputDirectory=root.toString(); m.outputFile=name+".mp4"; m.save(p); Files.write(root.resolve(m.outputFile),new byte[]{1,2,3});
        return new RecordingLibrary.Item(p,root.resolve(m.outputFile),m.sessionId,m.createdUtc,4,"",true,false);
    }
    @Test void renameNotesAndTrashPreserveBundleAndRefuseStaleActions() throws Exception {
        var item=video("Original"); Files.writeString(item.partial().resolveSibling("keep.txt"),"sidecar");
        RecordingLibrary.notes(item,"日本語 notes"); RecordingLibrary.move(item,"Renamed",false);
        assertFalse(Files.exists(item.video())); assertTrue(Files.exists(root.resolve("Renamed.mp4"))); assertTrue(Files.exists(item.partial().resolveSibling("keep.txt")));
        assertThrows(java.io.IOException.class,() -> RecordingLibrary.notes(item,"stale"));
        var fresh=RecordingLibrary.scan(root,() -> false,x -> {}).getFirst(); assertEquals("日本語 notes",fresh.notes());
        RecordingLibrary.move(fresh,"",true); assertFalse(Files.exists(fresh.video())); assertTrue(Files.exists(fresh.partial().getParent().resolve("Trash/Renamed.mp4"))); assertTrue(SessionManifest.read(fresh.partial()).trashed);
        RecordingLibrary.restore(RecordingLibrary.scan(root,() -> false,x -> {}).getFirst()); assertTrue(Files.exists(fresh.video())); assertFalse(SessionManifest.read(fresh.partial()).trashed);
    }
    @Test void busyCollisionAndInterruptedRename() throws Exception {
        var item=video("One"); video("Two");
        assertThrows(java.io.IOException.class,() -> RecordingLibrary.move(item,"Two",false));
        try(var lock=SessionFiles.tryLock(item.partial())) { assertThrows(java.io.IOException.class,() -> RecordingLibrary.notes(item,"locked")); }
        var p=new Properties(); p.setProperty("old","One.mp4"); p.setProperty("new","Moved.mp4"); p.setProperty("trash","false");
        try(var out=Files.newOutputStream(item.partial().resolveSibling("library-move.properties"))) { p.store(out,""); }
        Files.move(item.video(),root.resolve("Moved.mp4")); RecordingLibrary.reconcile(root); assertEquals("Moved.mp4",SessionManifest.read(item.partial()).outputFile);
    }
    @Test void profilesPreservePrivacyDevicesPathsAndValidatePortableValues() throws Exception {
        var c=new RecorderConfig(); c.recordingsFolder="keep"; c.microphoneDevice="my device"; c.markerContext=false;
        var p=CreatorProfile.builtIn("Cinematic",c); p.apply(c); assertFalse(c.captureHud); assertEquals("keep",c.recordingsFolder); assertEquals("my device",c.microphoneDevice); assertFalse(c.markerContext);
        CreatorProfile.save(root.resolve("profile.json"),CreatorProfile.capture("Custom",c)); var loaded=CreatorProfile.load(root.resolve("profile.json")); loaded.apply(c); assertEquals(VideoQuality.VERY_HIGH,c.quality);
        loaded.schemaVersion=99; assertThrows(IllegalArgumentException.class,() -> loaded.apply(c));
    }
    @Test void templatesSanitizeAndResolveCollisions() throws Exception {
        var time=java.time.LocalDateTime.of(2026,10,11,12,34,56);
        assertEquals("2026-10-11-Minecraft-12-34-56",NamingTemplate.resolve("{date}-{server}-{time}","","",time));
        assertFalse(NamingTemplate.resolve("{title}","../../CON",null,time).contains("/"));
        assertEquals("Recording",NamingTemplate.resolve("CON","","",time));
        assertThrows(IllegalArgumentException.class,() -> NamingTemplate.resolve("{unknown}","","",time));
        assertNotEquals(RecordingStorage.reserveTemplate(root,"same"),RecordingStorage.reserveTemplate(root,"same"));
    }
    @Test void tenThousandEntryIndexIsBoundedIncrementalAndToleratesMissingAndCorrupt() throws Exception {
        Path details=RecordingStorage.details(root); Files.createDirectories(details);
        for(int n=0;n<10000;n++) {
            Path dir=details.resolve("entry"+n); Files.createDirectory(dir); var m=new SessionManifest(); m.sessionId="id"+n; m.state="complete"; m.outputDirectory=root.toString(); m.outputFile="entry"+n+".mp4"; m.save(dir.resolve("entry"+n+".partial.mp4"));
        }
        Path bad=details.resolve("bad"); Files.createDirectory(bad); Files.writeString(bad.resolve("bad.session.json"),"broken");
        int[] pages={0}; long start=System.nanoTime(); var items=RecordingLibrary.scan(root,() -> false,p -> pages[0]++);
        assertEquals(10000,items.size()); assertTrue(pages[0]>1); assertTrue(items.stream().noneMatch(RecordingLibrary.Item::exists));
        System.out.println("10k metadata scan ms="+(System.nanoTime()-start)/1e6);
        assertTrue(RecordingLibrary.scan(root,() -> true,p -> {}).isEmpty());
    }
}
