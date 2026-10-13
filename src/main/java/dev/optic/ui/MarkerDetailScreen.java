package dev.optic.ui;
import dev.optic.markers.MarkerExport;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;

public final class MarkerDetailScreen extends Screen {
    private final Screen parent;
    private final Path partial;
    private final MarkerExport.Entry entry;
    private int x,y,w;
    public MarkerDetailScreen(Screen parent,Path partial,MarkerExport.Entry entry) { super(Component.literal("Marker")); this.parent=parent; this.partial=partial; this.entry=entry; }
    @Override protected void init() {
        w=Math.min(400,width-24); x=(width-w)/2; y=Math.max(8,(height-230)/2);
        addRenderableWidget(new FlatButton(x,y+174,w,"Open thumbnail (after export)",b -> RecordingSavedScreen.open(partial.resolveSibling("marker-"+entry.id()+".jpg")))).active=entry.thumbnailRequested();
        addRenderableWidget(new FlatButton(x,y+200,w,"Done",b -> onClose()));
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); g.drawString(font,MarkerTimelineScreen.time(entry.seconds())+"  "+entry.type()+" · frame "+entry.frame(),x,y,0xffffffff);
        int lineY=y+22; for(var line:font.split(Component.literal(entry.note().isBlank()?"No note":entry.note()),w)) { if(lineY>y+125) break; g.drawString(font,line,x,lineY,0xffdddddd); lineY+=11; }
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.text(font,MarkerTimelineScreen.time(entry.seconds())+"  "+entry.type()+" · frame "+entry.frame(),x,y,0xffffffff);
        int lineY=y+22; for(var line:font.split(Component.literal(entry.note().isBlank()?"No note":entry.note()),w)) { if(lineY>y+125) break; g.text(font,line,x,lineY,0xffdddddd); lineY+=11; }
*///?}
        var c=entry.context(); String context=c==null?"No location collected.":c.x()+", "+c.y()+", "+c.z()+" · "+c.dimension()+" · "+c.worldLabel();
//? if <26.2 {
        lineY=y+137; for(var line:font.split(Component.literal(context),w)) { if(lineY>y+161) break; g.drawString(font,line,x,lineY,0xffaaaaaa); lineY+=11; }
//?} else {
        /*lineY=y+137; for(var line:font.split(Component.literal(context),w)) { if(lineY>y+161) break; g.text(font,line,x,lineY,0xffaaaaaa); lineY+=11; }
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
