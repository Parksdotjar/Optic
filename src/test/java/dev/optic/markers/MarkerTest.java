package dev.optic.markers;
import dev.optic.recording.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MarkerTest {
    @org.junit.jupiter.api.io.TempDir Path root;
    private Marker marker(long nanos,int fps,String note) { return new Marker(1,UUID.randomUUID().toString(),"session",nanos,fps,Marker.Type.KEEP,note,null,false); }
    @Test void journalSurvivesMalformedAndTornUnicodeTail() throws Exception {
        Path partial=root.resolve("test.partial.mp4"); var m=marker(1234567890,60,"日本語, \"clip\"\nnext");
        try(var journal=new MarkerJournal(partial,s -> {})) { assertTrue(journal.offer(m)); }
        Files.writeString(MarkerJournal.path(partial),"broken\n",StandardOpenOption.APPEND);
        Files.write(MarkerJournal.path(partial),new byte[]{'{','"',(byte)0xe6,(byte)0x97},StandardOpenOption.APPEND);
        assertEquals(List.of(m),MarkerJournal.read(partial));
    }
    @Test void stableDuplicateTimesAndBoundariesAtAllFrameRates() {
        for(int fps:new int[]{30,60,120}) {
            var a=marker(5_000_000_000L,fps,"a"); var b=marker(5_000_000_000L,fps,"b");
            var list=MarkerExport.map(List.of(marker(3_000_000_000L,fps,"expired"),a,b,marker(8_000_000_000L,fps,"end excluded")),4,4,fps);
            assertEquals(2,list.size()); assertEquals(fps,list.getFirst().frame()); assertEquals(1,list.getFirst().seconds()); assertEquals("b",list.getLast().note());
        }
    }
    @Test void exportedClipRetainsOnlyItsEventsAndNoDefaultPrivateContext() throws Exception {
        Path source=root.resolve("source.partial.mp4");
        try(var journal=new MarkerJournal(source,s -> {})) { journal.offer(marker(1_000_000_000L,60,"old")); journal.offer(marker(5_000_000_000L,60,"=SUM(A1)")); }
        Path folder=root.resolve("clip"); Files.createDirectory(folder); Path clip=folder.resolve("clip.partial.mp4");
        var m=new SessionManifest(); m.fps=60; m.sourceStartSeconds=4; m.durationSeconds=3; m.outputDirectory=root.toString(); m.outputFile="clip.mp4"; m.save(clip);
        var timeline=MarkerExport.write(null,source,clip,m);
        assertEquals(1,timeline.markers().size()); assertNull(timeline.markers().getFirst().context());
        assertEquals(1,MarkerJournal.read(clip).size()); assertTrue(Files.readString(folder.resolve("markers.csv")).contains("'=SUM(A1)"));
        assertTrue(Files.readString(folder.resolve("markers.json")).contains("sourceElapsedNanos"));
    }
    @Test void queueCloseRefusesNewEventsAndNoteLengthBounded() {
        var journal=new MarkerJournal(root.resolve("closed.partial.mp4"),s -> {}); journal.close();
        assertFalse(journal.offer(marker(0,30,""))); assertEquals(256,Marker.note("x".repeat(1000)).length());
        assertEquals("\"comma,\"\"quote\"\"\nline\"",MarkerExport.cell("comma,\"quote\"\nline"));
    }
    @Test void fullJournalRefusesAdditionalEvents() throws Exception {
        var journal=new MarkerJournal(root.resolve("bounded.partial.mp4"),s -> {}); int accepted=0;
        for(int i=0;i<10000;i++) if(journal.offer(marker(i*1000L,30,""))) accepted++;
        journal.close(); assertTrue(accepted<=MarkerJournal.MAX_EVENTS); assertEquals(accepted,MarkerJournal.read(root.resolve("bounded.partial.mp4")).size());
    }
}
