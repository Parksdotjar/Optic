package dev.optic.ui;
import dev.optic.OpticClient;
import dev.optic.workflow.*;
import dev.optic.recording.RecordingManager;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;

public final class WorkflowScreen extends Screen {
    private final Screen parent; private int x,y,w;
    private String status="Profiles change video, audio enablement and capture settings; review before recording.";
    private static Path profileFile() { return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("optic-profile.json"); }
    public WorkflowScreen(Screen parent) { super(Component.literal("Creator workflow")); this.parent=parent; }
    private boolean idle() { if(OpticClient.manager.state()!=RecordingManager.State.IDLE) { status="Stop recording/replay before applying profiles."; return false; } return true; }
    @Override protected void init() {
        w=Math.min(420,width-24); x=(width-w)/2; y=Math.max(8,(height-270)/2);
        String[] profiles={"YouTube","Cinematic","Low-End PC","High FPS"};
        for(int i=0;i<profiles.length;i++) { String name=profiles[i]; addRenderableWidget(new FlatButton(x+(i%2)*(w/2+2),y+24+(i/2)*24,w/2-2,name,b -> { if(idle()) { CreatorProfile.builtIn(name,OpticClient.config).apply(OpticClient.config); OpticClient.saveConfig(); status=name+" applied. Auto encoder detection/fallback runs at start."; } })); }
        addRenderableWidget(new FlatButton(x,y+74,w/2-2,"Save Custom",b -> { var p=CreatorProfile.capture("Custom",OpticClient.config.snapshot()); status="Saving Custom…"; dev.optic.workflow.WorkflowJobs.execute(() -> { try { CreatorProfile.save(profileFile(),p); minecraft.execute(() -> status="Custom saved (device identity and paths excluded)."); } catch(Exception e) { minecraft.execute(() -> status="Could not save Custom: "+e.getMessage()); } }); }));
        addRenderableWidget(new FlatButton(x+w/2+2,y+74,w/2-2,"Load Custom",b -> { if(!idle()) return; status="Loading Custom…"; dev.optic.workflow.WorkflowJobs.execute(() -> { try { var p=CreatorProfile.load(profileFile()); minecraft.execute(() -> { try { if(idle()) { p.apply(OpticClient.config); OpticClient.saveConfig(); status="Custom applied. Review Audio and Capture."; } } catch(Exception e) { status="Invalid profile: "+e.getMessage(); } }); } catch(Exception e) { minecraft.execute(() -> status="Custom not available: "+e.getMessage()); } }); }));
        var template=new EditBox(font,x,y+117,w,20,Component.literal("Filename template")); template.setMaxLength(160); template.setValue(OpticClient.config.namingTemplate); template.setHint(Component.literal("Blank uses existing naming choices")); addRenderableWidget(template);
        addRenderableWidget(new FlatButton(x,y+141,w/2-2,"Save template",b -> { try { String value=template.getValue(); if(!value.isBlank()) NamingTemplate.resolve(value,OpticClient.config.recordingTitle,"Minecraft",java.time.LocalDateTime.now()); OpticClient.config.namingTemplate=value; OpticClient.saveConfig(); status=value.isBlank()?"Existing naming restored.":"Template saved; applies to full recordings. Clips keep Clip names."; } catch(Exception e) { status=e.getMessage(); } }));
        addRenderableWidget(new FlatButton(x+w/2+2,y+141,w/2-2,"Server label: "+(OpticClient.config.namingServerLabel?"ON":"OFF"),b -> { OpticClient.config.namingServerLabel=!OpticClient.config.namingServerLabel; OpticClient.saveConfig(); b.setMessage(Component.literal("Server label: "+(OpticClient.config.namingServerLabel?"ON":"OFF"))); status="Uses displayed world/server name, not address field. OFF substitutes Minecraft."; }));
        addRenderableWidget(new FlatButton(x,y+237,w,"Done",b -> onClose()));
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) { super.render(g,mx,my,dt); g.drawString(font,"CREATOR WORKFLOW",x,y,0xffffffff); g.drawString(font,"Template: {date}  {time}  {title}  {server}",x,y+103,0xffaaaaaa);
        int lineY=y+170; for(var line:font.split(Component.literal(status),w)) { if(lineY>y+201) break; g.drawString(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
        g.drawString(font,"Shorts portrait framing is not available yet.",x,y+216,0xffaaaaaa);
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) { super.extractRenderState(g,mx,my,dt); g.text(font,"CREATOR WORKFLOW",x,y,0xffffffff); g.text(font,"Template: {date}  {time}  {title}  {server}",x,y+103,0xffaaaaaa);
        int lineY=y+170; for(var line:font.split(Component.literal(status),w)) { if(lineY>y+201) break; g.text(font,line,x,lineY,0xffaaaaaa); lineY+=10; }
        g.text(font,"Shorts portrait framing is not available yet.",x,y+216,0xffaaaaaa);
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
