package dev.optic.encoding;
public final class BootstrapIntegration {
    public static void main(String[] args) throws Exception {
        var exe=new FFmpegManager(java.nio.file.Path.of(args[0])).obtain();
        if(!exe.getFileName().toString().equals("ffmpeg-9.0.2.exe")) throw new AssertionError("Test did not exercise installation");
        System.out.println("PASS: verified publisher download and executable startup: "+exe);
        var before=java.nio.file.Files.getLastModifiedTime(exe);
        new FFmpegManager(java.nio.file.Path.of(args[0])).obtain();
        if(!before.equals(java.nio.file.Files.getLastModifiedTime(exe))) throw new AssertionError("Healthy cache was needlessly replaced");
        var receipt=exe.resolveSibling("ffmpeg-9.0.2.sha256"); String original=java.nio.file.Files.readString(receipt);
        java.nio.file.Files.writeString(receipt,"invalid-cache-receipt");
        try {
            new FFmpegManager(java.nio.file.Path.of(args[0])).obtain();
            if(!original.equals(java.nio.file.Files.readString(receipt))) throw new AssertionError("Repaired cache did not match verified original");
            System.out.println("PASS: healthy cache reuse and corrupted-receipt repair from pinned archive");
        } finally { java.nio.file.Files.writeString(receipt,original); }
    }
}
