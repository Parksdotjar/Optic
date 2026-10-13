package dev.optic.capture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ScaleLayoutTest {
    @Test void fitPreservesWholeSourceAndPadsUltrawide() {
        var r=ScaleLayout.calculate(3440,1440,1920,1080,ScaleLayout.Aspect.FIT);
        assertEquals(3440,r.sw()); assertEquals(1440,r.sh()); assertEquals(1920,r.dw()); assertEquals(804,r.dh()); assertEquals(138,r.dy()); assertTrue(r.bars());
    }
    @Test void cropFillsWithoutStretching() {
        var r=ScaleLayout.calculate(3440,1440,1920,1080,ScaleLayout.Aspect.CROP);
        assertEquals(2560,r.sw()); assertEquals(440,r.sx()); assertEquals(1440,r.sh()); assertEquals(1080,r.dh()); assertFalse(r.bars());
    }
    @Test void upscalingAndOddWindowSizesRemainInBounds() {
        for(int[] size:new int[][]{{1920,1080},{961,541},{100,200},{3840,2160},{1,1}}) for(var mode:ScaleLayout.Aspect.values()) {
            var r=ScaleLayout.calculate(size[0],size[1],2560,1440,mode);
            assertTrue(r.sw()>0 && r.sh()>0 && r.dw()>0 && r.dh()>0);
            assertTrue(r.sx()>=0 && r.sy()>=0 && r.sx()+r.sw()<=size[0] && r.sy()+r.sh()<=size[1]);
            assertTrue(r.dx()>=0 && r.dy()>=0 && r.dx()+r.dw()<=2560 && r.dy()+r.dh()<=1440);
        }
    }
    @Test void invalidSizeRejected() { assertThrows(IllegalArgumentException.class,() -> ScaleLayout.calculate(0,1080,1920,1080,ScaleLayout.Aspect.FIT)); }
}
