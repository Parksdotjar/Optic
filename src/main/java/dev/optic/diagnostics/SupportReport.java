package dev.optic.diagnostics;

import java.util.*;
import java.nio.file.*;
import java.io.IOException;

/** Deliberately never accepts raw logs, paths, world metadata or device names. */
public final class SupportReport {
    private static final Set<String> FIELDS=Set.of("minecraft","fabric_loader","optic","os","java","gpu","driver","state","encoder","resolution","fps","quality","color","microphone_enabled","game_audio_enabled","replay_enabled","capture_backend","optional_mods","ffmpeg");
    private static final Set<String> METRICS=Set.of("capture_avg_ms","readback_copy_avg_ms","hook_avg_ms","queue","dropped","duplicated","accepted_fps","bitrate_mbps","hook_p95_ms","hook_p99_ms","frame_p95_ms","frame_p99_ms","one_percent_low_fps","queue_high_water","encoder_fps","unique_fps");
    public static String build(Map<String,String> fields,String metrics) {
        var text=new StringBuilder("OPTIC SUPPORT REPORT v1\nReview before sharing. Nothing is uploaded.\nNo raw logs, file paths, world/server names, account IDs or microphone names are collected.\n\n");
        new TreeMap<>(fields).forEach((key,value) -> { if(FIELDS.contains(key)) text.append(key).append(": ").append(safe(value)).append('\n'); });
        text.append("\nActive-session numeric metrics (empty when idle):\n");
        var values=new TreeMap<String,String>();
        for(String token:metrics.split("\\s+")) {
            String[] pair=token.split("=",2);
            if(pair.length==2 && METRICS.contains(pair[0]) && pair[1].matches("-?[0-9]{1,15}(\\.[0-9]{1,6})?")) values.put(pair[0],pair[1]);
        }
        values.forEach((key,value) -> text.append(key).append(": ").append(value).append('\n'));
        return text.toString();
    }
    private static String safe(String value) {
        if(value==null) return "unavailable";
        // Structured hardware/version fields still have a strict length and character budget.
        if(value.contains("\\") || value.contains("/") || value.contains(":") || value.contains("@")) return "omitted";
        String clean=value.replaceAll("[^a-zA-Z0-9 .,_()+;=\\-]","?");
        return clean.substring(0,Math.min(384,clean.length()));
    }
    public static Path export(Path folder,String reviewedText) throws IOException {
        if(reviewedText.length()>16_384) throw new IOException("Support report exceeds size limit");
        Files.createDirectories(folder);
        Path file=folder.resolve("optic-support-"+UUID.randomUUID()+".txt");
        Files.writeString(file,reviewedText,StandardOpenOption.CREATE_NEW); return file;
    }
    private SupportReport() {}
}
