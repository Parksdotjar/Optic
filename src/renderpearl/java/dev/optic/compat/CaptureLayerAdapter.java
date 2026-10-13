package dev.optic.compat;

import dev.optic.capture.CapturePolicy;

/** Version-sensitive render adapters scope command submission only; UI logic is never invoked twice. */
public interface CaptureLayerAdapter {
    String id();
    CapturePolicy.Element element();
    boolean supported();
    String limitation();
    default int enter() { return CleanCapture.enter(element()); }
    default void exit(int previous) { CleanCapture.exit(previous); }

    enum Vanilla implements CaptureLayerAdapter {
        HUD, CHAT, SUBTITLES, SCOREBOARD, BOSSES, ACTION_BAR, DEBUG, INDICATOR;
        public String id() { return "optic:" + name().toLowerCase(java.util.Locale.ROOT); }
        public CapturePolicy.Element element() { return CapturePolicy.Element.valueOf(name()); }
        public boolean supported() { return true; }
        public String limitation() { return switch (this) {
            case HUD -> "Screen-space HUD only. Hands, world labels and menus remain.";
            case CHAT -> "Vanilla chat history only. Chat input and third-party chat windows remain.";
            case DEBUG -> "2D debug display only; world-space debug graphics remain.";
            default -> "Minecraft vanilla GUI extraction path.";
        }; }
    }
}
