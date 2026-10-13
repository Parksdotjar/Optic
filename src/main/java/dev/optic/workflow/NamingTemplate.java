package dev.optic.workflow;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import dev.optic.recording.RecordingStorage;

public final class NamingTemplate {
    public static String resolve(String template,String title,String server,LocalDateTime time) {
        if(template==null || template.length()>160) throw new IllegalArgumentException("Template limit: 160 characters");
        String result=template.replace("{date}",time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .replace("{time}",time.format(DateTimeFormatter.ofPattern("HH-mm-ss")))
            .replace("{title}",title==null || title.isBlank()?"Optic":title).replace("{server}",server==null || server.isBlank()?"Minecraft":server);
        if(result.contains("{") || result.contains("}")) throw new IllegalArgumentException("Use only {date}, {time}, {title}, {server}");
        return RecordingStorage.cleanTitle(result);
    }
}
