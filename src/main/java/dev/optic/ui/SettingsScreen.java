package dev.optic.ui;

import dev.optic.OpticClient;
import dev.optic.config.RecorderConfig;
import dev.optic.recording.RecordingManager;
import dev.optic.audio.WindowsProcessAudio;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;

/** Compact navigation, one settings group at a time. No audio samples or filesystem IO on render. */
public final class SettingsScreen extends Screen {
    private enum Tab { RECORDING, REPLAY, AUDIO, CAPTURE, LOCATIONS, INTERFACE }
    private final Screen parent;
    private Tab tab=Tab.RECORDING;
    private int left,top,side,content,span,bottom;
    private EditBox folder;
    private String storageStatus="Changes apply to new recordings.";
    public SettingsScreen(Screen parent) { super(Component.translatable("optic.settings")); this.parent=parent; }
    @Override protected void init() {
        if(tab==Tab.AUDIO) OpticClient.refreshMicrophones();
        int panel=Math.min(560,width-16); left=(width-panel)/2; top=Math.max(8,(height-264)/2); bottom=Math.min(height-8,top+264);
        side=Math.min(108,panel/3); content=left+side+14; span=panel-side-28;
        int i=0;
        for(var section:Tab.values()) {
            String label=tr("optic.tab."+section.name().toLowerCase(java.util.Locale.ROOT));
            var button=new TabButton(left+6,top+34+i++*Math.min(25,Math.max(20,(bottom-top-130)/4)),side-12,label,section==tab,b -> { OpticClient.saveConfig(); tab=section; rebuildWidgets(); });
            addRenderableWidget(button);
        }
        addRenderableWidget(new FolderButton(left+6,bottom-74,side-12,"Details",b -> RecordingSavedScreen.open(OpticClient.manager.detailsDirectory())));
        addRenderableWidget(new FolderButton(left+6,bottom-50,side-12,"Recordings",b -> RecordingSavedScreen.open(OpticClient.manager.directory)));
        addRenderableWidget(new FlatButton(left+6,bottom-26,side-12,Component.translatable("gui.done"),b -> onClose()));
        var c=OpticClient.config; int y=top+36;
        switch(tab) {
            case REPLAY -> {
                row(y,"Instant Replay: "+on(c.replayEnabled),() -> { OpticClient.manager.setReplay(!c.replayEnabled); rebuildWidgets(); });
                row(y+26,"History: "+(c.replaySeconds<60?c.replaySeconds+" seconds":c.replaySeconds/60+" minutes"),() -> {
                    if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { OpticClient.notice("Switch replay OFF and wait before changing history length."); return; }
                    int[] choices={15,30,60,120,300}; int next=0; for(int n=0;n<choices.length;n++) if(choices[n]==c.replaySeconds) next=(n+1)%choices.length;
                    c.replaySeconds=choices[next]; refresh();
                });
                row(y+52,"Save Clip",() -> OpticClient.manager.saveClip());
            }
            case CAPTURE -> {
                row(y,"Minecraft HUD: "+on(c.captureHud),() -> { c.captureHud=!c.captureHud; refresh(); });
                String[] labels={"Chat","Subtitles","Scoreboard","Boss bars","Action bar","Debug display"};
                boolean[] values={c.captureChat,c.captureSubtitles,c.captureScoreboard,c.captureBosses,c.captureActionBar,c.captureDebug};
                for(int n=0;n<labels.length;n++) { final int index=n;
                    addRenderableWidget(new FlatButton(content+(n%2)*(span/2+2),y+26+(n/2)*24,span/2-2,labels[n]+": "+on(c.captureHud && values[n]),b -> {
                        switch(index) { case 0 -> c.captureChat=!c.captureChat; case 1 -> c.captureSubtitles=!c.captureSubtitles; case 2 -> c.captureScoreboard=!c.captureScoreboard; case 3 -> c.captureBosses=!c.captureBosses; case 4 -> c.captureActionBar=!c.captureActionBar; case 5 -> c.captureDebug=!c.captureDebug; } refresh();
                    })).active=c.captureHud;
                }
                row(y+102,"Recording indicator: "+on(c.captureIndicator),() -> { c.captureIndicator=!c.captureIndicator; refresh(); });
//? if <26.2 {
                row(y+128,"Compatibility / unavailable elements",() -> minecraft.setScreen(new CaptureCompatibilityScreen(this)));
//?} else {
                /*row(y+128,"Compatibility / unavailable elements",() -> minecraft.gui.setScreen(new CaptureCompatibilityScreen(this)));
*///?}
            }
            case RECORDING -> {
                row(y,tr("optic.setting.quality",c.preset.label),() -> { var presets=dev.optic.config.RecordingPreset.values(); presets[(c.preset.ordinal()+1)%presets.length].apply(c); refresh(); });
                row(y+24,tr("optic.setting.resolution",c.resolution),() -> { c.resolution=RecorderConfig.Resolution.values()[(c.resolution.ordinal()+1)%3]; c.preset=dev.optic.config.RecordingPreset.CUSTOM; refresh(); });
                addRenderableWidget(new FlatButton(content,y+48,span/2-2,c.fps+" FPS",b -> { c.fps=c.fps==30?60:c.fps==60?120:30; c.preset=dev.optic.config.RecordingPreset.CUSTOM; refresh(); }));
//? if <26.2 {
                addRenderableWidget(new FlatButton(content+span/2+2,y+48,span/2-2,Component.translatable("optic.advanced"),b -> minecraft.setScreen(new VideoAdvancedScreen(this))));
//?} else {
                /*addRenderableWidget(new FlatButton(content+span/2+2,y+48,span/2-2,Component.translatable("optic.advanced"),b -> minecraft.gui.setScreen(new VideoAdvancedScreen(this))));
*///?}
                var title=new EditBox(font,content,y+84,span,20,Component.literal("Recording title")); title.setMaxLength(80); title.setHint(Component.literal("Recording title (optional)")); title.setValue(c.recordingTitle);
                title.setResponder(value -> c.recordingTitle=value); addRenderableWidget(title);
                row(y+110,"Naming: "+c.namingStyle.label(),() -> { var styles=dev.optic.recording.RecordingStorage.Style.values(); c.namingStyle=styles[(c.namingStyle.ordinal()+1)%styles.length]; refresh(); });
//? if <26.2 {
                addRenderableWidget(new FlatButton(content,y+138,span/2-2,"Library",b -> minecraft.setScreen(new LibraryScreen(this))));
                addRenderableWidget(new FlatButton(content+span/2+2,y+138,span/2-2,"Profiles / naming",b -> minecraft.setScreen(new WorkflowScreen(this))));
                addRenderableWidget(new FlatButton(content,y+162,span/2-2,"Last recording",b -> minecraft.setScreen(new RecordingSavedScreen(this,OpticClient.manager.lastRecording)))).active=OpticClient.manager.lastRecording!=null;
                addRenderableWidget(new FlatButton(content+span/2+2,y+162,span/2-2,Component.translatable("optic.diagnostics"),b -> minecraft.setScreen(new DiagnosticsScreen(this))));
//?} else {
                /*addRenderableWidget(new FlatButton(content,y+138,span/2-2,"Library",b -> minecraft.gui.setScreen(new LibraryScreen(this))));
                addRenderableWidget(new FlatButton(content+span/2+2,y+138,span/2-2,"Profiles / naming",b -> minecraft.gui.setScreen(new WorkflowScreen(this))));
                addRenderableWidget(new FlatButton(content,y+162,span/2-2,"Last recording",b -> minecraft.gui.setScreen(new RecordingSavedScreen(this,OpticClient.manager.lastRecording)))).active=OpticClient.manager.lastRecording!=null;
                addRenderableWidget(new FlatButton(content+span/2+2,y+162,span/2-2,Component.translatable("optic.diagnostics"),b -> minecraft.gui.setScreen(new DiagnosticsScreen(this))));
*///?}
            }
            case AUDIO -> {
                var game=row(y,"Minecraft audio: "+(c.gameAudio?"ON":"OFF"),() -> { c.gameAudio=!c.gameAudio; refresh(); });
                game.active=WindowsProcessAudio.supported();
                game.setTooltip(Tooltip.create(Component.literal(WindowsProcessAudio.supported()?"Minecraft and mod sounds only. Other apps are excluded. Voice chat played by mods is included.":"Game capture currently requires Windows x64 build 20348 or newer.")));
                row(y+26,"Microphone: "+(c.microphone?"ON":"OFF"),() -> { c.microphone=!c.microphone; refresh(); });
                var device=row(y+52,c.microphoneDevice,() -> { var devices=OpticClient.microphones; c.microphoneDevice=devices.get((devices.indexOf(c.microphoneDevice)+1)%devices.size()); refresh(); });
                device.setTooltip(Tooltip.create(Component.literal("Input: "+c.microphoneDevice+" (next recording)")));
                addRenderableWidget(new FlatButton(content,y+78,span/2-2,"Gain: "+Math.round(c.microphoneGain*100)+"%",b -> { c.microphoneGain=c.microphoneGain>=2?0:c.microphoneGain+.25; liveAudio(); refresh(); }));
                addRenderableWidget(new FlatButton(content+span/2+2,y+78,span/2-2,"Mute: "+(c.microphoneMuted?"ON":"OFF"),b -> { c.microphoneMuted=!c.microphoneMuted; liveAudio(); refresh(); }));
                row(y+104,"Reconnect selected input: "+(c.microphoneReconnect?"ON":"OFF"),() -> { c.microphoneReconnect=!c.microphoneReconnect; refresh(); });
            }
            case LOCATIONS -> {
                folder=new EditBox(font,content,y+16,span,20,Component.literal("Recording folder path")); folder.setMaxLength(2048); folder.setValue(OpticClient.manager.directory.toString()); addRenderableWidget(folder);
                addRenderableWidget(new FlatButton(content,y+44,span/2-2,"Apply folder",b -> applyFolder(folder.getValue())));
                addRenderableWidget(new FlatButton(content+span/2+2,y+44,span/2-2,"Use default",b -> applyFolder(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("recordings").toString())));
                var details=new EditBox(font,content,y+94,span,20,Component.literal("Details folder path")); details.setMaxLength(2048); details.setValue(OpticClient.manager.detailsDirectory().toString()); details.setEditable(false); addRenderableWidget(details);
            }
            case INTERFACE -> {
                row(y,"Recording HUD: "+(c.hud?"ON":"OFF"),() -> { c.hud=!c.hud; refresh(); });
                row(y+26,"HUD details: "+(c.hudDetails?"ON":"OFF"),() -> { c.hudDetails=!c.hudDetails; refresh(); });
                row(y+52,"Completion screen: "+(c.completionScreen?"ON":"OFF"),() -> { c.completionScreen=!c.completionScreen; refresh(); });
                row(y+78,"Notifications: "+c.notifications,() -> { var all=dev.optic.config.RecorderConfig.Notifications.values(); c.notifications=all[(c.notifications.ordinal()+1)%all.length]; refresh(); });
//? if <26.2 {
                row(y+104,"Markers / timeline",() -> minecraft.setScreen(new MarkersScreen(this)));
//?} else {
                /*row(y+104,"Markers / timeline",() -> minecraft.gui.setScreen(new MarkersScreen(this)));
*///?}
            }
        }
    }
    private FlatButton row(int y,String label,Runnable action) { return addRenderableWidget(new FlatButton(content,y,span,label,b -> action.run())); }
    private static String on(boolean value) { return value?"ON":"OFF"; }
    private static String tr(String key,Object... args) { return Component.translatable(key,args).getString(); }
    private void liveAudio() { OpticClient.manager.microphoneControls.configure(OpticClient.config.microphoneGain,OpticClient.config.microphoneMuted); }
    private void applyFolder(String value) {
        if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { storageStatus="Stop recording before changing folders."; return; }
        storageStatus="Checking folder…";
        OpticClient.io.execute(() -> {
            try {
                if(value.isBlank()) throw new java.io.IOException("Enter a folder path.");
                Path path=Path.of(value).toAbsolutePath().normalize(); Files.createDirectories(path);
                if(path.getParent()==null) throw new java.io.IOException("Choose a folder, not a drive root.");
                Files.createDirectories(dev.optic.recording.RecordingStorage.details(path));
                Path probe=Files.createTempFile(path,".optic-write-check-",".tmp"); Files.delete(probe);
                minecraft.execute(() -> {
                    if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { storageStatus="Stop recording before changing folders."; return; }
                    OpticClient.manager.directory=path; OpticClient.config.recordingsFolder=path.toString(); OpticClient.saveConfig();
                    storageStatus="Folder saved. Existing files stay where they are."; rebuildWidgets();
                });
            } catch(Exception e) { minecraft.execute(() -> storageStatus="Could not use that folder. Check the path and access."); OpticClient.LOG.warn("Recording folder change failed",e); }
        });
    }
    private void refresh() { OpticClient.saveConfig(); rebuildWidgets(); }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt);
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt);
*///?}
        g.fill(left+side,top,left+side+1,bottom,0xff303030);
