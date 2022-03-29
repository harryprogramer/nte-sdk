package pgts.nte.sdk.ntex.decompiler.ntex1;

import pgts.nte.sdk.ntex.NTEXMethod;
import pgts.nte.sdk.ntex.compiler.ntex1.NTEX1Compiler;
import pgts.nte.sdk.ntex.decompiler.NTEXDecompiler;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static pgts.nte.sdk.ntex.compiler.ntex1.NTEX1Instructions.*;

public class NTEX1Decompiler implements NTEXDecompiler {

    public enum DeclareType {
        AOR("aor"), SD("sd")
        ;

        public final String name;

        DeclareType(String name) {
            this.name = name;
        }


    }

    private ByteBuffer getBuffer(String filename){
        ByteBuffer buffer;
        try {
            RandomAccessFile aFile = new RandomAccessFile(filename, "r");

            FileChannel inChannel = aFile.getChannel();
            long fileSize = inChannel.size();

            buffer = ByteBuffer.allocate((int) fileSize);
            inChannel.read(buffer);
            buffer.flip();
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
        return buffer;
    }

    private String readString(ByteBuffer buffer){
        byte[] data = new byte[12];
        for(int i = 0; i < 12; i++){
            byte buffer_byte = buffer.get();
            if(buffer_byte == 0){
                data[i] = (byte) ' ';
                continue;
            }
            data[i] = buffer_byte;
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private DeclareType getDeclareType(byte type){
        if(type == 0x0d){
            return DeclareType.AOR;
        }else if(type == 0x0f){
            return DeclareType.SD;
        }else{
            throw new RuntimeException("unknown declare type");
        }
    }

    private NTEXMethod.ArithmeticOperator geArithmeticOperator(byte operator){
        return null;
    }

    private void dumpInt(ByteBuffer buffer){
        int startPos = buffer.position();
        int pointer = buffer.getInt();
        DeclareType declareType = getDeclareType(buffer.get());
        String value = "unknown";
        if(declareType == DeclareType.AOR){

        }else if(declareType == DeclareType.SD){
            value = String.valueOf(buffer.getInt());
        }
        System.out.println("int, " + pointer + ", $" + declareType + " " + value);
    }

    private void dumpFunctionHeader(ByteBuffer buffer){

    }

    private void dumpFunctions(ByteBuffer buffer){
        buffer.position(32); // skip header
        while (buffer.capacity() != buffer.position()){
            byte currentByte = buffer.get();
            //System.out.printf(buffer.position() + ": 0x%01X%n", currentByte);
            switch (currentByte){
                case (byte) 0xFF -> {

                }
                case ntexInt -> {
                    try {
                        dumpInt(buffer);
                    }catch (Exception e){
                        System.out.println("on position " + buffer.position() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public String dumpInfo(String file) {
        StringBuilder builder = new StringBuilder();
        ByteBuffer buffer = getBuffer(file);
        if(buffer == null){
            return "";
        }
        try {
            int appVersion = buffer.getInt();
            short nteVersion = buffer.getShort();
            String appName = readString(buffer);

            builder.append("decompiler analysis about ").append(file);
            builder.append("\n\n    header:");
            builder.append("\napp name: ").append(appName);
            builder.append("\napp version: ").append(appVersion);
            builder.append("\n\n    obj dump:");
            System.out.println(builder);

            dumpFunctions(buffer);
        }catch (Exception e){
            e.printStackTrace();
        }
        return builder.toString();
    }

}
