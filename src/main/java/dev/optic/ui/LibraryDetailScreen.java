package dev.optic.ui;
import dev.optic.OpticClient;
import dev.optic.workflow.RecordingLibrary;
import dev.optic.recording.RecordingManager;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LibraryDetailScreen extends Screen {
    private final Screen parent; private final RecordingLibrary.Item item;
    private EditBox title,notes; private int x,y,w; private String status="Notes stay in Details. Refresh the library after changes.";
    private boolean confirm,busy;
    public LibraryDetailScreen(Screen parent,RecordingLibrary.Item item) { super(Component.literal("Recording details")); this.parent=parent; this.item=item; }
    @Override protected void init() {
        w=Math.min(430,width-24); x=(width-w)/2; y=Math.max(8,(height-260)/2);
        title=new EditBox(font,x,y+25,w,20,Component.literal("Video name")); title.setMaxLength(80); title.setValue(item.video().getFileName().toString().replaceFirst("\\.mp4$","")); addRenderableWidget(title);
        addRenderableWidget(new FlatButton(x,y+49,w/2-2,"Open video",b -> RecordingSavedScreen.open(item.video()))).active=item.exists() && !item.trashed();
        addRenderableWidget(new FlatButton(x+w/2+2,y+49,w/2-2,"Rename",b -> { String value=title.getValue(); work(() -> RecordingLibrary.move(item,value,false)); }));
        notes=new EditBox(font,x,y+87,w,20,Component.literal("Recording notes")); notes.setMaxLength(2048); notes.setValue(item.notes()); addRenderableWidget(notes);
        addRenderableWidget(new FlatButton(x,y+111,w,"Save notes",b -> { String value=notes.getValue(); work(() -> RecordingLibrary.notes(item,value)); }));
//? if <26.2 {
        addRenderableWidget(new FlatButton(x,y+137,w/2-2,"View markers",b -> minecraft.setScreen(new MarkerTimelineScreen(this,item.partial()))));
//?} else {
        /*addRenderableWidget(new FlatButton(x,y+137,w/2-2,"View markers",b -> minecraft.gui.setScreen(new MarkerTimelineScreen(this,item.partial()))));
*///?}
        addRenderableWidget(new FlatButton(x+w/2+2,y+137,w/2-2,"Open Details",b -> RecordingSavedScreen.open(item.partial().getParent())));
        addRenderableWidget(new FlatButton(x,y+163,w,item.trashed()?"Restore from Trash":"Delete…",b -> { if(item.trashed()) { work(() -> RecordingLibrary.restore(item)); return; } if(!confirm) { confirm=true; b.setMessage(Component.literal("Confirm: move this video to Details / Trash")); status="Recoverable removal of "+item.video().getFileName(); } else work(() -> RecordingLibrary.move(item,"",true)); }));
        addRenderableWidget(new FlatButton(x,y+232,w,"Done",b -> onClose()));
    }
    private interface Action { void run() throws Exception; }
    private void work(Action action) {
        if(busy) return;
        if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { status="Stop recording/replay before changing saved files."; return; }
        busy=true; status="Working…";
        if(!dev.optic.workflow.WorkflowJobs.execute(() -> { try { if(OpticClient.manager.state()!=RecordingManager.State.IDLE) throw new java.io.IOException("Stop recording/replay before changing saved files."); action.run(); minecraft.execute(() -> { status="Saved. Refresh the library to see changes."; busy=false; }); } catch(Exception e) { minecraft.execute(() -> { status=e.getMessage(); busy=false; }); } })) { busy=false; status="Library busy; retry shortly."; }
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) { super.render(g,mx,my,dt); g.drawString(font,"RECORDING",x,y,0xffffffff); g.drawString(font,"Notes",x,y+75,0xffaaaaaa); int lineY=y+191; for(var line:font.split(Component.literal(status==null?"Operation failed":status),w)) { if(lineY>y+221) break; g.drawString(font,line,x,lineY,0xffaaaaaa); lineY+=10; } }
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) { super.extractRenderState(g,mx,my,dt); g.text(font,"RECORDING",x,y,0xffffffff); g.text(font,"Notes",x,y+75,0xffaaaaaa); int lineY=y+191; for(var line:font.split(Component.literal(status==null?"Operation failed":status),w)) { if(lineY>y+221) break; g.text(font,line,x,lineY,0xffaaaaaa); lineY+=10; } }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