//? if <26.2 {
        g.drawString(font,"OPTIC",left+10,top+12,0xffffffff);
        g.drawString(font,tr("optic.tab."+tab.name().toLowerCase(java.util.Locale.ROOT)),content,top+12,0xffffffff);
//?} else {
        /*g.text(font,"OPTIC",left+10,top+12,0xffffffff);
        g.text(font,tr("optic.tab."+tab.name().toLowerCase(java.util.Locale.ROOT)),content,top+12,0xffffffff);
*///?}
        int y=top+36;
        switch(tab) {
//? if <26.2 {
            case CAPTURE -> note(g,"Video only; your screen stays unchanged. Applies next frame. HUD OFF also hides its child elements. Menus still appear.",y+157);
//?} else {
            /*case CAPTURE -> note(g,"Video only; your screen stays unchanged. Clean capture uses one GPU copy and one filtered GUI pass, never a second world render.",y+157);
*///?}
            case RECORDING -> {
                var c=OpticClient.config;
                note(g,c.quality.label+" quality · "+dev.optic.encoding.EncoderDetector.label(c.encoder)+" · Impact: not measured. Video settings apply to new recordings.",y+188);
            }
            case AUDIO -> {
                var audio=OpticClient.manager.microphoneControls;
                g.fill(content,y+134,content+span,y+140,0xff303030);
                g.fill(content,y+134,content+(int)(Math.min(1,audio.peak)*span),y+140,System.nanoTime()<audio.clippingUntil?0xffee5555:0xffdddddd);
                note(g,System.nanoTime()<audio.clippingUntil?"Clipping — lower microphone gain.":audio.status,y+148);
                note(g,"Separate Minecraft + microphone tracks. Voice chat is included in Minecraft audio; isolated voices/categories are not available.",y+166);
            }
            case LOCATIONS -> {
//? if <26.2 {
                g.drawString(font,"Recording folder",content,y,0xffbbbbbb);
                g.drawString(font,"Details folder",content,y+78,0xffbbbbbb);
//?} else {
                /*g.text(font,"Recording folder",content,y,0xffbbbbbb);
                g.text(font,"Details folder",content,y+78,0xffbbbbbb);
*///?}
                note(g,storageStatus,y+128);
                note(g,"Recordings holds videos only. Logs and recovery files stay in the separate details folder.",y+157);
            }
            case REPLAY -> {
                note(g,OpticClient.manager.replayStatus(),y+82);
                note(g,OpticClient.clipKeyLabel()+" saves recent gameplay. "+(OpticClient.clipKeyConflict()?"Binding conflict detected! Change it in Minecraft Controls.":"Change the binding in Minecraft Controls."),y+112);
                note(g,"Uses your video/audio settings and continuously encodes. Up to 2 GiB temporary/export budget; shorter history under pressure. Full recordings use additional disk space.",y+145);
                note(g,"Stop a full recording to save it; replay then restarts. Change video/audio sources with replay OFF.",y+194);
            }
            case INTERFACE -> note(g,"Off hides Optic messages. Simple shows brief gray status messages in the top-right. Advanced uses Minecraft's full colored toasts.",y+140);
        }
    }
