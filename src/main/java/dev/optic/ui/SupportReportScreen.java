package dev.optic.ui;

import dev.optic.diagnostics.SupportReport;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A fixed snapshot: export writes exactly the paginated preview, never a refreshed/private log. */
public final class SupportReportScreen extends Screen {
    private final Screen parent;
    private final String report;
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines;
    private int page,perPage;
    private boolean busy;
    public SupportReportScreen(Screen parent) {
        super(Component.translatable("optic.support.title")); this.parent=parent;
        report=SupportReport.build(dev.optic.compat.CompatibilityStatus.supportFields(),dev.optic.OpticClient.manager.diagnostics());
    }
    @Override protected void init() {
        lines=font.split(Component.literal(report),Math.max(120,width-40)); perPage=Math.max(1,(height-100)/11); page=Math.min(page,(lines.size()-1)/perPage);
        int x=width/2;
        addRenderableWidget(new FlatButton(x-150,height-54,72,Component.translatable("spectatorMenu.previous_page"),b -> { page--; rebuildWidgets(); })).active=page>0;
        addRenderableWidget(new FlatButton(x+78,height-54,72,Component.translatable("spectatorMenu.next_page"),b -> { page++; rebuildWidgets(); })).active=(page+1)*perPage<lines.size();
        addRenderableWidget(new FlatButton(x-150,height-28,146,Component.translatable("optic.support.export"),b -> export())).active=!busy;
        addRenderableWidget(new FlatButton(x+4,height-28,146,Component.translatable("gui.done"),b -> onClose()));
    }
    private void export() {
        if(busy) return; busy=true; rebuildWidgets();
        var directory=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("optic-support");
        if(!dev.optic.workflow.WorkflowJobs.execute(() -> {
            try { SupportReport.export(directory,report); minecraft.execute(() -> { busy=false; rebuildWidgets(); RecordingSavedScreen.open(directory); }); }
            catch(Exception e) { dev.optic.OpticClient.LOG.warn("Support export failed",e); minecraft.execute(() -> { busy=false; rebuildWidgets(); dev.optic.OpticClient.notice("Support report could not be saved. Check folder access."); }); }
        })) { busy=false; rebuildWidgets(); dev.optic.OpticClient.notice("Worker busy. Try exporting again shortly."); }
    }
//? if <26.2 {
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); g.drawString(font,getTitle(),20,12,0xffffffff);
        for(int i=page*perPage,y=32;i<Math.min(lines.size(),(page+1)*perPage);i++,y+=11) g.drawString(font,lines.get(i),20,y,0xffcccccc);
        g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(lines.size()+perPage-1)/perPage),width/2,height-48,0xffffffff);
//?} else {
    /*@Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.text(font,getTitle(),20,12,0xffffffff);
        for(int i=page*perPage,y=32;i<Math.min(lines.size(),(page+1)*perPage);i++,y+=11) g.text(font,lines.get(i),20,y,0xffcccccc);
        g.centeredText(font,(page+1)+" / "+Math.max(1,(lines.size()+perPage-1)/perPage),width/2,height-48,0xffffffff);
*///?}
    }
//? if <26.2 {
    @Override public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*@Override public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
    @Override protected void updateNarrationState(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        super.updateNarrationState(output);
        if(lines==null) return;
        var text=new StringBuilder();
        for(int i=page*perPage;i<Math.min(lines.size(),(page+1)*perPage);i++) {
            lines.get(i).accept((index,style,codepoint) -> { text.appendCodePoint(codepoint); return true; }); text.append(' ');
        }
        output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT,Component.literal(text.toString()));
    }
}
