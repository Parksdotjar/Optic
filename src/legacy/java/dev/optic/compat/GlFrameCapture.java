package dev.optic.compat;

import dev.optic.capture.*;
import dev.optic.recording.RecordingMetrics;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL33C.*;

/** Triple PBO with zero-timeout fence checks. Persistent coherent maps publish leases
 * to the worker; the GL 3.3 fallback copies only signalled transfers to a CPU pool. */
public final class GlFrameCapture implements FrameCapture {
    private final int width, height, bytes;
    private final int[] pbos = new int[3];
    private final long[] fences = new long[3], indices = new long[3];
    private final long[] submittedAt=new long[3];
    private static final class Lease { volatile boolean inUse; FrameBufferPool.Frame frame; }
    private final Lease[] leases=new Lease[3];
    private final boolean persistent;
    private final ScaleLayout.Aspect aspect;
    private ScaleLayout layout;
    private int sourceWidth,sourceHeight;
    private int readFbo, scaleFbo, scaleTexture, cursor, pendingTransfers;
    private final FrameBufferPool pool;
    private final ArrayBlockingQueue<FrameBufferPool.Frame> queue;
    private final RecordingMetrics metrics;
    public GlFrameCapture(int width, int height, FrameBufferPool pool, ArrayBlockingQueue<FrameBufferPool.Frame> queue, RecordingMetrics metrics,boolean persistent) {
        this(width,height,pool,queue,metrics,persistent,ScaleLayout.Aspect.FIT);
    }
    public GlFrameCapture(int width, int height, FrameBufferPool pool, ArrayBlockingQueue<FrameBufferPool.Frame> queue, RecordingMetrics metrics,boolean persistent,ScaleLayout.Aspect aspect) {
        this.width = width; this.height = height; this.bytes = Math.multiplyExact(Math.multiplyExact(width,height),4);
        this.pool = pool; this.queue = queue; this.metrics = metrics;
        this.persistent=persistent;
        this.aspect=aspect;
        int oldPack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING), oldTex = glGetInteger(GL_TEXTURE_BINDING_2D), oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            readFbo = glGenFramebuffers(); scaleFbo = glGenFramebuffers(); scaleTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, scaleTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scaleFbo);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, scaleTexture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Resize framebuffer incomplete");
            for (int i = 0; i < 3; i++) {
                pbos[i] = glGenBuffers(); glBindBuffer(GL_PIXEL_PACK_BUFFER,pbos[i]);
                if(persistent) {
                    int flags=GL_MAP_READ_BIT|ARBBufferStorage.GL_MAP_PERSISTENT_BIT|ARBBufferStorage.GL_MAP_COHERENT_BIT;
                    ARBBufferStorage.glBufferStorage(GL_PIXEL_PACK_BUFFER,bytes,flags);
                    ByteBuffer mapping=glMapBufferRange(GL_PIXEL_PACK_BUFFER,0,bytes,flags);
                    if(mapping==null) throw new IllegalStateException("Persistent PBO mapping failed");
                    Lease lease=new Lease(); lease.frame=new FrameBufferPool.Frame(mapping,() -> lease.inUse=false); leases[i]=lease;
                } else glBufferData(GL_PIXEL_PACK_BUFFER,bytes,GL_STREAM_READ);
                if(persistent && Boolean.getBoolean("optic.failPersistentSetup")) throw new IllegalStateException("Development-injected persistent allocation failure");
            }
        } catch (Throwable e) { close(); throw e; }
        finally { glBindBuffer(GL_PIXEL_PACK_BUFFER,oldPack); glBindTexture(GL_TEXTURE_2D,oldTex); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldDraw); }
    }
    public void poll() {
        if(pendingTransfers==0) return;
        int old = persistent?0:glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            // Process in submission order even when the ring wraps.
            for (int n = 0; n < 3; n++) {
                int i = (cursor+n)%3;
                if (fences[i] == 0) continue;
                int result = glClientWaitSync(fences[i],0,0);
                if (result == GL_TIMEOUT_EXPIRED) break; // Preserve order even on a driver that signals fences unevenly.
                if (result == GL_WAIT_FAILED) throw new IllegalStateException("GPU transfer fence failed");
                glDeleteSync(fences[i]); fences[i] = 0; pendingTransfers--;
                metrics.transferLatencyNanos.add(System.nanoTime()-submittedAt[i]); metrics.completedTransfers.increment();
                if(persistent) {
                    Lease lease=leases[i]; lease.frame.index=indices[i]; lease.inUse=true;
                    // Coherent mapping + signalled fence makes GPU writes visible. Queue publication
                    // transfers Java ownership; volatile release forbids reuse until the worker finishes.
                    if(!queue.offer(lease.frame)) { lease.inUse=false; metrics.dropped.increment(); }
                    continue;
                }
                var frame = pool.acquire();
                if (frame == null) { metrics.dropped.increment(); continue; }
                long start = System.nanoTime();
                boolean delivered = false;
                try {
                    glBindBuffer(GL_PIXEL_PACK_BUFFER,pbos[i]);
                    ByteBuffer mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER,0,bytes,GL_MAP_READ_BIT);
                    if (mapped == null) throw new IllegalStateException("PBO mapping unavailable");
                    try { frame.pixels.clear(); frame.pixels.put(mapped); frame.pixels.flip(); }
                    finally { if (!glUnmapBuffer(GL_PIXEL_PACK_BUFFER)) throw new IllegalStateException("PBO contents invalid"); }
                    frame.index = indices[i];
                    delivered = queue.offer(frame);
                    if (!delivered) metrics.dropped.increment();
                } finally { if (!delivered) pool.release(frame); metrics.readbackNanos.add(System.nanoTime()-start); }
            }
        } finally { if(!persistent) glBindBuffer(GL_PIXEL_PACK_BUFFER,old); metrics.queue(queue.size()); }
    }
    public void capture(int texture, int sw, int sh, long index) {
        if (fences[cursor] != 0 || (persistent && leases[cursor].inUse) || queue.remainingCapacity() == 0) { metrics.dropped.increment(); return; }
        long start = System.nanoTime();
        int oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), oldPack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT), row = glGetInteger(GL_PACK_ROW_LENGTH), skipRows = glGetInteger(GL_PACK_SKIP_ROWS), skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo);
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Source framebuffer incomplete");
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,scaleFbo);
            glDisable(GL_SCISSOR_TEST);
            // Geometry allocates only on resize. Full coverage needs no redundant color clear.
            if(layout==null || sw!=sourceWidth || sh!=sourceHeight) { layout=ScaleLayout.calculate(sw,sh,width,height,aspect); sourceWidth=sw; sourceHeight=sh; }
            var r=layout;
            if(r.bars()) { glClearBufferfv(GL_COLOR,0,BLACK); metrics.scaleClears.increment(); }
            glBlitFramebuffer(r.sx(),r.sy(),r.sx()+r.sw(),r.sy()+r.sh(),r.dx(),r.dy()+r.dh(),r.dx()+r.dw(),r.dy(),GL_COLOR_BUFFER_BIT,GL_LINEAR);
            metrics.scaleCopies.increment();
            glBindFramebuffer(GL_READ_FRAMEBUFFER,scaleFbo); glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindBuffer(GL_PIXEL_PACK_BUFFER,pbos[cursor]);
            glPixelStorei(GL_PACK_ALIGNMENT,1); glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_ROWS,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0);
            glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,0L);
            fences[cursor] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);
            if (fences[cursor] == 0) throw new IllegalStateException("Cannot fence transfer");
            pendingTransfers++;
            indices[cursor] = index; cursor = (cursor+1)%3;
            submittedAt[(cursor+2)%3]=System.nanoTime();
            metrics.submitted.increment();
        } finally {
            if (scissor) glEnable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldDraw); glBindBuffer(GL_PIXEL_PACK_BUFFER,oldPack);
            glPixelStorei(GL_PACK_ALIGNMENT,alignment); glPixelStorei(GL_PACK_ROW_LENGTH,row); glPixelStorei(GL_PACK_SKIP_ROWS,skipRows); glPixelStorei(GL_PACK_SKIP_PIXELS,skipPixels);
            long duration=System.nanoTime()-start; metrics.captureNanos.add(duration); metrics.captureTime.add(duration);
        }
    }
    private static final float[] BLACK = {0,0,0,1};
    public boolean ready() { return fences[cursor]==0 && (!persistent || !leases[cursor].inUse) && queue.remainingCapacity()>0; }
    public void close() {
        pendingTransfers=0;
        int oldPack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        for(int i=0;i<3;i++) if(leases[i]!=null) { glBindBuffer(GL_PIXEL_PACK_BUFFER,pbos[i]); glUnmapBuffer(GL_PIXEL_PACK_BUFFER); leases[i]=null; }
        glBindBuffer(GL_PIXEL_PACK_BUFFER,oldPack);
        for (int i=0;i<3;i++) { if(fences[i]!=0) { glDeleteSync(fences[i]); fences[i]=0; } if(pbos[i]!=0) { glDeleteBuffers(pbos[i]); pbos[i]=0; } }
        if(readFbo!=0) { glDeleteFramebuffers(readFbo); readFbo=0; }
        if(scaleFbo!=0) { glDeleteFramebuffers(scaleFbo); scaleFbo=0; }
        if(scaleTexture!=0) { glDeleteTextures(scaleTexture); scaleTexture=0; }
    }
}
