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
        NTEX1Compiler.NTExecutableBuilder builder =
                new NTEX1Compiler.NTExecutableBuilder(20, 2, "siema", 655, 255, 90000);
        Files.write(Path.of("APP.NTE"), builder.build());

        NTEXCompiler compiler = new NTEX1Compiler();
        compiler.compile("sjema.ntes");
    }
}