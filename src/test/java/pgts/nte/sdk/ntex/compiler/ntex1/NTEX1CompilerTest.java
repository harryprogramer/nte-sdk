package pgts.nte.sdk.ntex.compiler.ntex1;

import pgts.nte.sdk.ntex.compiler.NTEXCompiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NTEX1CompilerTest {

    @org.junit.jupiter.api.Test
    void compile() throws IOException {

        NTEXCompiler compiler = new NTEX1Compiler();
        compiler.compile("sjema.ntes");
    }
}