package dev.optic.ui;

import dev.optic.OpticClient;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DiagnosticsScreen extends Screen {
    private final Screen parent;
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines=java.util.List.of();
    private long updated;
    public DiagnosticsScreen(Screen parent) { super(Component.literal("Recorder diagnostics")); this.parent=parent; }
    @Override protected void init() {
//? if <26.2 {
        addRenderableWidget(new FlatButton(width/2-150,height-30,146,Component.translatable("optic.support.preview"),b -> minecraft.setScreen(new SupportReportScreen(this))));
//?} else {
        /*addRenderableWidget(new FlatButton(width/2-150,height-30,146,Component.translatable("optic.support.preview"),b -> minecraft.gui.setScreen(new SupportReportScreen(this))));
*///?}
        addRenderableWidget(new FlatButton(width/2+4,height-30,146,Component.translatable("gui.done"),b -> onClose()));
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xee080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt);
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xee080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt);
*///?}
        if(System.nanoTime()-updated>1_000_000_000L) {
            updated=System.nanoTime(); lines=font.split(Component.literal((OpticClient.manager.diagnostics()+"\nClean capture (since launch): "+dev.optic.compat.CleanCapture.diagnostics()).replace(" ","  ")),Math.max(200,width-40));
        }
//? if <26.2 {
        g.drawString(font,"Recorder diagnostics",20,16,0xffffffff);
        g.drawString(font,"CPU hook timings are not an OBS comparison.",20,32,0xffaaaaaa);
        int y=54; for(var line:lines) { if(y>height-46) break; g.drawString(font,line,20,y,0xffcccccc); y+=12; }
//?} else {
        /*g.text(font,"Recorder diagnostics",20,16,0xffffffff);
        g.text(font,"CPU hook timings are not an OBS comparison.",20,32,0xffaaaaaa);
        int y=54; for(var line:lines) { if(y>height-46) break; g.text(font,line,20,y,0xffcccccc); y+=12; }
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
