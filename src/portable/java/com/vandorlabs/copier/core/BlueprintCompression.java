package com.vandorlabs.copier.core;

import java.io.*;
import java.util.zip.GZIPInputStream;

/** The existing Vehicle Chip wire codec, without any vehicle runtime or world dependency. */
public final class BlueprintCompression {
    public static final int MAX_STORED_BYTES=768*1024,MAX_DECODED_BYTES=4*1024*1024;
    public static byte[] decode(int codec,byte[] stored)throws IOException{
        if(codec==0){if(stored.length>MAX_DECODED_BYTES)throw new IOException("Vehicle Chip expands beyond the design data limit");return stored.clone();}
        if(codec!=1||stored.length>MAX_STORED_BYTES)throw new IllegalArgumentException("Invalid Vehicle Chip codec or capacity");
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(InputStream input=new GZIPInputStream(new ByteArrayInputStream(stored))){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(result.size()>MAX_DECODED_BYTES-count)throw new IOException("Vehicle Chip expands beyond the design data limit");result.write(buffer,0,count);}}
        return result.toByteArray();
    }
    private BlueprintCompression(){}
}
