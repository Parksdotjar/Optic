package dev.optic.capture;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Standalone experiment, NEVER called from Minecraft. Blocking GPU queries and readback
 * here are intentional measurement barriers, not production capture architecture. */
public final class ScaleBenchmark {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]); Files.createDirectories(out);
        if(!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"Optic scaling experiment",0,0);
        if(window==0) throw new IllegalStateException("OpenGL context unavailable");
        try {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            String environment="GPU="+glGetString(GL_RENDERER)+"\nGL="+glGetString(GL_VERSION)+"\nOS="+System.getProperty("os.name")+"\nseed=51\n";
            Files.writeString(out.resolve("environment.txt"),environment);
            int vs=shader(GL_VERTEX_SHADER,"#version 330 core\nout vec2 uv; void main(){ vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); uv=p; gl_Position=vec4(p*2.-1.,0.,1.); }");
            int fs=shader(GL_FRAGMENT_SHADER,"#version 330 core\nin vec2 uv; out vec4 color; uniform sampler2D source; void main(){ color=texture(source,vec2(uv.x,1.-uv.y)); }");
            int program=glCreateProgram(); glAttachShader(program,vs); glAttachShader(program,fs); glLinkProgram(program);
            if(glGetProgrami(program,GL_LINK_STATUS)==0) throw new IllegalStateException(glGetProgramInfoLog(program));
            int vao=glGenVertexArrays(); glBindVertexArray(vao);
            var csv=new StringBuilder("source_width,source_height,width,height,repeat,sample,method,gpu_ns\n");
            var quality=new StringBuilder("source,width,mean_abs_channel_error,max_channel_error\n");
            int[][] sizes={{3840,2160,1920,1080},{1920,1080,2560,1440},{1920,1080,1920,1080}};
            Random random=new Random(51);
            for(int[] size:sizes) {
                int sw=size[0],sh=size[1],w=size[2],h=size[3];
                ByteBuffer pixels=MemoryUtil.memAlloc(sw*sh*4);
                for(int y=0;y<sh;y++) for(int x=0;x<sw;x++) {
                    pixels.put((byte)(x*255/(sw-1))).put((byte)(y*255/(sh-1))).put((byte)(((x/3+y/3)%2)*255)).put((byte)255);
                }
                pixels.flip(); int source=texture(sw,sh,pixels); MemoryUtil.memFree(pixels);
                int dest=texture(w,h,null),read=glGenFramebuffers(),draw=glGenFramebuffers();
                glBindFramebuffer(GL_READ_FRAMEBUFFER,read); glFramebufferTexture2D(GL_READ_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,source,0);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,dest,0);
                if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer incomplete");
                glViewport(0,0,w,h); glBindTexture(GL_TEXTURE_2D,source); glUseProgram(program);
                int query=glGenQueries();
                for(int repeat=0;repeat<3;repeat++) for(int sample=-20;sample<100;sample++) {
                    boolean first=random.nextBoolean();
                    for(int order=0;order<2;order++) {
                        boolean blit=order==0?first:!first;
                        glBeginQuery(GL_TIME_ELAPSED,query); scale(blit,sw,sh,w,h); glEndQuery(GL_TIME_ELAPSED);
                        long nanos=glGetQueryObjectui64(query,GL_QUERY_RESULT);
                        if(sample>=0) csv.append(sw+","+sh+","+w+","+h+","+repeat+","+sample+","+(blit?"blit":"bilinear_shader")+","+nanos+"\n");
                    }
                }
                ByteBuffer a=MemoryUtil.memAlloc(w*h*4),b=MemoryUtil.memAlloc(w*h*4);
                glBindFramebuffer(GL_READ_FRAMEBUFFER,read); scale(true,sw,sh,w,h);
                glBindFramebuffer(GL_READ_FRAMEBUFFER,draw); glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,a);
                scale(false,sw,sh,w,h); glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,b);
                long error=0; int max=0;
                for(int i=0;i<a.capacity();i++) { int e=Math.abs((a.get(i)&255)-(b.get(i)&255)); error+=e; max=Math.max(max,e); }
                quality.append(sw+","+w+","+(double)error/a.capacity()+","+max+"\n");
                save(out.resolve(sw+"-to-"+w+"-blit.png"),a,w,h); save(out.resolve(sw+"-to-"+w+"-shader.png"),b,w,h);
                MemoryUtil.memFree(a); MemoryUtil.memFree(b); glDeleteQueries(query); glDeleteFramebuffers(read); glDeleteFramebuffers(draw); glDeleteTextures(source); glDeleteTextures(dest);
            }
            Files.writeString(out.resolve("samples.csv"),csv); Files.writeString(out.resolve("quality.csv"),quality);
            glDeleteVertexArrays(vao); glDeleteProgram(program); glDeleteShader(vs); glDeleteShader(fs);
            System.out.println("SCALING_EXPERIMENT_COMPLETE "+out);
        } finally { glfwDestroyWindow(window); glfwTerminate(); }
    }
    private static void scale(boolean blit,int sw,int sh,int w,int h) { if(blit) glBlitFramebuffer(0,0,sw,sh,0,h,w,0,GL_COLOR_BUFFER_BIT,GL_LINEAR); else glDrawArrays(GL_TRIANGLES,0,3); }
    private static int texture(int w,int h,ByteBuffer pixels) {
        int t=glGenTextures(); glBindTexture(GL_TEXTURE_2D,t); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels); return t;
    }
    private static int shader(int type,String code) { int s=glCreateShader(type); glShaderSource(s,code); glCompileShader(s); if(glGetShaderi(s,GL_COMPILE_STATUS)==0) throw new IllegalStateException(glGetShaderInfoLog(s)); return s; }
    private static void save(Path path,ByteBuffer pixels,int w,int h) throws Exception {
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) { int i=(y*w+x)*4; image.setRGB(x,y,((pixels.get(i)&255)<<16)|((pixels.get(i+1)&255)<<8)|(pixels.get(i+2)&255)); }
        ImageIO.write(image,"png",path.toFile());
    }
}
