package com.cockpit406.diagnostic;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;

/** Reads complete journal lines only, including a concurrently growing journal. */
public final class BackupChunks {
    public static final int MAX=256*1024;
    public static byte[] read(File file,long offset)throws IOException{
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){
            if(offset<0||offset>input.length())throw new IOException("Position de sauvegarde incohérente");
            byte[] buffer=new byte[(int)Math.min(MAX,input.length()-offset)];input.seek(offset);input.readFully(buffer);
            int end=buffer.length;while(end>0&&buffer[end-1]!='\n')end--;
            return Arrays.copyOf(buffer,end);
        }
    }
    public static String digest(byte[] data){
        try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private BackupChunks(){}
}
