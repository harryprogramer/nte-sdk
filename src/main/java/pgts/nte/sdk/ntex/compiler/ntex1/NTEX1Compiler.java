package pgts.nte.sdk.ntex.compiler.ntex1;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang3.StringUtils;
import pgts.nte.sdk.ntex.NTEXMethod;
import pgts.nte.sdk.ntex.compiler.NTEXCompiler;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class NTEX1Compiler implements NTEXCompiler {
    // instructions:
    // int (variable) - 0x01
    // string (variable) - 0x02
    // call (call funtion) - 0x03
    // modify variable -    0x04
    // preturn -            0x05 (pointer return)
    // areturn -            0x06 (arithmetic return)
    // sreturn -            0x07 (static return)
    // add  -               0x08
    // subtract -           0x09
    // divide  -            0x0A
    // multiply -           0x0B
    // modulo  -            0x0C
    // artihmetic operation requeired (AOR) - 0x0D
    // AOR END -  0x0E
    // static declare (SD) -    0x0F
    // get pointer value (GPV) - 0x10


    private final Map<String, NTEXMethod> methods = new HashMap<>();
    private final Map<String, CompiledNTEXMethod> methods_implementations = new HashMap<>();

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
            System.out.println(buffer.position());
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
        private final AtomicInteger varIds = new AtomicInteger(0);
        private final Map<String, Integer> variables_registry = new HashMap<>();
        private final Map<Integer, String> variables = new HashMap<>();
        private final Map<String, NTEXMethod> availableMethods;
        private final ByteArrayDataOutput out;
        private final String line;

        enum ArithmeticOperator{
            PLUS,
            MINUS,
            DIVIDE,
            MULTIPLY,
            MODULO
        }

        public CompiledNTEXMethod(String line, NTEXMethod method, Map<String, NTEXMethod> availableMethods) {
            super(method.getName(), method.getParams(), method.isExternal(), method.getReturnType());
            this.availableMethods = availableMethods;
            this.out = ByteStreams.newDataOutput();
            this.line = line;
            try {
                compileFunction();
                try (OutputStream outputStream = new FileOutputStream(getName() + ".method")) {
                    outputStream.write(out.toByteArray());
                }
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
                case PLUS -> {return 0x08;}
                case MINUS -> {return 0x09;}
                case DIVIDE -> {return 0x0A;}
                case MULTIPLY -> {return 0x0B;}
                case MODULO -> {return 0x0C;}
                default -> {
                    assert true; // unknown operator
                    return -1;
                }
            }
        }

        private static boolean isArithmetic(String data){
            return data.indexOf('+') != -1 || data.indexOf('-') != -1 ||
                    data.indexOf('/') != -1 || data.indexOf('%') != -1 ||
                    data.indexOf('*') != -1;
        }

        private void compileFunction() throws IOException{ // kompilacja funkcji
            System.out.println(getName());
            String code = line.substring(line.indexOf('{') + 1, line.indexOf('}')); // wyodrebnij sam kod
            if(code.length() == 0){
                System.out.println("------------");
                return;
            }

            for(String data : code.split("\\s*;\\s*")){ // petla instrukcji
                int latestSpace = data.indexOf(' ');

                if(latestSpace != -1 && data.substring(0, data.indexOf(' ')).equals("int")){ // jesli instrukcja rozpoczyna sie od int skompiluj jako zmienna
                    out.writeByte(0x01); // zapisywanie że jest to instrukcja deklaracji zmiennej int
                    int id = varIds.getAndIncrement();
                    out.writeInt(id);
                    String value = data.substring(data.indexOf('=') + 1).trim();
                    String varName = data.substring(data.indexOf(' '), data.indexOf('=') - 1).trim();
                    if(isArithmetic(value)){
                        System.out.println("AOR: " + data);
                        ArithmeticOperator operator = getOperator(value);
                        assert operator != null;
                        out.writeByte(0x0d);
                        if(StringUtils.isNumeric(value.substring(0, value.indexOf(' ')))){
                            out.writeByte(0x0f);
                        }else {
                            String var = value.substring(0, value.indexOf(' '));
                            if(!variables_registry.containsKey(var)){
                                throw new RuntimeException("unknown reference to " + var + " on " + data);
                            }else {
                                int varId = variables_registry.get(var);
                                out.writeByte(0x10);
                                out.writeInt(varId);
                            }
                        }

                    }else if(value.length() != 0 && StringUtils.isNumeric(value)){
                        System.out.println("SD: " + data);
                    }else {

                        throw new RuntimeException("unknown operator on: "  + data);
                    }
                    variables_registry.put(varName, id);
                    variables.put(id, value);
                }else if(data.indexOf('(') != -1 && availableMethods.containsKey(data.substring(0, data.indexOf('(')))){ // jesli instrukcja zawiera nazwe znanej funkcji skompiluj jako wywolanie
                    String name = data.substring(0, data.indexOf('(')); // wyodrebianie nazwy funkcji
                    System.out.println("calling declaration: " + name);
                    out.writeByte(0x03); // zapisywanie ze jest to instrukcja dzwoniąca
                    out.writeByte(name.length()); // zapisywanie rozmiaru nazwy funkji
                    for(int i = 0; i < name.length(); i++){ // zapisywanie nazwy
                        out.writeByte((byte) (0xff & name.charAt(i)));
                    }
                }else if(data.contains("return")){
                    if(data.indexOf('"') != -1 && data.substring(data.indexOf('"') + 1).indexOf('"') != -1){ // static string return (sreturn)
                        System.out.println("sreturn: " + data.substring(data.indexOf(' ') + 2, data.length() - 1));
                    }else if(StringUtils.isNumeric(data.substring(data.indexOf(' ')))){
                        System.out.println("returned type: sreturn (number): " + data.substring(data.indexOf(' ')));
                    } else if(isArithmetic(data)){
                        System.out.println("areturn");
                    }else if(variables.containsKey(data.substring(data.indexOf(' ')))){ // pointer return (preturn)
                        System.out.println("returned type is preturn");
                    }else {
                        throw new RuntimeException("unknown return type for function: " + getName());
                    }
                }
                else {
                    throw new RuntimeException("Undefined instruction: " + data); // niezdefiniowana instrukcja
                }
            }
            System.out.println("------------");
        }


        private ByteArrayDataOutput getOut() {
            return out;
        }
    }


    private NTEXMethod parseMethod(String line){
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

        return new NTEXMethod(name, method_params, isexternal, returnType);
    }

    private void load_lib(String filename){
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            compileSource(br);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDeclaration(String line){
        NTEXMethod method = parseMethod(line);
        System.out.println("declaration: " + method.getName());
        methods.put(method.getName(), method);
    }

    private void compileSource(BufferedReader br) throws IOException {
        String st;
        while (true) {
            st = br.readLine();
            if(st == null){
                break;
            }

            st = st.trim();

            if(st.equalsIgnoreCase(".end")){
                System.out.println("end file");
                break;
            }

            if (st.length() == 0) {
                continue;
            }

            if(st.indexOf('$') != -1){
                if(st.contains("$use")){
                    String contain_element = st.substring(st.indexOf('"') + 1, st.lastIndexOf('"'));
                    load_lib(contain_element);
                }
            }

            if (st.length() > 4) {
                if (st.substring(0, 4).equalsIgnoreCase("func")) {
                    if(st.lastIndexOf(';') == st.length() - 1){
                        loadDeclaration(st);
                    }else {
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
                        NTEXMethod method = parseMethod(builder.toString());
                        CompiledNTEXMethod compiledNTEXMethod = new CompiledNTEXMethod(builder.toString(), method, methods);
                        methods.put(method.getName(), method);
                        methods_implementations.put(method.getName(), compiledNTEXMethod);
                    }
                }

            }
        }
    }

    @Override
    public ByteBuffer compile(String file) {
        try {
            BufferedReader br
                    = new BufferedReader(new FileReader(file));

            compileSource(br);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        ByteArrayDataOutput out =  ByteStreams.newDataOutput();
        NTExecutableBuilder builder = new NTExecutableBuilder(20, 25, "testapp", 20, 20, 20);
        byte[] header = builder.build();
        out.write(header);
        for (var entry : methods_implementations.entrySet()) {
            CompiledNTEXMethod method =  entry.getValue();
            for(int i = 0; i < method.getName().length(); i++){ // zapisywanie nazwy
                out.writeByte((byte) (0xff & method.getName().charAt(i)));
            }
            out.write(method.getOut().toByteArray());
        }

        try(OutputStream outputStream = new FileOutputStream("APP2.NTE")) {
            outputStream.write(out.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
