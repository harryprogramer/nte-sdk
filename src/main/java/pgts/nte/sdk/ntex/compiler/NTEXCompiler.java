package pgts.nte.sdk.ntex.compiler;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;

public interface NTEXCompiler {
    ByteBuffer compile(String file);
}
