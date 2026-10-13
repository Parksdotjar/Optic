package dev.optic.ui;

import dev.optic.OpticClient;
import dev.optic.recording.CompletedRecording;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Only opens from an existing menu or an explicit user action; never interrupts gameplay. */
public final class RecordingSavedScreen extends Screen {
    private final Screen parent;
    private final CompletedRecording recording;
    public RecordingSavedScreen(Screen parent, CompletedRecording recording) {
        super(Component.translatable("optic.saved")); this.parent=parent; this.recording=recording;
    }
    @Override protected void init() {
        int x=width/2-110, y=height/2+24;
        addRenderableWidget(new FlatButton(x,y,108,Component.translatable("optic.open.video"),b -> open(recording.file())));
        addRenderableWidget(new FlatButton(x+112,y,108,Component.translatable("optic.open.folder"),b -> open(recording.file().getParent())));
        addRenderableWidget(new FlatButton(x,y+26,220,"View markers",b -> {
            var folder=dev.optic.recording.RecordingStorage.details(recording.file().getParent()).resolve(recording.file().getFileName().toString().replaceFirst("\\.mp4$",""));
//? if <26.2 {
            minecraft.setScreen(new MarkerTimelineScreen(this,folder.resolve(folder.getFileName()+".partial.mp4")));
//?} else {
            /*minecraft.gui.setScreen(new MarkerTimelineScreen(this,folder.resolve(folder.getFileName()+".partial.mp4")));
*///?}
        }));
        addRenderableWidget(new FlatButton(x,y+52,220,Component.translatable("gui.done"),b -> onClose()));
    }
    public static void open(java.nio.file.Path path) {
        OpticClient.io.execute(() -> {
//? if <26.2 {
            try { if(!java.nio.file.Files.exists(path)) throw new java.io.IOException("File is no longer available"); Util.getPlatform().openPath(path); }
//?} else {
            /*try { if(!java.nio.file.Files.exists(path)) throw new java.io.IOException("File is no longer available"); java.awt.Desktop.getDesktop().open(path.toFile()); }
*///?}
            catch(Exception e) { OpticClient.notice("Could not open the recording location."); OpticClient.LOG.warn("Open recording failed",e); }
        });
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xee080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); int x=width/2-110, y=height/2-70;
        g.drawString(font,getTitle(),x,y,0xffffffff);
        g.drawString(font,recording.durationLabel(),x,y+22,0xffffffff);
        g.drawString(font,recording.width()+"×"+recording.height(),x,y+38,0xffbbbbbb);
        g.drawString(font,recording.fps()+" FPS",x,y+54,0xffbbbbbb);
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xee080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); int x=width/2-110, y=height/2-70;
        g.text(font,getTitle(),x,y,0xffffffff);
        g.text(font,recording.durationLabel(),x,y+22,0xffffffff);
        g.text(font,recording.width()+"×"+recording.height(),x,y+38,0xffbbbbbb);
        g.text(font,recording.fps()+" FPS",x,y+54,0xffbbbbbb);
*///?}
        String audio=recording.gameAudio()?(recording.microphone()?"Minecraft + microphone (2 tracks)":"Minecraft audio"):(recording.microphone()?"Microphone":"Video only");
//? if <26.2 {
        g.drawString(font,audio,x,y+70,0xffaaaaaa);
//?} else {
        /*g.text(font,audio,x,y+70,0xffaaaaaa);
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
