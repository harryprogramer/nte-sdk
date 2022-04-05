package pgts.nte.sdk.ntex.compiler.ntex1;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import pgts.nte.sdk.ntex.NTEXMethod;
import pgts.nte.sdk.ntex.compiler.NTEXCompiler;
import pgts.nte.sdk.ntex.errors.SyntaxException;
import pgts.nte.sdk.ntex.errors.UndefinedReference;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static pgts.nte.sdk.ntex.compiler.ntex1.NTEX1Instructions.*;

public class NTEX1Compiler implements NTEXCompiler {
    private static int lineCounter;
    private static AtomicInteger varIds;
    // instructions:
    // int  - 0x01 - 01
    // string - 0x02 - 02
    // call (call funtion) - 0x03 - 03
    // modify variable -    0x04 - 04
    // preturn -            0x05 (pointer return) -    05
    // areturn -            0x06 (arithmetic return) - 06
    // sreturn -            0x07 (static return) - 07
    // add  -               0x08 - 08
    // subtract -           0x09 - 09
    // divide  -            0x0A - 10
    // multiply -           0x0B - 11
    // modulo  -            0x0C - 12
    // artihmetic operation requeired (AOR) - 0x0D - 13
    // AOR END -  0x0E              - 14
    // static declare (SD) -    0x0F - 15
    // get pointer value (GPV) - 0x10 - 16
    // void -                   0x11 - 17


    private final Map<String, NTEXMethod> methods = new HashMap<>();
    private final Map<String, CompiledNTEXMethod> methods_implementations = new HashMap<>();

    public NTEX1Compiler(){
        System.out.println("creating compiler ntexc1");
        varIds = new AtomicInteger(1);
        lineCounter = 0;
    }

    public static class NTExecutableBuilder {
        private final int app_version, data_size;
        private final short nte_version, perm_size;
        private final long file_size;
        private final String name;

        public NTExecutableBuilder(int app_version, int nte_version, String name,
                                   int data_size, int perm_size, long file_size){
            this.app_version = app_version;
            this.nte_version = (short) nte_version;
            this.name = name;
            this.data_size = data_size;
            this.perm_size = (short) perm_size;
            this.file_size = file_size;

            if(name.length() > 12){
                throw new RuntimeException("name too long");
            }
        }

        public byte[] build(){
            long start = System.currentTimeMillis();

            ByteBuffer buffer = ByteBuffer.allocateDirect(32);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putInt(app_version); // 4 bytes
            buffer.putShort(nte_version); // 2 bytes
            for(int i = 0; i < 12; i++){ // 12 bytes
                if(i >= name.length()){
                    buffer.put((byte) 0);
                }else {
                    buffer.put((byte)(0xff & name.charAt(i)));
                }
            }
            buffer.putInt(data_size); // 4 bytes
            buffer.putShort(perm_size); // 2 bytes
            buffer.putLong(file_size); //  8 bytes

            buffer.flip();

            byte[] arr = new byte[buffer.remaining()];
            buffer.get(arr);

            long end = System.currentTimeMillis();

            System.out.println("build success in " + (end - start) / 1000.0 + "s");

            return arr;
        }
    }

    private static class CompiledNTEXMethod extends NTEXMethod {
        private final Map<String, Integer> variables_registry = new HashMap<>();
        private final Map<Integer, String> variables = new HashMap<>();
        private final Map<String, NTEXMethod> availableMethods;
        private final ByteArrayDataOutput out;
        private final String line;

