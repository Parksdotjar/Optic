package dev.optic.compat;

import dev.optic.ui.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;

/** Opt-in visual regression harness, independent of the normal recording hot path. */
public final class SettingsSmokeTest {
    private static long ready;
    private static int step;
    public static void frame() {
        if(!Boolean.getBoolean("optic.settingsSmoke")) return;
        var mc=Minecraft.getInstance(); long now=System.nanoTime();
        if(mc.getOverlay()!=null) return;
        if(step==0 && mc.screen instanceof TitleScreen) { mc.setScreen(new SettingsScreen(mc.screen)); step++; ready=now; }
        if(now-ready<1_000_000_000L) return;
        if(step==7) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-settings-advanced.png",mc.getMainRenderTarget(),1,message -> dev.optic.OpticClient.LOG.info(message.getString()));
            dev.optic.OpticClient.LOG.info("OPTIC_SETTINGS_SMOKE_FINISHED"); mc.stop(); return;
        }
        if(!(mc.screen instanceof SettingsScreen)) return;
        if(step<=5) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-settings-"+step+".png",mc.getMainRenderTarget(),1,message -> dev.optic.OpticClient.LOG.info(message.getString()));
            if(step<5) {
                String next=switch(step) { case 1 -> "Audio"; case 2 -> "Capture"; case 3 -> "Locations"; default -> "Interface"; };
                for(var child:java.util.List.copyOf(mc.screen.children())) if(child instanceof Button b && b.getMessage().getString().equals(next)) { b.onPress(null); break; }
            }
            step++; ready=now;
        } else {
            mc.setScreen(new dev.optic.ui.VideoAdvancedScreen(mc.screen)); step=7; ready=now;
        }
    }
}
