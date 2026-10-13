package dev.optic.ui;

import dev.optic.OpticClient;
import dev.optic.markers.Marker;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MarkersScreen extends Screen {
    private final Screen parent;
    private EditBox note;
    private String draft="";
    private int x,y,w;
    public MarkersScreen(Screen parent) { super(Component.literal("Markers")); this.parent=parent; }
    @Override protected void init() {
        w=Math.min(360,width-24); x=(width-w)/2; y=Math.max(8,(height-260)/2);
        var c=OpticClient.config;
        addRenderableWidget(new FlatButton(x,y+24,w,"Markers: "+(c.markersEnabled?"ON":"OFF"),b -> { c.markersEnabled=!c.markersEnabled; refresh(); }));
        addRenderableWidget(new FlatButton(x,y+48,w,"Default type: "+c.markerType.label(),b -> { var types=Marker.Type.values(); c.markerType=types[(c.markerType.ordinal()+1)%types.length]; refresh(); }));
        addRenderableWidget(new FlatButton(x,y+72,w,"Coordinates + world/server label: "+(c.markerContext?"ON":"OFF"),b -> { c.markerContext=!c.markerContext; refresh(); })).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Applies to new markers. Turning OFF does not remove previously saved locations or labels. No account IDs or server address field is collected.")));
        addRenderableWidget(new FlatButton(x,y+96,w,"Thumbnails after saving: "+(c.markerThumbnails?"ON":"OFF"),b -> { c.markerThumbnails=!c.markerThumbnails; refresh(); }));
        note=new EditBox(font,x,y+133,w,20,Component.literal("Optional marker note")); note.setMaxLength(256); note.setValue(draft); addRenderableWidget(note);
        addRenderableWidget(new FlatButton(x,y+159,w/2-2,"Add marker",b -> OpticClient.manager.addMarker(note.getValue())));
//? if <26.2 {
        addRenderableWidget(new FlatButton(x+w/2+2,y+159,w/2-2,"Timeline",b -> { draft=note.getValue(); minecraft.setScreen(new MarkerTimelineScreen(this,OpticClient.manager.markerPartial())); }));
//?} else {
        /*addRenderableWidget(new FlatButton(x+w/2+2,y+159,w/2-2,"Timeline",b -> { draft=note.getValue(); minecraft.gui.setScreen(new MarkerTimelineScreen(this,OpticClient.manager.markerPartial())); }));
*///?}
        addRenderableWidget(new FlatButton(x,y+231,w,"Done",b -> onClose()));
    }
    private void refresh() { draft=note==null?draft:note.getValue(); OpticClient.saveConfig(); rebuildWidgets(); }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); g.drawString(font,"MARKERS",x,y+4,0xffffffff); g.drawString(font,"Optional note (Add marker only)",x,y+122,0xffaaaaaa);
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.text(font,"MARKERS",x,y+4,0xffffffff); g.text(font,"Optional note (Add marker only)",x,y+122,0xffaaaaaa);
*///?}
        String text=OpticClient.markerKeyLabel()+" bookmarks; F10 saves clips. "+(OpticClient.markerKeyConflict()?"Key conflict: change in Minecraft Controls. ":"")+"Context exports include private locations/labels when enabled. Thumbnails: up to 8 per video. Generic JSON/CSV; no certified editor import.";
//? if <26.2 {
        int lineY=y+185; for(var line:font.split(Component.literal(text),w)) { if(lineY>y+223) break; g.drawString(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
//?} else {
        /*int lineY=y+185; for(var line:font.split(Component.literal(text),w)) { if(lineY>y+223) break; g.text(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { OpticClient.saveConfig(); minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { OpticClient.saveConfig(); minecraft.gui.setScreen(parent); }
*///?}
}
