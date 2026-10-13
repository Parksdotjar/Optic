package dev.optic.audio;

import java.nio.file.*;
import javax.sound.sampled.*;

/** Plays a quiet synthetic signal, never opens a microphone. Captures only this JVM process. */
public final class WindowsAudioIntegration {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]); Files.createDirectories(root); Path pcm=root.resolve("game-"+System.nanoTime()+".pcm");
        var controls=new AudioControls();
        try(var source=new WindowsProcessAudio(System.nanoTime(),controls)) {
            source.start(pcm);
            var format=new AudioFormat(48000,16,2,true,false);
            try(var playback=AudioSystem.getSourceDataLine(format)) {
                playback.open(format); playback.start();
                byte[] data=new byte[4800*4];
                for(int i=0;i<4800;i++) { short sample=(short)(Math.sin(i*2*Math.PI*440/48000)*1200); for(int ch=0;ch<2;ch++) { data[i*4+ch*2]=(byte)sample; data[i*4+ch*2+1]=(byte)(sample>>8); } }
                for(int i=0;i<10;i++) playback.write(data,0,data.length);
                playback.drain(); Thread.sleep(300);
            }
            if(source.failure()!=null) throw source.failure();
        }
        byte[] data=Files.readAllBytes(pcm); long active=0;
        for(int i=0;i<data.length;i+=2) if(Math.abs((short)((data[i]&255)|(data[i+1]<<8)))>100) active++;
        if(active<10000) throw new AssertionError("Process audio signal missing: "+active+" samples");
        if(controls.droppedFrames!=0) throw new AssertionError("Audio queue overrun");
        System.out.println("PASS: Windows process-only stereo capture, audible signal recovered, bounded queue: "+pcm);
    }
}
