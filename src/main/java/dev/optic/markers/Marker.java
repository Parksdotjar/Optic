package dev.optic.markers;

/** Immutable input snapshot; no Minecraft objects or absolute paths in portable metadata. */
public record Marker(int schemaVersion,String id,String sessionId,long elapsedNanos,int fps,Type type,String note,Context context,boolean thumbnail) {
    public enum Type { KEEP, FUNNY, IMPORTANT, B_ROLL, RETAKE, THUMBNAIL;
        public String label() { return name().replace('_','-'); }
    }
    public record Context(int x,int y,int z,String dimension,String worldLabel) {}
    public double seconds() { return elapsedNanos/1e9; }
    public static String note(String value) { String s=value==null?"":value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]",""); return s.substring(0,Math.min(256,s.length())); }
}
