package dev.optic.ui;
import dev.optic.OpticClient;
import dev.optic.workflow.RecordingLibrary;
import dev.optic.recording.RecordingManager;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class LibraryScreen extends Screen {
    private final Screen parent;
    private List<RecordingLibrary.Item> items=List.of();
    private String status="Loading…";
    private int page,x,y,w;
    private volatile int generation;
    private boolean loaded;
    private static java.nio.file.Path cachedRoot;
    private static List<RecordingLibrary.Item> cachedItems=List.of();
    public LibraryScreen(Screen parent) { super(Component.literal("Recordings")); this.parent=parent; }
    @Override protected void init() {
        w=Math.min(480,width-24); x=(width-w)/2; y=Math.max(8,(height-252)/2);
        for(int i=0;i<6;i++) { int n=page*6+i; if(n>=items.size()) break; var item=items.get(n);
            String date=item.date().length()>=10?item.date().substring(0,10):item.date();
            String label=date+"  "+item.video().getFileName()+"  "+MarkerTimelineScreen.time(item.duration())+(item.trashed()?" [Trash]":!item.exists()?" [Missing]":"");
//? if <26.2 {
            addRenderableWidget(new FlatButton(x,y+34+i*25,w,font.plainSubstrByWidth(label,w-12),b -> minecraft.setScreen(new LibraryDetailScreen(this,item))));
//?} else {
            /*addRenderableWidget(new FlatButton(x,y+34+i*25,w,font.plainSubstrByWidth(label,w-12),b -> minecraft.gui.setScreen(new LibraryDetailScreen(this,item))));
*///?}
        }
        addRenderableWidget(new FlatButton(x,y+190,75,"Previous",b -> { page--; rebuildWidgets(); })).active=page>0;
        addRenderableWidget(new FlatButton(x+79,y+190,75,"Next",b -> { page++; rebuildWidgets(); })).active=(page+1)*6<items.size();
        addRenderableWidget(new FlatButton(x+158,y+190,80,"Refresh",b -> load()));
        addRenderableWidget(new FlatButton(x+242,y+190,w-242,"Open folder",b -> RecordingSavedScreen.open(OpticClient.manager.directory)));
        addRenderableWidget(new FlatButton(x,y+218,w,"Done",b -> onClose()));
        if(!loaded) { loaded=true; if(OpticClient.manager.directory.equals(cachedRoot)) { items=cachedItems; status="Cached sessions — Refresh checks changes."; rebuildWidgets(); } else load(); }
    }
    private void load() {
        if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { status="Stop recording/replay to refresh the library."; return; }
        int request=++generation; status="Scanning saved sessions…";
        dev.optic.workflow.WorkflowJobs.execute(() -> { try {
            var result=RecordingLibrary.scan(OpticClient.manager.directory,() -> generation!=request || OpticClient.manager.state()!=RecordingManager.State.IDLE,partial -> minecraft.execute(() -> { if(generation==request) { items=partial; page=Math.min(page,Math.max(0,(items.size()-1)/6)); rebuildWidgets(); } }));
            minecraft.execute(() -> { if(generation==request) { items=result; cachedRoot=OpticClient.manager.directory; cachedItems=result; status=items.size()+" saved sessions · newest first · up to 20,000 folders per refresh"; page=0; rebuildWidgets(); } });
        } catch(Exception e) { minecraft.execute(() -> status="Cannot scan this folder: "+e.getMessage()); } });
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) { super.render(g,mx,my,dt); g.drawString(font,"RECORDINGS",x,y,0xffffffff); g.drawString(font,font.plainSubstrByWidth(status,w),x,y+17,0xffaaaaaa); }
    @Override public void onClose() { generation++; minecraft.setScreen(parent); }
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) { super.extractRenderState(g,mx,my,dt); g.text(font,"RECORDINGS",x,y,0xffffffff); g.text(font,font.plainSubstrByWidth(status,w),x,y+17,0xffaaaaaa); }
    @Override public void onClose() { generation++; minecraft.gui.setScreen(parent); }
*///?}
}
