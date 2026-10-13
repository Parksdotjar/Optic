package dev.optic.capture;

/** Immutable per-frame policy. False affects video only; the player's GUI is always submitted. */
public record CapturePolicy(boolean hud,boolean chat,boolean subtitles,boolean scoreboard,boolean bosses,boolean actionBar,boolean debug,boolean indicator) {
    private static final CapturePolicy[] POLICIES=new CapturePolicy[256];
    static { for(int i=0;i<256;i++) POLICIES[i]=new CapturePolicy((i&1)!=0,(i&2)!=0,(i&4)!=0,(i&8)!=0,(i&16)!=0,(i&32)!=0,(i&64)!=0,(i&128)!=0); }
    public static CapturePolicy of(boolean hud,boolean chat,boolean subtitles,boolean scoreboard,boolean bosses,boolean actionBar,boolean debug,boolean indicator) {
        return POLICIES[(hud?1:0)|(chat?2:0)|(subtitles?4:0)|(scoreboard?8:0)|(bosses?16:0)|(actionBar?32:0)|(debug?64:0)|(indicator?128:0)];
    }
    public enum Element { HUD, CHAT, SUBTITLES, SCOREBOARD, BOSSES, ACTION_BAR, DEBUG, INDICATOR }
    public boolean includes(Element element) {
        if(element==Element.INDICATOR) return indicator;
        if(!hud) return false;
        return switch(element) { case HUD -> true; case CHAT -> chat; case SUBTITLES -> subtitles; case SCOREBOARD -> scoreboard; case BOSSES -> bosses; case ACTION_BAR -> actionBar; case DEBUG -> debug; case INDICATOR -> indicator; };
    }
    public boolean clean() { return !hud || !chat || !subtitles || !scoreboard || !bosses || !actionBar || !debug || !indicator; }
}
