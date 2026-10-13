package dev.optic.compat;

import dev.optic.OpticClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Explicit developer-only test. Run against a disposable COPY of a world. Never enabled by normal clients. */
public final class CleanWorldSmokeTest {
    private static long ready,started,lastSound;
    private static int stage=-1;
    private static boolean requested,stopped,finished;
    public static void frame() {
        if(!Boolean.getBoolean("optic.cleanWorldSmoke") || finished) return;
        var mc=Minecraft.getInstance(); long now=System.nanoTime();
        if(mc.level==null || mc.player==null || mc.getOverlay()!=null) return;
        if(!requested) {
            if(ready==0) ready=now;
            if(now-ready<5_000_000_000L) return;
            requested=true; mc.setScreen(null);
            mc.options.pauseOnLostFocus=false;
            mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
            mc.options.showSubtitles().set(true);
            org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().handle());
            OpticClient.config.microphone=false; OpticClient.config.completionScreen=false; OpticClient.config.hud=true;
            OpticClient.config.fps=30; OpticClient.config.resolution=dev.optic.config.RecorderConfig.Resolution.P1080;
            OpticClient.manager.directory=mc.gameDirectory.toPath().resolve("v4-validation");
            mc.gui.getChat().addMessage(Component.literal("OPTIC CHAT — stays on the player screen"));
            var board=mc.level.getScoreboard();
            var objective=board.addObjective("optic_v4",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,Component.literal("OPTIC SCORE"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
            board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("Visible to player"),objective).set(42);
            board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR,objective);
            var boss=new net.minecraft.world.BossEvent(java.util.UUID.randomUUID(),Component.literal("OPTIC BOSS"),net.minecraft.world.BossEvent.BossBarColor.RED,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS) {};
            boss.setProgress(.65f); mc.gui.getBossOverlay().update(net.minecraft.network.protocol.game.ClientboundBossEventPacket.createAddPacket(boss));
            OpticClient.manager.toggle();
        }
        if(OpticClient.manager.recording()) {
            if(started==0) started=now;
            mc.gui.setOverlayMessage(Component.literal("OPTIC ACTION BAR"),false);
            if(now-lastSound>1_000_000_000L) {
                lastSound=now;
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.COW_AMBIENT,1));
            }
            int next=(int)((now-started)/3_000_000_000L);
            if(next!=stage) {
                if(stage>=0) net.minecraft.client.Screenshot.grab(mc.gameDirectory,"optic-v4-player-"+stage+".png",mc.getMainRenderTarget(),1,m -> OpticClient.LOG.info(m.getString()));
                stage=next;
                var c=OpticClient.config;
                c.captureHud=stage!=8; c.captureChat=stage!=1 && stage!=9 && stage!=10 && stage!=12; c.captureSubtitles=stage!=2 && stage!=9;
                c.captureScoreboard=stage!=3 && stage!=9; c.captureBosses=stage!=4 && stage!=9;
                c.captureActionBar=stage!=5 && stage!=9; c.captureDebug=stage!=6 && stage!=9; c.captureIndicator=stage!=7 && stage!=9 && stage<11;
                mc.debugEntries.setOverlayVisible(stage==6 || stage==9);
                if(stage==10) mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("input stays visible",false));
                if(stage==11) mc.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true));
                if(stage==12) mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                if(stage==13) { mc.setScreen(null); org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),960,540); }
                if(stage==14) mc.reloadResourcePacks();
                if(stage==15) { org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),1920,1080); c.captureIndicator=true; }
                OpticClient.LOG.info("OPTIC_V4_STAGE {} {}",stage,CleanCapture.diagnostics());
            }
            if(stage>=16) { OpticClient.manager.stop(); stopped=true; }
        }
        if(stopped && OpticClient.manager.state()==dev.optic.recording.RecordingManager.State.IDLE) {
            finished=true; mc.options.showSubtitles().set(false); mc.options.pauseOnLostFocus=true; mc.debugEntries.setOverlayVisible(false);
            OpticClient.LOG.info("OPTIC_V4_WORLD_FINISHED {}",CleanCapture.diagnostics()); mc.stop();
        }
    }
}
