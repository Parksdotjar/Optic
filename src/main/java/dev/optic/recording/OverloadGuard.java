package dev.optic.recording;
/** Three consecutive five-second windows above the declared duplicate-frame ceiling. */
public final class OverloadGuard {
    private long lastTime,frames,duplicates;
    private int unhealthy;
    public boolean sample(long now,long totalFrames,long totalDuplicates) {
        if(lastTime==0) { lastTime=now; frames=totalFrames; duplicates=totalDuplicates; return false; }
        if(now-lastTime<5_000_000_000L) return false;
        long count=totalFrames-frames,copy=totalDuplicates-duplicates;
        unhealthy=count>0 && copy/(double)count>.10?unhealthy+1:0;
        lastTime=now; frames=totalFrames; duplicates=totalDuplicates;
        return unhealthy>=3;
    }
}