        public CompiledNTEXMethod(String line, NTEXMethod method, Map<String, NTEXMethod> availableMethods, int lineCount) throws SyntaxException {
            super(method.getName(), method.getParams(), method.isExternal(), method.getReturnType(), lineCount);
            this.availableMethods = availableMethods;
            this.out = ByteStreams.newDataOutput();
            this.line = line;
            try {
                compileFunction();
                //try (OutputStream outputStream = new FileOutputStream(getName() + ".method")) {
                //    outputStream.write(out.toByteArray());
                //}
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private static ArithmeticOperator getOperator(String data){
            if(data.indexOf('+') != -1){
                return ArithmeticOperator.PLUS;
            }else if(data.indexOf('-') != -1){
                return ArithmeticOperator.MINUS;
            }else if(data.indexOf('/') != -1){
              return ArithmeticOperator.DIVIDE;
            }else if(data.indexOf('%') != -1){
              return ArithmeticOperator.MODULO;
            }else if(data.indexOf('*') != -1){
              return ArithmeticOperator.MULTIPLY;
            } else {
                throw new RuntimeException("unknown operator: " + data);
            }
        }

        private static byte getOperatorInstruction(ArithmeticOperator operator){
            switch (operator){
                case PLUS -> {return ntexAdd;}
                case MINUS -> {return ntexSubtract;}
                case DIVIDE -> {return ntexDivide;}
                case MULTIPLY -> {return ntexMultiply;}
                case MODULO -> {return ntexModulo;}
                default -> {
                    assert true; // unknown operator
                    return -1;
                }
            }
        }

        private static boolean isArithmetic(@NotNull String data){
            return data.indexOf('+') != -1 || data.indexOf('-') != -1 ||
                    data.indexOf('/') != -1 || data.indexOf('%') != -1 ||
                    data.indexOf('*') != -1;
        }

        private static void validateAlgebra(String @NotNull [] operations) throws SyntaxException{
            int index = 0;
            while (true){
                if(getOperator(operations[index]) != null){
                    throw new SyntaxException("excepted number but got math operator '" + operations[index] + "'");
                }
                if(getOperator(operations[++index]) == null){
                    throw new SyntaxException("excepted math operator but got '" + operations[index] + "'");
                }
            }
        }



        private void compileFunction() throws IOException, SyntaxException { // kompilacja funkcji
            String code = line.substring(line.indexOf('{') + 1, line.indexOf('}')); // wyodrebnij sam kod

            for(String data : code.split("\\s*;\\s*")){ // petla instrukcji
                int latestSpace = data.indexOf(' ');
                String value = data.substring(data.indexOf('=') + 1).trim();
                if(data.contains("//")){
                    continue;
                }
                if(latestSpace != -1 && data.substring(0, data.indexOf(' ')).equals("int")){ // jesli instrukcja rozpoczyna sie od int skompiluj jako zmienna
                    out.write(ntexInt); // zapisywanie że jest to instrukcja deklaracji zmiennej int
                    int id = varIds.getAndIncrement();
                    out.writeInt(id);
                    String varName = data.substring(data.indexOf(' '), data.indexOf('=') - 1).trim();
                    String[] operationsMap = value.split("\\s* \\s*");
                    if(isArithmetic(value)){
                        int operationIndex = 0;
                        out.write(ntexAor);
                        while (true) {

                            if (StringUtils.isNumeric(operationsMap[operationIndex])) { // static declare
                                out.write(ntexSd);
                                out.writeInt(Integer.parseInt(operationsMap[operationIndex]));
                            } else {
                                if (!variables_registry.containsKey(operationsMap[operationIndex])) {
                                    throw new UndefinedReference("unknown reference to " + operationsMap[operationIndex] + " on '" + data + "'");
                                } else {
                                    int varId = variables_registry.get(operationsMap[operationIndex]);
                                    out.write(ntexGpv);
                                    out.writeInt(varId);
                                }
                            }

                            if (operationIndex == operationsMap.length - 1) {
                                out.write(ntexAorEnd);
                                break;
                            }

                            ArithmeticOperator operator = getOperator(operationsMap[++operationIndex]);
                            out.write(getOperatorInstruction(operator));
                            operationIndex++;
                        }
                    }else if(value.length() != 0 && StringUtils.isNumeric(value)){
                        out.write(ntexSd);
                        out.writeInt(Integer.parseInt(value));
                    }else {

                        throw new RuntimeException("unknown operator on: "  + data);
                    }

                    variables_registry.put(varName, id);
                    variables.put(id, value);
                }else if(data.indexOf('(') != -1 && availableMethods.containsKey(data.substring(0, data.indexOf('(')))){ // jesli instrukcja zawiera nazwe znanej funkcji skompiluj jako wywolanie
                    String name = data.substring(0, data.indexOf('(')); // wyodrebianie nazwy funkcji
                    out.write(ntexCall); // zapisywanie ze jest to instrukcja dzwoniąca
                    out.write(name.length()); // zapisywanie rozmiaru nazwy funkji
                    for(int i = 0; i < name.length(); i++){ // zapisywanie nazwy
                        out.write((byte) (0xff & name.charAt(i)));
                    }
                }else if(data.contains("return")) {
                    if (data.indexOf('"') != -1 && data.substring(data.indexOf('"') + 1).indexOf('"') != -1) { // static string return (sreturn)
                        //System.out.println("sreturn: " + data.substring(data.indexOf(' ') + 2, data.length() - 1));
                    } else if (StringUtils.isNumeric(data.substring(data.indexOf(' ')))) {
                        //System.out.println("returned type: sreturn (number): " + data.substring(data.indexOf(' ')));
                    } else if (isArithmetic(data)) {
                        //System.out.println("areturn");
                    } else if (variables_registry.containsKey(data.substring(data.indexOf(' ')))) { // pointer return (preturn)
                        //System.out.println("returned type is preturn");
                    } else {
                        throw new RuntimeException("unknown return type for function: " + getName());
                    }
                }
                else {
                    throw new SyntaxException("Undefined instruction: " + data); // niezdefiniowana instrukcja
                }
            }
        }


        private ByteArrayDataOutput getOut() {
            return out;
        }
    }


    private NTEXMethod parseMethod(String line, int lineNumber){
        line = line.trim();
        line = line.replaceAll(" +", " ");
        if(!line.substring(0, 4).equalsIgnoreCase("func")){
            throw new RuntimeException("not a function");
        }
        String returnType = line.substring(5).substring(0, line.substring(5).indexOf(' '));
        String name = line.substring(line.substring(5).indexOf(' ') + 6, line.indexOf('('));
        Map<String, String> method_params = new HashMap<>();
        boolean isexternal = line.contains("external");

        String params = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
        if(params.length() > 1) {
            for (String param : params.split("\\s*,\\s*")) {
                String[] n = param.split(" ");
                method_params.put(n[0], n[1]);
            }
        }

        return new NTEXMethod(name, method_params, isexternal, returnType, lineNumber);
    }

    private void load_lib(String filename) throws SyntaxException {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            compileSource(br, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDeclaration(String line, int lineNumber){
        NTEXMethod method = parseMethod(line, lineNumber);
        methods.put(method.getName(), method);
    }

    private void compileSource(BufferedReader br, boolean isMain) throws IOException, SyntaxException {
        String st;
        while (true) {
            st = br.readLine();
            if(isMain) {
                lineCounter++;
            }
            if (st == null) {
                break;
            }

            st = st.trim();

            if (st.equalsIgnoreCase(".end")) {
                break;
            }

            if (st.length() == 0) {
                continue;
            }

            try {

                if (st.indexOf('$') != -1) {
                    if (st.contains("$use")) {
                        String contain_element = st.substring(st.indexOf('"') + 1, st.lastIndexOf('"'));
                        load_lib(contain_element);
                    }
                }

                if (st.length() > 4) {
                    if (st.substring(0, 4).equalsIgnoreCase("func")) {
                        if (st.lastIndexOf(';') == st.length() - 1) {
                            loadDeclaration(st, lineCounter);
                        } else {
                            StringBuilder builder = new StringBuilder();
                            while (true) {
                                if (st.indexOf('{') == -1) {
                                    st = br.readLine().trim();
                                } else {
                                    break;
                                }
                            }

                            while (true) {
                                if (st.indexOf('}') == -1) {
                                    builder.append(st);
                                    st = br.readLine().trim();
                                } else {
                                    builder.append(st);
                                    break;
                                }
                            }
                            NTEXMethod method = parseMethod(builder.toString(), lineCounter);
                            CompiledNTEXMethod compiledNTEXMethod = new CompiledNTEXMethod(builder.toString(), method, methods, lineCounter);
                            methods.put(method.getName(), method);
                            methods_implementations.put(method.getName(), compiledNTEXMethod);
                        }
                    }

                }
            } catch (Throwable t) {
                System.err.println("compiler error on function line " + lineCounter + ": " + t.getMessage());
                throw t;
            }
        }
    }

    private static byte getDataType(String type){
        switch (type) {
            case "void" -> {
                return ntexVoid;
            }
            case "int" -> {
                return ntexInt;
            }
            case "string" -> {
                return ntexString;
            }
            default -> {
                return -1;
            }
        }
    }

    @Override
    public ByteBuffer compile(String file) throws SyntaxException {
        try {
            BufferedReader br
                    = new BufferedReader(new FileReader(file));

            compileSource(br, true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        String name = FilenameUtils.removeExtension(file);
        ByteArrayDataOutput out =  ByteStreams.newDataOutput();
        NTExecutableBuilder builder = new NTExecutableBuilder(20, 25, name, 20, 20, 20);
        byte[] header = builder.build();
        out.write(header);
        for (var entry : methods_implementations.entrySet()) {
            CompiledNTEXMethod method =  entry.getValue();
            byte dataType = getDataType(method.getReturnType());
            if(dataType == -1){
                throw new RuntimeException("function: " + method.getName() + " has unknown return type: " + method.getReturnType());
            }
            out.write(0xff); // function begin
            out.write(dataType); // function return type
            if(method.getName().length() > 256){
                throw new RuntimeException("function at line: " + method.getLine() + " has to long name");
            }
            out.write(method.getName().length()); // function name length
            for(int i = 0; i < method.getName().length(); i++){ // function name
                out.write((byte) (0xff & method.getName().charAt(i)));
            }
            out.write(method.getParams().size());
            for (var param : method.getParams().entrySet()) {
                byte paramDataType = getDataType(param.getKey());
                int paramId = varIds.getAndIncrement();
                if(paramDataType == -1){
                    throw new RuntimeException("param at function at line " + method.getLine() + " has unknown data type");
                }
                out.write(paramDataType);
                out.writeInt(paramId);
            }
            byte[] compiledCode = method.getOut().toByteArray();
            out.write(compiledCode); // function body
        }

        try(OutputStream outputStream = new FileOutputStream(name.toUpperCase() + ".NTE")) {
            outputStream.write(out.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
