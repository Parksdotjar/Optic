package dev.optic.compat;
import dev.optic.OpticClient;
import dev.optic.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/** Visual smoke only; no production recording settings are changed. */
public final class WorkflowSmokeTest {
    private static int stage; private static long ready;
    public static void frame() {
        if(!Boolean.getBoolean("optic.workflowSmoke")) return;
        var mc=Minecraft.getInstance(); if(mc.getOverlay()!=null) return;
        long now=System.nanoTime();
        if(Boolean.getBoolean("optic.menuSmoke")) {
            if(stage==0 && mc.level!=null) { mc.getWindow().setWindowed(1280,720); mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)); stage=4; ready=now; }
            if(stage==0 && mc.screen instanceof TitleScreen) { mc.getWindow().setWindowed(1280,720); stage=1; ready=now; }
            if(now-ready<2_000_000_000L) return;
            if(stage==1) { net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-title-shortcuts.png",mc.getMainRenderTarget(),1,m -> {}); click("Optic library"); stage=2; ready=now; }
            else if(stage==2) { if(!(mc.screen instanceof LibraryScreen)) throw new IllegalStateException("Title library shortcut failed"); mc.screen.onClose(); click("Optic settings"); stage=3; ready=now; }
            else if(stage==3) { if(!(mc.screen instanceof SettingsScreen)) throw new IllegalStateException("Title settings shortcut failed"); stage=6; OpticClient.LOG.info("OPTIC_TITLE_SHORTCUTS_FINISHED"); mc.stop(); }
            else if(stage==4) { net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-pause-shortcuts.png",mc.getMainRenderTarget(),1,m -> {}); click("Optic library"); stage=5; ready=now; }
            else if(stage==5) { if(!(mc.screen instanceof LibraryScreen)) throw new IllegalStateException("Pause library shortcut failed"); stage=6; OpticClient.LOG.info("OPTIC_MENU_SHORTCUTS_FINISHED"); mc.stop(); }
            return;
        }
        if(stage==0 && mc.screen instanceof TitleScreen) { mc.getWindow().setWindowed(1280,720); mc.setScreen(Boolean.getBoolean("optic.supportSmoke")?new SupportReportScreen(mc.screen):Boolean.getBoolean("optic.advancedSmoke")?new CreatorCaptureScreen(mc.screen):new WorkflowScreen(mc.screen)); stage=1; ready=now; }
        if(now-ready<2_000_000_000L) return;
        if(stage==1) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,Boolean.getBoolean("optic.supportSmoke")?"optic-v10-support.png":"optic-v8-workflow.png",mc.getMainRenderTarget(),1,m -> {});
            if(Boolean.getBoolean("optic.supportSmoke")) { click(net.minecraft.network.chat.Component.translatable("spectatorMenu.next_page").getString()); stage=4; ready=now; return; }
            OpticClient.manager.directory=mc.gameDirectory.toPath().resolve("v6-validation"); mc.setScreen(new LibraryScreen(null)); stage=2; ready=now;
        }
        else if(stage==4) { net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-v10-support-page2.png",mc.getMainRenderTarget(),1,m -> {}); OpticClient.LOG.info("OPTIC_SUPPORT_SMOKE_FINISHED"); stage=5; mc.stop(); }
        else if(stage==2) { net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-v8-library.png",mc.getMainRenderTarget(),1,m -> {}); OpticClient.LOG.info("OPTIC_WORKFLOW_SMOKE_FINISHED"); stage=3; mc.stop(); }
    }
    private static void click(String label) {
        var mc=Minecraft.getInstance();
        for(var child:java.util.List.copyOf(mc.screen.children())) if(child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(label)) { b.onPress(null); return; }
        throw new IllegalStateException("Missing shortcut: "+label);
    }
}
