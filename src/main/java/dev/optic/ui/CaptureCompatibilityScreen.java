package dev.optic.ui;
import net.minecraft.client.gui.screens.Screen;
//? if <26.2 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;

public final class CaptureCompatibilityScreen extends Screen {
    private final Screen parent;
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines=java.util.List.of();
    public CaptureCompatibilityScreen(Screen parent) { super(Component.literal("Capture compatibility")); this.parent=parent; }
    protected void init() {
        addRenderableWidget(new FlatButton(width/2-100,height-30,200,"Done",b -> onClose()));
        String mods=dev.optic.compat.CompatibilityStatus.installed();
        String text="CAPTURE ELEMENTS\n\nVanilla HUD, chat history, subtitles, scoreboard, boss bars, action bar, 2D debug UI and Optic indicator: supported.\n\nPlayer names / world labels / waypoints: unavailable.\nOther mod overlays: no validated adapters.\nMenus, chat input, hands and world-space debug graphics remain visible.\n\nDetected optional mods: "+(mods.isEmpty()?"none":mods)+"\nNo optional mod combination is certified.\n\nClean mode adds a GPU copy and an extra GUI pass on captured frames; no second world render.\nThis is not a privacy guarantee against unknown overlays.";
        lines=font.split(Component.literal(text),Math.min(440,width-32));
    }
//? if <26.2 {
    public void renderBackground(GuiGraphics g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    public void render(GuiGraphics g,int mx,int my,float dt) {
        super.render(g,mx,my,dt); int x=Math.max(16,width/2-220),y=20;
        for(var line:lines) { if(y>height-44) break; g.drawString(font,line,x,y,0xffdddddd); y+=11; }
//?} else {
    /*public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt) { g.fill(0,0,width,height,0xf2080808); }
    public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); int x=Math.max(16,width/2-220),y=20;
        for(var line:lines) { if(y>height-44) break; g.text(font,line,x,y,0xffdddddd); y+=11; }
*///?}
    }
//? if <26.2 {
    public void onClose() { minecraft.setScreen(parent); }
//?} else {
    /*public void onClose() { minecraft.gui.setScreen(parent); }
*///?}
}
