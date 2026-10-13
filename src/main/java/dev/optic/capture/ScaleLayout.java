package dev.optic.capture;

/** Pixel geometry, independent of GL/Minecraft. Recompute only when source dimensions change. */
public record ScaleLayout(int sx,int sy,int sw,int sh,int dx,int dy,int dw,int dh,boolean bars) {
    public enum Aspect { FIT, CROP;
        public String label() { return this==FIT?"Fit (black bars)":"Fill (crop edges)"; }
    }
    public static ScaleLayout calculate(int sourceWidth,int sourceHeight,int width,int height,Aspect aspect) {
        if(sourceWidth<1 || sourceHeight<1 || width<1 || height<1) throw new IllegalArgumentException("Dimensions must be positive");
        if(aspect==Aspect.CROP) {
            double scale=Math.max(width/(double)sourceWidth,height/(double)sourceHeight);
            int sw=Math.max(1,Math.min(sourceWidth,(int)Math.round(width/scale)));
            int sh=Math.max(1,Math.min(sourceHeight,(int)Math.round(height/scale)));
            return new ScaleLayout((sourceWidth-sw)/2,(sourceHeight-sh)/2,sw,sh,0,0,width,height,false);
        }
        double scale=Math.min(width/(double)sourceWidth,height/(double)sourceHeight);
        int dw=Math.max(1,Math.min(width,(int)Math.round(sourceWidth*scale)));
        int dh=Math.max(1,Math.min(height,(int)Math.round(sourceHeight*scale)));
        return new ScaleLayout(0,0,sourceWidth,sourceHeight,(width-dw)/2,(height-dh)/2,dw,dh,dw!=width || dh!=height);
    }
}
