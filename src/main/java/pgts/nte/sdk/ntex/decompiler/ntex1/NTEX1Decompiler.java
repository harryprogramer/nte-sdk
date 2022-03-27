package pgts.nte.sdk.ntex.decompiler.ntex1;

import pgts.nte.sdk.ntex.decompiler.NTEXDecompiler;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class NTEX1Decompiler implements NTEXDecompiler {

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
            builder.append("\napp name: ").append(appName);
            builder.append("\napp version: ").append(appVersion);
        }catch (Exception e){
            e.printStackTrace();
        }
        return builder.toString();
    }

}
