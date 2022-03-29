package pgts.nte.sdk.ntex.decompiler.ntex1;

import org.junit.jupiter.api.Test;
import pgts.nte.sdk.ntex.decompiler.NTEXDecompiler;

import static org.junit.jupiter.api.Assertions.*;

class NTEX1DecompilerTest {

    @Test
    void dumpInfo() {
        NTEXDecompiler decompiler = new NTEX1Decompiler();
        decompiler.dumpInfo("SJEMA.NTE");
    }
}