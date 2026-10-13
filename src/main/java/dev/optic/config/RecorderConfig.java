package dev.optic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.optic.OpticClient;
import java.nio.file.*;

public final class RecorderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public int schemaVersion = 4;
    public enum Resolution { P1080, P1440, NATIVE;
        @Override public String toString() { return switch(this) { case P1080 -> "1080p"; case P1440 -> "1440p"; case NATIVE -> "Native / window"; }; }
    }
    public enum Notifications { OFF, SIMPLE, ADVANCED;
        @Override public String toString() { return switch(this) { case OFF -> "Off"; case SIMPLE -> "Simple"; case ADVANCED -> "Advanced"; }; }
    }
    public Resolution resolution = Resolution.P1080;
    public int fps = 60;
    public boolean replayEnabled=false;
    public int replaySeconds=30;
    public boolean markersEnabled=true,markerContext=false,markerThumbnails=false;
    public dev.optic.markers.Marker.Type markerType=dev.optic.markers.Marker.Type.KEEP;
    public String encoder = "auto";
    public ColorMode colorMode=ColorMode.LEGACY;
    public boolean stopOnSustainedDuplicates=false;
    public RecordingPreset preset=RecordingPreset.CUSTOM;
    public VideoQuality quality=VideoQuality.STANDARD;
    public dev.optic.capture.ScaleLayout.Aspect aspect=dev.optic.capture.ScaleLayout.Aspect.FIT;
    public boolean microphone = false;
    public String microphoneDevice = "Default Input";
    public boolean gameAudio = true;
    public double microphoneGain = 1;
    public boolean microphoneMuted = false;
    public boolean microphoneReconnect = true;
    public boolean completionScreen = true;
    public Notifications notifications = Notifications.SIMPLE;
    public String recordingsFolder = "";
    public String recordingTitle = "";
    public String namingTemplate="";
    public boolean namingServerLabel=false;
    public dev.optic.recording.RecordingStorage.Style namingStyle = dev.optic.recording.RecordingStorage.Style.DEFAULT;
    public boolean hud = true;
    public boolean hudDetails = false;
    public boolean captureHud=true,captureChat=true,captureSubtitles=true,captureScoreboard=true,captureBosses=true,captureActionBar=true,captureDebug=true,captureIndicator=true;
    public dev.optic.capture.CapturePolicy capturePolicy() { return dev.optic.capture.CapturePolicy.of(captureHud,captureChat,captureSubtitles,captureScoreboard,captureBosses,captureActionBar,captureDebug,captureIndicator); }
    public RecorderConfig snapshot() { return GSON.fromJson(GSON.toJson(this),RecorderConfig.class); }
    public static RecorderConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                var document=SettingsStore.read(file);
                RecorderConfig c = GSON.fromJson(document, RecorderConfig.class);
                // V1/V2 stored gameAudio=false for an unavailable control; it was not a user choice.
                if(c!=null && !document.has("schemaVersion")) c.gameAudio=true;
                if (c != null) { c.validate(); return c; }
            }
        } catch (Exception e) { OpticClient.LOG.warn("Cannot read settings; using defaults", e); }
        return new RecorderConfig();
    }
    public void validate() {
        if(colorMode==null) colorMode=ColorMode.LEGACY;
        if(notifications==null) notifications=Notifications.SIMPLE;
        if(markerType==null) markerType=dev.optic.markers.Marker.Type.KEEP;
        if(replaySeconds!=15 && replaySeconds!=30 && replaySeconds!=60 && replaySeconds!=120 && replaySeconds!=300) replaySeconds=30;
        if(quality==null) quality=VideoQuality.STANDARD;
        if(aspect==null) aspect=dev.optic.capture.ScaleLayout.Aspect.FIT;
        if(preset==null || !preset.matches(this)) preset=RecordingPreset.CUSTOM;
        if(!Double.isFinite(microphoneGain)) microphoneGain=1;
        microphoneGain=Math.max(0,Math.min(4,microphoneGain));
        if(recordingsFolder==null) recordingsFolder="";
        if(recordingTitle==null) recordingTitle="";
        if(namingTemplate==null || namingTemplate.length()>160) namingTemplate="";
        if(namingStyle==null) namingStyle=dev.optic.recording.RecordingStorage.Style.DEFAULT;
        if (resolution == null) resolution = Resolution.P1080;
        if (microphoneDevice == null || microphoneDevice.isBlank()) microphoneDevice = "Default Input";
        if (fps != 30 && fps != 60 && fps != 120) fps = 60;
        if (!java.util.List.of("auto", "h264_nvenc", "h264_amf", "h264_qsv", "libx264").contains(encoder == null ? "" : encoder) && !dev.optic.encoding.AdvancedEncoders.isAdvanced(encoder)) encoder = "auto";
    }
    public boolean save(Path file) {
        try {
            SettingsStore.write(file,GSON.toJsonTree(this).getAsJsonObject(),4);
            return true;
        } catch (Exception e) { OpticClient.LOG.warn("Cannot save settings; original retained", e); return false; }
    }
}
