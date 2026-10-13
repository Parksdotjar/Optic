package dev.optic.ui;
import dev.optic.OpticClient;
import dev.optic.config.*;
import dev.optic.encoding.*;
import dev.optic.recording.*;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class CreatorCaptureScreen extends Screen {
    private final Screen parent;
    private List<String> codecs=List.of("auto");
    private String status="Check hardware to discover usable HEVC/AV1 encoders. These are optional; verify your editor can import them.";
    private boolean checking;
    private Cancellation probeCancellation=new Cancellation();
    private int x,y,w;
    public CreatorCaptureScreen(Screen parent) { super(Component.literal("Advanced creator capture")); this.parent=parent; }
    @Override protected void init() {
        w=Math.min(400,width-24); x=(width-w)/2; y=Math.max(8,(height-264)/2); var c=OpticClient.config;
        addRenderableWidget(new FlatButton(x,y+24,w,"Check hardware formats",b -> probe())).active=!checking;
        addRenderableWidget(new FlatButton(x,y+50,w,"Format: "+EncoderDetector.label(c.encoder),b -> { if(!idle()) return; c.encoder=codecs.get((codecs.indexOf(c.encoder)+1)%codecs.size()); save(); }));
        addRenderableWidget(new FlatButton(x,y+76,w,"Color: "+c.colorMode.label,b -> { if(!idle()) return; c.colorMode=c.colorMode==ColorMode.LEGACY?ColorMode.SDR_SRGB:ColorMode.LEGACY; codecs=List.of("auto"); save(); }));
        addRenderableWidget(new FlatButton(x,y+102,w,"Stop on sustained duplicates: "+(c.stopOnSustainedDuplicates?"ON":"OFF"),b -> { if(!idle()) return; c.stopOnSustainedDuplicates=!c.stopOnSustainedDuplicates; save(); }));
        addRenderableWidget(new FlatButton(x,y+235,w,"Done",b -> onClose()));
    }
    private boolean idle() { if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { status="Stop recording/replay before changing advanced formats."; return false; } return true; }
    private void save() { OpticClient.config.preset=RecordingPreset.CUSTOM; OpticClient.saveConfig(); rebuildWidgets(); }
    private void probe() {
        if(!idle() || checking) return; checking=true; status="Testing encode, MP4 mux and decode…"; rebuildWidgets();
        probeCancellation=new Cancellation(); var cancellation=probeCancellation;
//? if <26.2 {
        var c=OpticClient.config.snapshot(); var target=minecraft.getMainRenderTarget();
//?} else {
        /*var c=OpticClient.config.snapshot(); var target=minecraft.gameRenderer.mainRenderTarget();
*///?}
        int width=c.resolution==RecorderConfig.Resolution.P1080?1920:c.resolution==RecorderConfig.Resolution.P1440?2560:target.width&~1;
        int height=c.resolution==RecorderConfig.Resolution.P1080?1080:c.resolution==RecorderConfig.Resolution.P1440?1440:target.height&~1;
        if(!dev.optic.workflow.WorkflowJobs.execute(() -> {
            try {
                var root=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("optic-tools"); var ffmpeg=new FFmpegManager(root.resolve("ffmpeg")).obtain(cancellation);
                var available=new ArrayList<String>(); available.add("auto");
                for(String codec:AdvancedEncoders.CANDIDATES) { if(OpticClient.manager.state()!=RecordingManager.State.IDLE) cancellation.cancel(); cancellation.check(); if(AdvancedEncoders.probe(ffmpeg,codec,width,height,c.fps,c.quality,c.colorMode,root.resolve("advanced-probes"),cancellation)) available.add(codec); }
                minecraft.execute(() -> { codecs=List.copyOf(available); checking=false; status=(available.size()-1)+" working advanced encoders at tested size/FPS. Click Format to select; checked again at start. Replay uses H.264."; rebuildWidgets(); });
            } catch(Exception e) { minecraft.execute(() -> { checking=false; status="Hardware check failed: "+e.getMessage(); rebuildWidgets(); }); }
        })) { checking=false; status="Worker busy; retry shortly."; rebuildWidgets(); }
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); g.drawString(font,"ADVANCED CAPTURE",x,y,0xffffffff);
        int lineY=y+133; for(var line:font.split(Component.literal(status),w)) { if(lineY>y+166) break; g.drawString(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.text(font,"ADVANCED CAPTURE",x,y,0xffffffff);
        int lineY=y+133; for(var line:font.split(Component.literal(status),w)) { if(lineY>y+166) break; g.text(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
*///?}
        lineY=y+177; String text="8-bit SDR only. CFR preserves time using repeated frames. Optional stop: >10% duplicates for three 5-second windows. No silent quality changes. HDR, depth/masks, VFR and adaptive quality are not available.";
//? if <26.2 {
        for(var line:font.split(Component.literal(text),w)) { if(lineY>y+224) break; g.drawString(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
//?} else {
        /*for(var line:font.split(Component.literal(text),w)) { if(lineY>y+224) break; g.text(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { probeCancellation.cancel(); minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { probeCancellation.cancel(); minecraft.gui.setScreen(parent); }
*///?}
}
