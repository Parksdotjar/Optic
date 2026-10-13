package dev.optic.encoding;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;

/** Runs on control worker. Only a fixed HTTPS publisher URL + embedded hash may be downloaded. */
public final class FFmpegManager {
    private static final String SHA256 = "60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba";
    private static final URI URL = URI.create("https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip");
    private final Path cache;
    private static volatile String lastVersion="not checked this launch";
    public static String lastVersion() { return lastVersion; }
    /** Explicit background diagnostic, with bounded output and cancellation. Never downloads. */
    public static void inspectVersion(Path executable,dev.optic.recording.Cancellation cancellation) throws Exception {
        Process process=new ProcessBuilder(executable.toString(),"-version").redirectErrorStream(true).start();
        var result=new java.util.concurrent.CompletableFuture<String>();
        Thread reader=new Thread(() -> {
            try(var stream=process.getInputStream()) {
                byte[] data=stream.readNBytes(4096);
                var match=java.util.regex.Pattern.compile("ffmpeg version ([A-Za-z0-9.+_-]{1,96})").matcher(new String(data,java.nio.charset.StandardCharsets.UTF_8));
                result.complete(match.find()?match.group(1):"unrecognized");
            } catch(Exception e) { result.complete("unavailable"); }
        },"Optic version check"); reader.setDaemon(true); reader.start();
        try(var cancel=cancellation.onCancel(process::destroyForcibly)) {
            cancellation.check(); lastVersion=result.get(5,java.util.concurrent.TimeUnit.SECONDS);
        } catch(java.util.concurrent.TimeoutException e) { lastVersion="unavailable"; }
        finally { if(process.isAlive()) process.destroyForcibly(); }
    }
    public FFmpegManager(Path cache) { this.cache = cache; }
    public Path obtain() throws Exception {
        return obtain(new dev.optic.recording.Cancellation());
    }
    public Path obtain(dev.optic.recording.Cancellation cancellation) throws Exception {
        cancellation.check();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String name = windows ? "ffmpeg.exe" : "ffmpeg";
        // Search only absolute PATH entries; never implicitly execute a game-directory binary.
        for (String entry : Objects.requireNonNullElse(System.getenv("PATH"), "").split(java.io.File.pathSeparator)) {
            try {
                Path dir = Path.of(entry.replace("\"", ""));
                if (!dir.isAbsolute()) continue;
                Path candidate = dir.resolve(name);
                if (Files.isRegularFile(candidate) && Processes.run(List.of(candidate.toString(),"-version"),null,5,cancellation)) return candidate;
            } catch (IOException | InvalidPathException ignored) {}
        }
        if (!windows || !System.getProperty("os.arch").equals("amd64")) throw new IOException("Install FFmpeg on PATH; automatic setup currently supports Windows x64.");
        Files.createDirectories(cache);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
        dev.optic.recording.SessionFiles lock;
        while ((lock = dev.optic.recording.SessionFiles.tryLock(cache.resolve("installer.partial.mp4"))) == null) {
            cancellation.check();
            if (System.nanoTime() > deadline) throw new IOException("FFmpeg setup is busy in another instance");
            Thread.sleep(100);
        }
        try (var owned = lock) { return install(cancellation); }
    }
    private Path install(dev.optic.recording.Cancellation cancellation) throws Exception {
        cancellation.check();
        Path exe = cache.resolve("ffmpeg-9.0.2.exe"), receipt = cache.resolve("ffmpeg-9.0.2.sha256");
        if (Files.isRegularFile(exe) && Files.isRegularFile(receipt) && digest(exe).equals(Files.readString(receipt).trim()) && Processes.run(List.of(exe.toString(),"-version"),null,5,cancellation)) return exe;
        Path archive = cache.resolve("download.zip.part"), extracted = cache.resolve("ffmpeg.exe.part");
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
            var request = client.sendAsync(HttpRequest.newBuilder(URL).timeout(Duration.ofMinutes(3)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> response;
            try (var cancelRequest = cancellation.onCancel(() -> request.cancel(true))) { response = request.get(); }
            var deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"Optic download timeout"); t.setDaemon(true); return t; });
            var timeout=deadline.schedule(() -> { try { response.body().close(); } catch(IOException ignored) {} },180,java.util.concurrent.TimeUnit.SECONDS);
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(archive);
                 var cancelStream = cancellation.onCancel(() -> { try { in.close(); } catch(IOException ignored) {} })) {
                cancellation.check();
                if (response.statusCode()!=200) throw new IOException("Trusted FFmpeg download unavailable");
                copyLimited(in,out,200L*1024*1024,cancellation);
            } finally { timeout.cancel(false); deadline.shutdownNow(); }
            if (!digest(archive).equals(SHA256)) throw new IOException("FFmpeg checksum mismatch; update Optic or install FFmpeg on PATH.");
            boolean found = false;
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry e;
                while((e=zip.getNextEntry())!=null) {
                    // Fixed destination: archive paths are never used as filesystem paths.
                    if (!e.isDirectory() && e.getName().endsWith("/bin/ffmpeg.exe")) {
                        if(found) throw new IOException("Ambiguous FFmpeg archive");
                        try(OutputStream out=Files.newOutputStream(extracted)) { copyLimited(zip,out,180L*1024*1024,cancellation); }
                        found=true;
                    }
                }
            }
            if(!found) throw new IOException("FFmpeg missing from verified archive");
            Files.move(extracted,exe,StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(receipt,digest(exe));
            if(!Processes.run(List.of(exe.toString(),"-version"),null,5,cancellation)) throw new IOException("FFmpeg could not start");
            return exe;
        } finally { Files.deleteIfExists(archive); Files.deleteIfExists(extracted); }
    }
    private static void copyLimited(InputStream in, OutputStream out, long limit, dev.optic.recording.Cancellation cancellation) throws IOException {
        byte[] data=new byte[65536]; long total=0; int n;
        while((n=in.read(data))!=-1) { cancellation.check(); total+=n; if(total>limit) throw new IOException("Download/extraction size limit exceeded"); out.write(data,0,n); }
    }
    private static String digest(Path path) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(path)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) hash.update(b,0,n); }
        return HexFormat.of().formatHex(hash.digest());
    }
}
