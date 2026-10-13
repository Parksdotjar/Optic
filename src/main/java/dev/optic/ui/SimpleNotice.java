package dev.optic.ui;

import java.util.Locale;

/** Small, neutral status messages used instead of Minecraft's colored system toasts. */
public final class SimpleNotice {
    private static volatile String text;
    private static volatile long until;
    private SimpleNotice() {}

    public static void show(String message) {
        String concise=concise(message);
        if(concise==null) return;
        text=concise;
        until=System.nanoTime()+3_000_000_000L;
    }

    public static String current() {
        return text!=null && System.nanoTime()<until?text:null;
    }

    static String concise(String message) {
        String value=message.toLowerCase(Locale.ROOT);
        if(value.contains("recording paused")) return "Recording paused";
        if(value.contains("recording resumed")) return "Recording resumed";
        if(value.contains("recording started")) return "Recording started";
        if(value.contains("recording saved")) return "Recording saved";
        if(value.contains("saving and checking")) return "Saving recording…";
        if(value.contains("disk") && (value.contains("full") || value.contains("space"))) return "Recording stopped — disk full";
        if(value.contains("microphone unavailable") || value.contains("microphone disconnected")) return "Microphone unavailable";
        if(value.contains("game audio unavailable") || value.contains("game audio interrupted")) return "Minecraft audio unavailable";
        if(value.contains("capture interrupted") || value.contains("encoding failed") || value.contains("recording stopped:")) return "Recording error";
        if(value.contains("recovered")) return "Recording recovered";
        return null;
    }
}
