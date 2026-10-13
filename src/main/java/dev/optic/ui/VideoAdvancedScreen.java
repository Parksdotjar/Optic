package dev.optic.ui;

import dev.optic.OpticClient;
import dev.optic.config.*;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Only video choices. Presets never touch audio or clean-capture preferences. */
public final class VideoAdvancedScreen extends Screen {
    private final Screen parent;
    private java.util.List<net.minecraft.util.FormattedCharSequence> notes=java.util.List.of();
    public VideoAdvancedScreen(Screen parent) { super(Component.literal("Video settings")); this.parent=parent; }
    protected void init() {
        var c=OpticClient.config; int x=width/2-130,y=Math.max(30,height/2-105);
        addRenderableWidget(new FlatButton(x,y,260,"Quality: "+c.quality.label,b -> { c.quality=VideoQuality.values()[(c.quality.ordinal()+1)%VideoQuality.values().length]; changed(); }));
        addRenderableWidget(new FlatButton(x,y+26,260,"Framing: "+c.aspect.label(),b -> { c.aspect=c.aspect==dev.optic.capture.ScaleLayout.Aspect.FIT?dev.optic.capture.ScaleLayout.Aspect.CROP:dev.optic.capture.ScaleLayout.Aspect.FIT; changed(); }));
        addRenderableWidget(new FlatButton(x,y+52,260,"Encoder: "+dev.optic.encoding.EncoderDetector.label(c.encoder),b -> {
            var choices=java.util.List.of("auto","h264_nvenc","h264_amf","h264_qsv","libx264"); c.encoder=choices.get((choices.indexOf(c.encoder)+1)%choices.size()); changed();
        }));
//? if <26.2 {
        addRenderableWidget(new FlatButton(x,y+78,260,"Advanced formats / color / overload",b -> minecraft.setScreen(new CreatorCaptureScreen(this))));
//?} else {
        /*addRenderableWidget(new FlatButton(x,y+78,260,"Advanced formats / color / overload",b -> minecraft.gui.setScreen(new CreatorCaptureScreen(this))));
*///?}
        String text="Higher output resolution than the window is upscaled, not extra world detail. Fit keeps the full view; Fill crops the edges. Output dimensions stay fixed during recording.\n\nAuto probes hardware and falls back to software when necessary. Quality and performance vary by encoder. Impact: not measured. Applies to new recordings.";
        notes=font.split(Component.literal(text),260);
        addRenderableWidget(new FlatButton(x,height-28,260,"Done",b -> onClose()));
    }
    private void changed() { OpticClient.config.preset=RecordingPreset.CUSTOM; OpticClient.saveConfig(); rebuildWidgets(); }
//? if <26.2 {
    public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); int x=width/2-130,y=Math.max(30,height/2-105);
        g.drawString(font,"VIDEO SETTINGS",x,y-18,0xffffffff); y+=106;
        for(var line:notes) { if(y>height-42) break; g.drawString(font,line,x,y,0xffaaaaaa); y+=11; }
//?} else {
    /*public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); int x=width/2-130,y=Math.max(30,height/2-105);
        g.text(font,"VIDEO SETTINGS",x,y-18,0xffffffff); y+=106;
        for(var line:notes) { if(y>height-42) break; g.text(font,line,x,y,0xffaaaaaa); y+=11; }
*///?}
    }
//? if <26.2 {
    public void onClose() { OpticClient.saveConfig(); minecraft.setScreen(parent); }
//?} else {
    /*public void onClose() { OpticClient.saveConfig(); minecraft.gui.setScreen(parent); }
*///?}
}
