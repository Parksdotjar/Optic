//? if >=26.2 {
/*package dev.optic.compat;
import dev.optic.capture.*;
import dev.optic.recording.RecordingMetrics;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=26.3 {
/^import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.textures.*;
^///?} else {
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.*;
//?}
import java.util.concurrent.ArrayBlockingQueue;
import java.util.*;
/^* Modern backend-independent triple staging ring. No fence wait, CPU resize, or encoder IO. ^/
public final class GlFrameCapture implements FrameCapture {
    private final int width,height,bytes;
    private final FrameBufferPool pool;
    private final ArrayBlockingQueue<FrameBufferPool.Frame> queue;
    private final RecordingMetrics metrics;
    private final ScaleLayout.Aspect aspect;
    private static final class Slot { GpuBuffer buffer; volatile boolean complete; boolean pending; long index,start; final Runnable ready=() -> complete=true; }
    private final Slot[] slots=new Slot[3];
    private GpuTexture output,cropped; private GpuTextureView view,cropView;
    private ScaleLayout layout; private int sw,sh,cursor;
    private RenderPass.RenderArea area;
    private static final org.joml.Vector4f BLACK=new org.joml.Vector4f(0,0,0,1);
    public GlFrameCapture(int w,int h,FrameBufferPool pool,ArrayBlockingQueue<FrameBufferPool.Frame> queue,RecordingMetrics metrics,boolean persistent,ScaleLayout.Aspect aspect) {
        width=w;height=h;bytes=Math.multiplyExact(Math.multiplyExact(w,h),4);this.pool=pool;this.queue=queue;this.metrics=metrics;this.aspect=aspect;
        var device=RenderSystem.getDevice();
        try {
            output=device.createTexture("Optic scaled capture",GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_COPY_DST,GpuFormat.RGBA8_UNORM,w,h,1,1);
            view=device.createTextureView(output);
            for(int i=0;i<3;i++) { slots[i]=new Slot(); slots[i].buffer=device.createBuffer(() -> "Optic readback",GpuBuffer.USAGE_COPY_DST|GpuBuffer.USAGE_MAP_READ,bytes); }
        } catch(Throwable e) { close(); throw e; }
    }
    public boolean ready() { return !slots[cursor].pending && queue.remainingCapacity()>0; }
    public void poll() {
        for(int n=0;n<3;n++) {
            Slot slot=slots[(cursor+n)%3]; if(!slot.pending) continue; if(!slot.complete) break;
            slot.pending=false; metrics.completedTransfers.increment(); metrics.transferLatencyNanos.add(System.nanoTime()-slot.start);
            var frame=pool.acquire(); if(frame==null) { metrics.dropped.increment(); continue; }
            long start=System.nanoTime(); boolean offered=false;
            try(var mapping=slot.buffer.map(true,false)) {
                frame.pixels.clear(); var data=mapping.data(); data.limit(bytes);
                // RenderPearl readback follows Minecraft Screenshot's bottom-up convention.
                // Fold orientation into the one required staging-to-pool copy; no extra image allocation/copy.
                int stride=width*4;
                for(int row=0;row<height;row++) frame.pixels.put(row*stride,data,(height-1-row)*stride,stride);
                frame.pixels.position(0).limit(bytes); frame.index=slot.index;
                offered=queue.offer(frame); if(!offered) metrics.dropped.increment();
            } finally { if(!offered) pool.release(frame); metrics.readbackNanos.add(System.nanoTime()-start); }
        }
        metrics.queue(queue.size());
    }
    public void capture(int ignored,int sourceWidth,int sourceHeight,long index) {
        if(!ready()) { metrics.dropped.increment(); return; }
        long start=System.nanoTime(); var device=RenderSystem.getDevice(); var source=net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if(layout==null || sw!=sourceWidth || sh!=sourceHeight) {
            sw=sourceWidth;sh=sourceHeight;layout=ScaleLayout.calculate(sw,sh,width,height,aspect);
            area=new RenderPass.RenderArea(layout.dx(),layout.dy(),layout.dw(),layout.dh());
            if(cropView!=null) { cropView.close();cropView=null; } if(cropped!=null) { cropped.close();cropped=null; }
            if(layout.sw()!=sw || layout.sh()!=sh) {
                cropped=device.createTexture("Optic crop",GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING,source.getColorTexture().getFormat(),layout.sw(),layout.sh(),1,1);cropView=device.createTextureView(cropped);
            }
        }
        var cmd=device.createCommandEncoder();
        if(cropped!=null) cmd.copyTextureToTexture(source.getColorTexture(),cropped,0,0,0,layout.sx(),layout.sy(),layout.sw(),layout.sh());
        cmd.clearColorTexture(output,BLACK); metrics.scaleClears.increment();
        try(var pass=cmd.createRenderPass(() -> "Optic resize",view,Optional.empty(),null,OptionalDouble.empty(),area)) {
            //? if >=26.3 {
            /^pass.setPipeline(RenderSystem.getCompiledPipeline(net.minecraft.client.renderer.RenderPipelines.ENTITY_OUTLINE_BLIT));
            ^///?} else {
            pass.setPipeline(net.minecraft.client.renderer.RenderPipelines.ENTITY_OUTLINE_BLIT);
            //?}
            RenderSystem.bindDefaultUniforms(pass);
            //? if >=26.3 {
            /^pass.setUniform("InSampler",cropView==null?source.getColorTextureView():cropView,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            ^///?} else {
            pass.bindTexture("InSampler",cropView==null?source.getColorTextureView():cropView,RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            //?}
            pass.draw(3,1,0,0);
        }
        Slot slot=slots[cursor]; slot.complete=false;slot.pending=true;slot.index=index;slot.start=System.nanoTime();
        cmd.copyTextureToBuffer(output,slot.buffer,0,slot.ready,0); cmd.submit();
        cursor=(cursor+1)%3;metrics.submitted.increment();metrics.scaleCopies.increment();long cost=System.nanoTime()-start;metrics.captureNanos.add(cost);metrics.captureTime.add(cost);
    }
    public void close() {
        // RenderPearl owns deferred destruction of submitted GPU resources. Workers only own CPU pool frames.
        for(Slot slot:slots) if(slot!=null && slot.buffer!=null) slot.buffer.close();
        if(view!=null)view.close();if(output!=null)output.close();if(cropView!=null)cropView.close();if(cropped!=null)cropped.close();
    }
}

*///?}
