package dev.optic.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class MicrophoneCapture implements AudioCapture {
    private static final AudioFormat FORMAT=new AudioFormat(48000,16,1,true,false);
    private static final DataLine.Info INFO=new DataLine.Info(TargetDataLine.class,FORMAT);
    private final String device;
    private volatile TargetDataLine line;
    private Thread worker;
    private volatile boolean running;
    private volatile IOException failure;
    private volatile long lastData;
    private java.util.concurrent.ScheduledExecutorService monitor;
    private long start, origin;
    private boolean reconnect=true;
    private AudioControls controls=new AudioControls();
    private PcmTrackWriter writer;
    private PcmStorage storage=PcmStorage.FILE;
    public void storage(PcmStorage storage) { this.storage=storage; }
    public MicrophoneCapture(String device) { this.device=device; }
    public void configure(long origin,AudioControls controls,boolean reconnect) { this.origin=origin; this.controls=controls; this.reconnect=reconnect; }
    public static List<String> devices() {
        List<String> names=new ArrayList<>(); names.add("Default Input");
        for(Mixer.Info mixer:AudioSystem.getMixerInfo()) if(AudioSystem.getMixer(mixer).isLineSupported(INFO)) names.add(mixer.getName());
        return List.copyOf(names);
    }
    private String boundDevice;
    private TargetDataLine open() throws Exception {
        TargetDataLine candidate=null;
        String selected=boundDevice==null?device:boundDevice;
        if(selected.equals("Default Input")) {
            Mixer mixer=AudioSystem.getMixer(null);
            if(mixer.isLineSupported(INFO)) { candidate=(TargetDataLine)mixer.getLine(INFO); boundDevice=mixer.getMixerInfo().getName(); }
            else {
                candidate=(TargetDataLine)AudioSystem.getLine(INFO);
                // Java Sound cannot identify this implicit device reliably: disable reconnect, never switch silently.
                reconnect=false;
            }
        } else {
            for(Mixer.Info mixer:AudioSystem.getMixerInfo()) if(mixer.getName().equals(selected) && AudioSystem.getMixer(mixer).isLineSupported(INFO)) {
                candidate=(TargetDataLine)AudioSystem.getMixer(mixer).getLine(INFO); boundDevice=selected; break;
            }
        }
        if(candidate==null) throw new LineUnavailableException("Selected input no longer exists");
        try { candidate.open(FORMAT,9600); candidate.start(); return candidate; }
        catch(Exception e) { candidate.close(); throw e; }
    }
    public void start(Path destination) throws Exception {
        line=open(); start=System.nanoTime(); if(origin==0) origin=start;
        try { writer=new PcmTrackWriter(destination,1,origin,controls,25,storage); }
        catch(Exception e) { line.close(); throw e; }
        running=true; lastData=start; controls.status="Microphone active";
        worker=new Thread(this::capture,"Optic microphone"); worker.setDaemon(true); worker.start();
        monitor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"Optic microphone health"); t.setDaemon(true); return t; });
        monitor.scheduleAtFixedRate(() -> {
            var current=line;
            if(running && current!=null && System.nanoTime()-lastData>3_000_000_000L) { current.stop(); current.close(); }
        },1,1,java.util.concurrent.TimeUnit.SECONDS);
    }
    private void capture() {
        byte[] input=new byte[960], converted=new byte[1024];
        AudioClock clock=new AudioClock(); clock.reset(start); LinearResampler resampler=new LinearResampler();
        long segment=start, outputFrames=0;
        int retries=0;
        while(running) {
            try {
                if(line==null || !line.isOpen()) throw new IOException("Microphone disconnected");
                int n=line.read(input,0,input.length);
                if(!running) break;
                if(n<=0) throw new IOException("Microphone stopped delivering samples");
                long now=System.nanoTime(); lastData=now;
                clock.advance(n/2,now-line.available()*1_000_000_000L/96000);
                int count=resampler.process(input,n/2,clock.rate(),converted);
                writer.offer(converted,count,segment+outputFrames*1_000_000_000L/48000); outputFrames+=count;
                controls.status="Microphone active"; retries=0;
                if(writer.failure()!=null) throw writer.failure();
            } catch(Exception e) {
                if(!running) break;
                failure=new IOException("Microphone interrupted",e);
                controls.status=reconnect?"Reconnecting selected microphone…":"Microphone disconnected";
                if(!reconnect || retries++>=15 || writer.failure()!=null) break;
                try {
                    var old=line; line=null; if(old!=null) { old.stop(); old.close(); }
                    Thread.sleep(2000); if(!running) break;
                    line=open(); segment=System.nanoTime(); outputFrames=0; clock.reset(segment); resampler=new LinearResampler();
                    lastData=segment; failure=null; controls.reconnects++;
                } catch(Exception retry) { /* Next iteration retries the same selected device, bounded to 30 seconds. */ }
            }
        }
        controls.peak=0;
    }
    public long startedNanos() { return origin; }
    public IOException failure() { return failure; }
    public void setPaused(boolean paused) { if(writer!=null) writer.setPaused(paused); }
    public void requestStop() { running=false; }
    public void close() throws Exception {
        running=false; if(monitor!=null) monitor.shutdownNow();
        var current=line; if(current!=null) { current.stop(); current.close(); }
        if(worker!=null) { worker.interrupt(); worker.join(2000); if(worker.isAlive()) throw new IOException("Microphone worker did not stop"); }
        if(writer!=null) writer.close(); controls.status="Idle";
    }
}
