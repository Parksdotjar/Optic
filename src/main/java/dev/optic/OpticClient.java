package dev.optic;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.optic.config.RecorderConfig;
import dev.optic.recording.RecordingManager;
import dev.optic.ui.PauseControls;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if <26.2 {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.concurrent.*;
//? if <26.2 {
import org.lwjgl.glfw.GLFW;
//?} else {
/*import com.mojang.blaze3d.platform.InputConstants;
*///?}

public final class OpticClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Optic");
    public static RecorderConfig config;
    public static RecordingManager manager;
    private static KeyMapping clipBinding;
    private static KeyMapping markerBinding;
    private static KeyMapping pauseBinding;
    public static String markerKeyLabel() { return markerBinding==null?"F7":markerBinding.getTranslatedKeyMessage().getString(); }
    public static boolean markerKeyConflict() {
        if(markerBinding==null || markerBinding.isUnbound()) return false;
        for(var mapping:Minecraft.getInstance().options.keyMappings) if(mapping!=markerBinding && mapping.same(markerBinding)) return true;
        return false;
    }
    public static String clipKeyLabel() { return clipBinding==null?"F10":clipBinding.getTranslatedKeyMessage().getString(); }
    public static boolean clipKeyConflict() {
        if(clipBinding==null || clipBinding.isUnbound()) return false;
        for(var mapping:Minecraft.getInstance().options.keyMappings) if(mapping!=clipBinding && mapping.same(clipBinding)) return true;
        return false;
    }
    public static volatile java.util.List<String> microphones=java.util.List.of("Default Input");
    private static final java.util.concurrent.atomic.AtomicBoolean microphoneScanPending=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile long microphoneScanAfter;
    /** Native audio discovery is demand-driven: never race Java Sound/driver threads with LWJGL bootstrap. */
    public static void refreshMicrophones() {
        if(System.nanoTime()<microphoneScanAfter || !microphoneScanPending.compareAndSet(false,true)) return;
        io.execute(() -> {
            try { microphones=dev.optic.audio.MicrophoneCapture.devices(); LOG.debug("Microphone discovery finished ({} choices)",microphones.size()); }
            catch(Exception | LinkageError e) { LOG.warn("Input enumeration failed; retaining previous device list",e); }
            finally { microphoneScanAfter=System.nanoTime()+5_000_000_000L; microphoneScanPending.set(false); }
        });
    }
    public static final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "Optic control"); t.setDaemon(true); return t; });
    private static Path configPath;
    private static boolean saveWarning;
    public static void saveConfig() { var snapshot=config.snapshot(); io.execute(() -> {
        if(snapshot.save(configPath)) saveWarning=false;
        else if(!saveWarning) { saveWarning=true; notice("Settings could not be saved. Original file retained; check access or use the matching Optic version."); }
    }); }
    public static void notice(String message) {
        LOG.info(message);
        var mode=config==null?RecorderConfig.Notifications.SIMPLE:config.notifications;
        if(mode==RecorderConfig.Notifications.OFF) return;
        if(mode==RecorderConfig.Notifications.SIMPLE) { dev.optic.ui.SimpleNotice.show(message); return; }
//? if <26.2 {
        Minecraft.getInstance().execute(() -> SystemToast.add(Minecraft.getInstance().getToastManager(), new SystemToast.SystemToastId(), Component.literal("Optic"), Component.literal(message)));
//?} else {
        /*Minecraft.getInstance().execute(() -> SystemToast.add(Minecraft.getInstance().gui.toastManager(), new SystemToast.SystemToastId(), Component.literal("Optic"), Component.literal(message)));
*///?}
    }
    @Override public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("optic.json");
        config = RecorderConfig.load(configPath);
        if(FabricLoader.getInstance().isDevelopmentEnvironment() && !System.getProperty("optic.benchmark","").isEmpty()) config.replayEnabled=false;
        Path recordings = FabricLoader.getInstance().getGameDir().resolve("recordings");
        if(!config.recordingsFolder.isBlank()) try { recordings=Path.of(config.recordingsFolder).toAbsolutePath().normalize(); } catch(Exception e) { LOG.warn("Invalid recording folder; using default",e); }
        try { Files.createDirectories(recordings); } catch (Exception e) { LOG.warn("Cannot create recordings directory", e); }
        manager = new RecordingManager(recordings, OpticClient::notice);
//? if <26.2 {
        var key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.optic.toggle", GLFW.GLFW_KEY_F9, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("optic", "recording"))));
        var clipKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.optic.clip",GLFW.GLFW_KEY_F10,key.getCategory()));
//?} else {
        /*var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.optic.toggle", InputConstants.KEY_F9, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("optic", "recording"))));
        var clipKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.optic.clip",InputConstants.KEY_F10,key.getCategory()));
*///?}
        clipBinding=clipKey;
//? if <26.2 {
        markerBinding=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.optic.marker",GLFW.GLFW_KEY_F7,key.getCategory()));
        pauseBinding=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.optic.pause",GLFW.GLFW_KEY_F8,key.getCategory()));
