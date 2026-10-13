package dev.optic.ui;

import dev.optic.OpticClient;
import dev.optic.markers.*;
import dev.optic.recording.*;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Paginated metadata list; no video player or scanning during render. */
public final class MarkerTimelineScreen extends Screen {
    private final Screen parent;
    private Path partial;
    private List<MarkerExport.Entry> entries=List.of();
    private String status="Loading…";
    private int page,x,y,w;
    private double duration;
    private boolean loading;
    public MarkerTimelineScreen(Screen parent,Path partial) { super(Component.literal("Marker timeline")); this.parent=parent; this.partial=partial; }
    @Override protected void init() {
        w=Math.min(460,width-24); x=(width-w)/2; y=Math.max(8,(height-250)/2);
        addRenderableWidget(new FlatButton(x,y+184,60,"Previous",b -> { page=Math.max(0,page-1); rebuildWidgets(); })).active=page>0;
        addRenderableWidget(new FlatButton(x+64,y+184,60,"Next",b -> { page++; rebuildWidgets(); })).active=(page+1)*6<entries.size();
        addRenderableWidget(new FlatButton(x+128,y+184,64,"Refresh",b -> load()));
        addRenderableWidget(new FlatButton(x+196,y+184,w-196,"Details / exports",b -> { if(partial!=null) RecordingSavedScreen.open(partial.getParent()); })).active=partial!=null;
        addRenderableWidget(new FlatButton(x,y+210,w/2-2,"Export JSON / CSV",b -> export())).active=partial!=null;
        addRenderableWidget(new FlatButton(x+w/2+2,y+210,w/2-2,"Done",b -> onClose()));
        for(int i=0;i<6;i++) { int n=page*6+i; if(n>=entries.size()) break; var e=entries.get(n);
//? if <26.2 {
            addRenderableWidget(new FlatButton(x,y+44+i*20,w,font.plainSubstrByWidth(time(e.seconds())+"  "+e.type()+"  "+e.note().replace('\n',' '),w-12),b -> minecraft.setScreen(new MarkerDetailScreen(this,partial,e))));
//?} else {
            /*addRenderableWidget(new FlatButton(x,y+44+i*20,w,font.plainSubstrByWidth(time(e.seconds())+"  "+e.type()+"  "+e.note().replace('\n',' '),w-12),b -> minecraft.gui.setScreen(new MarkerDetailScreen(this,partial,e))));
*///?}
        }
        if(status.equals("Loading…") && !loading) load();
    }
    private void load() {
        if(loading) return; loading=true;
        OpticClient.io.execute(() -> {
            try {
                Path selected=partial;
                if(selected==null) {
                    // Latest local session only; a full recording library belongs to V8.
                    Path root=OpticClient.manager.detailsDirectory(); long newest=Long.MIN_VALUE;
                    if(Files.isDirectory(root)) try(var dirs=Files.newDirectoryStream(root)) { for(Path dir:dirs) {
                        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)) continue;
                        Path candidate=dir.resolve(dir.getFileName()+".partial.mp4"); Path journal=MarkerJournal.path(candidate);
                        if(Files.isRegularFile(journal) && Files.getLastModifiedTime(journal).toMillis()>newest) { newest=Files.getLastModifiedTime(journal).toMillis(); selected=candidate; }
                    } }
                }
                if(selected==null) throw new java.io.IOException("No marker session yet.");
                var m=SessionManifest.read(selected); if(m==null) throw new java.io.IOException("Session metadata unavailable.");
                var markers=MarkerJournal.read(selected); boolean complete="complete".equals(m.state);
                double length=complete?m.durationSeconds:Math.max(m.durationSeconds,markers.isEmpty()?0:markers.getLast().seconds()+1);
                var list=MarkerExport.map(markers,m.sourceStartSeconds,length,m.fps); Path result=selected;
                minecraft.execute(() -> { partial=result; entries=list; duration=length; status=complete?"Saved video · "+entries.size()+" markers":"Session timestamps — replay footage may expire; save a clip to retain it."; page=0; loading=false; rebuildWidgets(); });
            } catch(Exception e) { minecraft.execute(() -> { status=Objects.toString(e.getMessage(),"Could not read timeline"); loading=false; }); }
        });
    }
    private void export() {
        status="Preparing generic exports…"; Path file=partial;
        OpticClient.io.execute(() -> { try {
            var m=SessionManifest.read(file); if(m==null || !"complete".equals(m.state)) throw new java.io.IOException("Save the video before exporting its timeline.");
            MarkerExport.schedule(null,file,file,m);
            minecraft.execute(() -> status="Export queued in Details. JSON/CSV are generic, not native editor markers.");
        } catch(Exception e) { minecraft.execute(() -> status=e.getMessage()); } });
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
*///?}
    public static String time(double seconds) { long n=(long)seconds; return String.format(Locale.ROOT,"%02d:%02d:%02d",n/3600,n/60%60,n%60); }
//? if <26.2 {
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); g.drawString(font,"MARKERS",x,y,0xffffffff);
        g.drawString(font,font.plainSubstrByWidth(status==null?"Unavailable":status,w),x,y+15,0xffaaaaaa);
        g.drawString(font,"00:00:00  Video/session start",x,y+31,0xffaaaaaa);
        g.drawString(font,time(duration)+"  "+(status.startsWith("Saved")?"Video ended":"Latest known time"),x,y+171,0xffaaaaaa);
//?} else {
    /*@Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.text(font,"MARKERS",x,y,0xffffffff);
        g.text(font,font.plainSubstrByWidth(status==null?"Unavailable":status,w),x,y+15,0xffaaaaaa);
        g.text(font,"00:00:00  Video/session start",x,y+31,0xffaaaaaa);
        g.text(font,time(duration)+"  "+(status.startsWith("Saved")?"Video ended":"Latest known time"),x,y+171,0xffaaaaaa);
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