//? if <26.2 {
    private void note(GuiGraphics g,String text,int y) {
        for(var line:font.split(Component.literal(text),span)) { if(y>bottom-10) break; g.drawString(font,line,content,y,0xffaaaaaa); y+=11; }
//?} else {
    /*private void note(GuiGraphicsExtractor g,String text,int y) {
        for(var line:font.split(Component.literal(text),span)) { if(y>bottom-10) break; g.text(font,line,content,y,0xffaaaaaa); y+=11; }
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { OpticClient.saveConfig(); minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { OpticClient.saveConfig(); minecraft.gui.setScreen(parent); }
*///?}
    private static final class TabButton extends FlatButton {
        private final boolean selected;
        TabButton(int x,int y,int w,String label,boolean selected,OnPress action) { super(x,y,w,label,action); this.selected=selected; }
//? if <26.2 {
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float dt) {
//?} else {
        /*@Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt) {
*///?}
            int x=getX(),y=getY(); if(selected || isHoveredOrFocused()) g.fill(x,y,x+getWidth(),y+20,0xff171717);
            if(selected) g.fill(x,y+3,x+2,y+17,0xffdddddd);
            if(isFocused()) { g.fill(x,y,x+getWidth(),y+1,0xffbbbbbb); g.fill(x,y+19,x+getWidth(),y+20,0xffbbbbbb); }
            var font=net.minecraft.client.Minecraft.getInstance().font;
//? if <26.2 {
            g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),getWidth()-12),x+8,y+6,selected?0xffffffff:0xffaaaaaa);
//?} else {
            /*g.text(font,font.plainSubstrByWidth(getMessage().getString(),getWidth()-12),x+8,y+6,selected?0xffffffff:0xffaaaaaa);
*///?}
        }
    }
    private static final class FolderButton extends FlatButton {
        FolderButton(int x,int y,int w,String label,OnPress action) { super(x,y,w,label,action); setTooltip(Tooltip.create(Component.literal("Open "+label.toLowerCase(java.util.Locale.ROOT)+" folder"))); }
//? if <26.2 {
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float dt) {
//?} else {
        /*@Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt) {
*///?}
            int x=getX(),y=getY(); g.fill(x,y,x+getWidth(),y+20,isHoveredOrFocused()?0xff171717:0xff0b0b0b);
            g.fill(x+6,y+5,x+11,y+7,0xffbbbbbb); g.fill(x+5,y+7,x+17,y+15,0xffbbbbbb); g.fill(x+6,y+9,x+16,y+14,0xff171717);
//? if <26.2 {
            g.drawString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),x+21,y+6,0xffffffff);
//?} else {
            /*g.text(net.minecraft.client.Minecraft.getInstance().font,getMessage(),x+21,y+6,0xffffffff);
*///?}
        }
    }
}