//?} else {
        /*markerBinding=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.optic.marker",InputConstants.KEY_F7,key.getCategory()));
        pauseBinding=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.optic.pause",InputConstants.KEY_F8,key.getCategory()));
*///?}
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while(markerBinding.consumeClick()) manager.addMarker(""); });
        ClientTickEvents.END_CLIENT_TICK.register(client -> { while (key.consumeClick()) manager.toggle(); while(pauseBinding.consumeClick()) manager.togglePause(); while(clipKey.consumeClick()) manager.saveClip(); manager.tickReplay(); });
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h) -> {
            {
                boolean[] held={false},clipHeld={false},markerHeld={false},pauseHeld={false};
                net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register((s,event) -> {
                    if(markerBinding.matches(event) && !(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox)) { if(!markerHeld[0]) { markerHeld[0]=true; manager.addMarker(""); } return false; }
                    if(clipKey.matches(event)) { if(!clipHeld[0]) { clipHeld[0]=true; manager.saveClip(); } return false; }
                    if(pauseBinding.matches(event)) { if(!pauseHeld[0]) { pauseHeld[0]=true; manager.togglePause(); } return false; }
                    if(!key.matches(event) || (!(screen instanceof net.minecraft.client.gui.screens.PauseScreen) && manager.state()==RecordingManager.State.IDLE)) return true;
                    if(!held[0]) { held[0]=true; manager.toggle(); } return false;
                });
                net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyRelease(screen).register((s,event) -> { if(key.matches(event)) held[0]=false; if(clipKey.matches(event)) clipHeld[0]=false; if(markerBinding.matches(event)) markerHeld[0]=false; if(pauseBinding.matches(event)) pauseHeld[0]=false; });
            }
        });
        PauseControls.register();
        manager.recover();
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("optic","recording"),(graphics,tick) -> {
            String notice=dev.optic.ui.SimpleNotice.current();
            if(notice!=null) {
                int scope=dev.optic.compat.CleanCapture.enter(dev.optic.capture.CapturePolicy.Element.INDICATOR);
                try {
                    var font=Minecraft.getInstance().font;
                    int right=Minecraft.getInstance().getWindow().getGuiScaledWidth()-8, width=font.width(notice), left=right-width-12;
                    graphics.fill(left-1,7,right+1,8+font.lineHeight+8,0xff303030);
                    graphics.fill(left,8,right,8+font.lineHeight+6,0xeb0b0b0b);
//? if <26.2 {
                    graphics.drawString(font,notice,left+6,11,0xffffffff);
//?} else {
                    /*graphics.text(font,notice,left+6,11,0xffffffff);
*///?}
                } finally { dev.optic.compat.CleanCapture.exit(scope); }
            }
            if(System.nanoTime()<manager.markerFeedbackUntil) {
                int scope=dev.optic.compat.CleanCapture.enter(dev.optic.capture.CapturePolicy.Element.INDICATOR);
//? if <26.2 {
                try { graphics.drawString(Minecraft.getInstance().font,manager.markerFeedback,6,34,0xffdddddd); }
//?} else {
                /*try { graphics.text(Minecraft.getInstance().font,manager.markerFeedback,6,34,0xffdddddd); }
*///?}
                finally { dev.optic.compat.CleanCapture.exit(scope); }
            }
            if(config.hud && manager.fullRecording()) {
                int scope=dev.optic.compat.CleanCapture.enter(dev.optic.capture.CapturePolicy.Element.INDICATOR);
                try {
//? if <26.2 {
                graphics.drawString(Minecraft.getInstance().font,(manager.paused()?"Ⅱ PAUSED ":"● REC ")+manager.elapsed(),6,6,manager.paused()?0xffdddddd:0xffff5555);
                if(config.hudDetails) graphics.drawString(Minecraft.getInstance().font,manager.details(),6,18,0xffdddddd);
//?} else {
                /*graphics.text(Minecraft.getInstance().font,(manager.paused()?"Ⅱ PAUSED ":"● REC ")+manager.elapsed(),6,6,manager.paused()?0xffdddddd:0xffff5555);
                if(config.hudDetails) graphics.text(Minecraft.getInstance().font,manager.details(),6,18,0xffdddddd);
*///?}
                } finally { dev.optic.compat.CleanCapture.exit(scope); }
            }
        });
        LOG.info("Optic initialized for Minecraft {}", FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
        if(Boolean.getBoolean("optic.cleanSmoke")) net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client,screen,w,h) -> {
//? if <26.2 {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s,g,mx,my,dt) -> {
                g.fill(10,40,130,65,0xff00ff00); g.drawString(client.font,"KEPT",15,45,0xff000000);
//?} else {
            /*net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((s,g,mx,my,dt) -> {
                g.fill(10,40,130,65,0xff00ff00); g.text(client.font,"KEPT",15,45,0xff000000);
*///?}
                int scope=dev.optic.compat.CleanCapture.enter(dev.optic.capture.CapturePolicy.Element.CHAT);
//? if <26.2 {
                try { g.fill(10,70,130,95,0xffff0000); g.drawString(client.font,"EXCLUDED CHAT",15,75,0xffffffff); }
//?} else {
                /*try { g.fill(10,70,130,95,0xffff0000); g.text(client.font,"EXCLUDED CHAT",15,75,0xffffffff); }
*///?}
                finally { dev.optic.compat.CleanCapture.exit(scope); }
            });
        });
    }
}
