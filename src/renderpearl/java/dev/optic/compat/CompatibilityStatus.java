package dev.optic.compat;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Presence is not certification. This registry installs no invasive optional mixins. */
public final class CompatibilityStatus {
    public static final List<String> IDS=List.of("flashback","sodium","iris","voicechat","xaerominimap","xaeroworldmap","journeymap");
    public static String installed() {
        var loader=FabricLoader.getInstance(); var entries=new ArrayList<String>();
        for(String id:IDS) loader.getModContainer(id).ifPresent(mod -> entries.add(id+"="+mod.getMetadata().getVersion().getFriendlyString()));
        return entries.isEmpty()?"none":String.join("; ",entries);
    }
    public static Map<String,String> supportFields() {
        var loader=FabricLoader.getInstance(); var c=dev.optic.OpticClient.config; var manager=dev.optic.OpticClient.manager;
        var fields=new LinkedHashMap<String,String>();
        for(String id:List.of("minecraft","fabricloader","optic")) fields.put(id.equals("fabricloader")?"fabric_loader":id,loader.getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        fields.put("os",System.getProperty("os.name")); fields.put("java",System.getProperty("java.version"));
        String gpu=com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().name();
        fields.put("gpu",gpu==null?null:gpu.split("/",2)[0]);
        fields.put("driver",com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().driverInfo());
        fields.put("state",manager.state().name()); fields.put("encoder",manager.detectedEncoder);
        fields.put("resolution",c.resolution.name()); fields.put("fps",Integer.toString(c.fps));
        fields.put("quality",c.quality.name()); fields.put("color",c.colorMode.name());
        fields.put("microphone_enabled",Boolean.toString(c.microphone)); fields.put("game_audio_enabled",Boolean.toString(c.gameAudio)); fields.put("replay_enabled",Boolean.toString(c.replayEnabled));
        fields.put("optional_mods",installed());
        fields.put("ffmpeg",dev.optic.encoding.FFmpegManager.lastVersion());
        manager.supportFields(fields);
        return fields;
    }
    private CompatibilityStatus() {}
}



