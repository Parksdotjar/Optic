package dev.optic.capture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapturePolicyTest {
    @Test void allPolicyCombinationsRespectParentAndIndependentIndicator() {
        for(int mask=0;mask<256;mask++) {
            var p=CapturePolicy.of((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0,(mask&32)!=0,(mask&64)!=0,(mask&128)!=0);
            assertEquals(mask!=255,p.clean());
            for(var e:CapturePolicy.Element.values()) {
                boolean own=(mask&(1<<e.ordinal()))!=0;
                assertEquals(own && (e==CapturePolicy.Element.INDICATOR || (mask&1)!=0),p.includes(e),"mask="+mask+" element="+e);
            }
            assertSame(p,CapturePolicy.of(p.hud(),p.chat(),p.subtitles(),p.scoreboard(),p.bosses(),p.actionBar(),p.debug(),p.indicator()));
        }
    }
}
