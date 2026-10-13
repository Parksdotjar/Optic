package dev.optic.audio;

/** Continuous linear interpolation for small microphone clock corrections, not arbitrary music conversion. */
public final class LinearResampler {
    private double position;
    private short previous;
    public int process(byte[] input,int frames,double inputRate,byte[] output) {
        int count=0; double step=inputRate/48000d;
        while(position<frames-1) {
            int index=(int)Math.floor(position); double fraction=position-index;
            int a=index<0?previous:sample(input,index), b=sample(input,index+1);
            int value=(int)Math.round(a+(b-a)*fraction);
            output[count*2]=(byte)value; output[count*2+1]=(byte)(value>>8); count++; position+=step;
        }
        position-=frames; previous=(short)sample(input,frames-1); return count;
    }
    private int sample(byte[] data,int i) { return (short)((data[i*2]&255)|(data[i*2+1]<<8)); }
}
