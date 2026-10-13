package dev.optic.audio;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.sun.jna.*;
import com.sun.jna.ptr.LongByReference;

/** Minecraft-process-only WASAPI adapter. Does not capture unrelated desktop applications. */
public final class WindowsProcessAudio implements AudioCapture {
    public interface Clock extends com.sun.jna.win32.StdCallLibrary {
        boolean QueryPerformanceCounter(LongByReference value);
        boolean QueryPerformanceFrequency(LongByReference value);
    }
    public interface Version extends com.sun.jna.win32.StdCallLibrary { int RtlGetVersion(com.sun.jna.platform.win32.WinNT.OSVERSIONINFOEX info); }
    private static final class Support {
        static final boolean AVAILABLE=check();
        static boolean check() {
            if(!Platform.isWindows() || !Platform.is64Bit() || !System.getProperty("os.arch","").equalsIgnoreCase("amd64")) return false;
            try { var info=new com.sun.jna.platform.win32.WinNT.OSVERSIONINFOEX(); return Native.load("ntdll",Version.class).RtlGetVersion(info)==0 && info.dwBuildNumber.intValue()>=20348 && WindowsProcessAudio.class.getResource("/native/windows/optic-process-audio.exe")!=null; }
            catch(Throwable e) { return false; }
        }
    }
    private final long origin;
    private final AudioControls controls;
    private Process process;
    private Thread reader;
    private PcmTrackWriter writer;
    private PcmStorage storage=PcmStorage.FILE;
    public void storage(PcmStorage storage) { this.storage=storage; }
    private volatile IOException failure;
    private volatile boolean stopping;
    public WindowsProcessAudio(long origin,AudioControls controls) { this.origin=origin; this.controls=controls; }
    public static boolean supported() { return Support.AVAILABLE; }
    public void start(Path destination) throws Exception {
        if(!supported()) throw new IOException("Game audio currently requires Windows x64 build 20348 or newer");
        var clock=Native.load("kernel32",Clock.class); var qpc=new LongByReference(); var frequency=new LongByReference();
        if(!clock.QueryPerformanceFrequency(frequency) || !clock.QueryPerformanceCounter(qpc)) throw new IOException("Windows audio clock unavailable");
        long nano=System.nanoTime(); double qpcNanos=qpc.getValue()*1e9/frequency.getValue();
        Path exe=destination.getParent().resolve("optic-process-audio.exe");
        try(var resource=WindowsProcessAudio.class.getResourceAsStream("/native/windows/optic-process-audio.exe")) {
            if(resource==null) throw new IOException("Windows audio helper missing from this build");
            Files.copy(resource,exe,StandardCopyOption.REPLACE_EXISTING);
        }
        process=new ProcessBuilder(exe.toString(),Long.toString(ProcessHandle.current().pid())).redirectError(destination.resolveSibling("game-audio.log").toFile()).start();
        var input=new DataInputStream(new BufferedInputStream(process.getInputStream(),4096));
        var ready=CompletableFuture.runAsync(() -> { try { if(Integer.reverseBytes(input.readInt())!=0x4f505433) throw new IOException("Invalid audio helper response"); } catch(IOException e) { throw new CompletionException(e); } });
        try { ready.get(7,TimeUnit.SECONDS); } catch(Exception e) { process.destroyForcibly(); throw new IOException("Windows game audio could not start; see game-audio.log",e); }
        writer=new PcmTrackWriter(destination,2,origin,controls,12,storage); controls.status="Capturing Minecraft + mod sounds";
        reader=new Thread(() -> {
            byte[] pcm=new byte[1920];
            try(input) {
                while(true) {
                    long q=Long.reverseBytes(input.readLong()); int frames=Integer.reverseBytes(input.readInt());
                    if(frames<1 || frames>480) throw new IOException("Invalid audio packet length");
                    input.readFully(pcm,0,frames*4);
                    writer.offer(pcm,frames,nano+(long)(q*100d-qpcNanos));
                    if(writer.failure()!=null) throw writer.failure();
                }
            } catch(IOException e) { if(!stopping) { failure=e; controls.status="Game audio interrupted"; } }
        },"Optic Windows audio"); reader.setDaemon(true); reader.start();
    }
    public long startedNanos() { return origin; }
    public IOException failure() { return failure; }
    public void setPaused(boolean paused) { if(writer!=null) writer.setPaused(paused); }
    public void requestStop() {
        if(stopping) return; stopping=true;
        CompletableFuture.runAsync(this::sendStop);
    }
    private void sendStop() {
        if(process!=null) try { process.getOutputStream().write('\n'); process.getOutputStream().flush(); } catch(IOException ignored) {}
    }
    public void close() throws Exception {
        stopping=true;
        if(process!=null) {
            sendStop();
            if(!process.waitFor(3,TimeUnit.SECONDS)) process.destroyForcibly();
        }
        if(reader!=null) reader.join(2000);
        if(writer!=null) writer.close(); controls.status="Idle";
    }
}
