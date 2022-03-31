package pgts.nte.sdk.ntex.compiler;

import pgts.nte.sdk.ntex.errors.SyntaxException;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;

public interface NTEXCompiler {
    ByteBuffer compile(String file) throws SyntaxException;
}
