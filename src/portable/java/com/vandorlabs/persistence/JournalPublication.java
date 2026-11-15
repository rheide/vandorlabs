package com.vandorlabs.persistence;

import java.io.*;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

/** Immutable byte publication, shared by engine adapters. All real I/O failures remain visible. */
public final class JournalPublication {
    public static void directories(Path path)throws IOException {
        ArrayDeque<Path> missing=new ArrayDeque<Path>();Path current=path.toAbsolutePath();while(!Files.isDirectory(current)){if(Files.exists(current))throw new IOException("Journal parent is not a directory");missing.push(current);current=current.getParent();if(current==null)throw new IOException("Journal directory has no accessible ancestor");}
        while(!missing.isEmpty()){Path next=missing.pop();Files.createDirectory(next);JournalFiles.syncDirectory(next.getParent());}
    }
    public static void write(Path file,byte[] bytes,boolean replace)throws IOException {
        directories(file.getParent());if(!replace&&Files.exists(file))throw new IOException("Journal identity already exists");
        Path temporary=file.resolveSibling(file.getFileName()+".pending-"+UUID.randomUUID());IOException failure=null;
        try{
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            if(!matches(temporary,bytes))throw new IOException("Journal byte roundtrip mismatch");
            if(!replace&&Files.exists(file))throw new IOException("Journal identity changed before publication");
            if(replace)Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);
            JournalFiles.syncDirectory(file.getParent());
        }catch(IOException unavailable){failure=unavailable;throw unavailable;}
        finally{try{Files.deleteIfExists(temporary);}catch(IOException cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}}
    }
    public static boolean matches(Path file,byte[] expected)throws IOException {
        if(Files.size(file)!=expected.length)return false;int offset=0;byte[] buffer=new byte[8192];try(InputStream input=Files.newInputStream(file)){int count;while((count=input.read(buffer))!=-1){if(count>expected.length-offset)return false;for(int i=0;i<count;i++)if(buffer[i]!=expected[offset+i])return false;offset+=count;}}return offset==expected.length;
    }
    public static void delete(Path file)throws IOException {Files.deleteIfExists(file);JournalFiles.syncDirectory(file.getParent());}
    private JournalPublication(){}
}
