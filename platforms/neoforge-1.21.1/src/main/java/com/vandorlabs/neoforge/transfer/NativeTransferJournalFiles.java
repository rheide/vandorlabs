package com.vandorlabs.neoforge.transfer;

import com.vandorlabs.persistence.JournalPublication;
import com.vandorlabs.persistence.JournalWorker;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.nbt.*;

/** Immutable copied journal bytes only. No worker task can inspect a world, tile or inventory. */
public final class NativeTransferJournalFiles {
    public static final int MAX_FILE_BYTES=8*1024*1024,MAX_NBT_BYTES=16*1024*1024,MAX_FILES=16;
    public record Saved(Path path,CompoundTag record){public Saved{record=record.copy();}@Override public CompoundTag record(){return record.copy();}}
    public static byte[] encode(CompoundTag record)throws IOException {
        if(record.sizeInBytes()>MAX_NBT_BYTES)throw new IOException("Transfer journal expanded byte bound");var output=new ByteArrayOutputStream();
        var shield=new FilterOutputStream(output){private int bytes;@Override public void write(int value)throws IOException{if(++bytes>MAX_FILE_BYTES)throw new IOException("Transfer journal file byte bound");out.write(value);}@Override public void write(byte[] data,int offset,int count)throws IOException{if(count>MAX_FILE_BYTES-bytes)throw new IOException("Transfer journal file byte bound");bytes+=count;out.write(data,offset,count);}@Override public void close()throws IOException{flush();}};
        NbtIo.writeCompressed(record,shield);return output.toByteArray();
    }
    public static CompoundTag read(Path file)throws IOException {
        long length=Files.size(file);if(length<1||length>MAX_FILE_BYTES)throw new IOException("Transfer journal file bound");return NbtIo.readCompressed(file,NbtAccounter.create(MAX_NBT_BYTES));
    }
    /** Expected null means a new identity; replacements compare the complete previous immutable record. */
    public static Future<?> write(Path path,CompoundTag expected,CompoundTag next)throws IOException {
        var old=expected==null?null:expected.copy();var stable=next.copy();if(stable.sizeInBytes()>MAX_NBT_BYTES)throw new IOException("Transfer journal expanded byte bound");return JournalWorker.submit(()->{
            byte[] bytes=encode(stable);
            if(old==null){if(Files.exists(path))throw new IOException("Transfer journal identity already exists");}
            else if(!read(path).equals(old))throw new IOException("Transfer journal identity changed before publication");
            JournalPublication.write(path,bytes,old!=null);if(!read(path).equals(stable))throw new IOException("Transfer journal roundtrip mismatch");return null;
        });
    }
    public static Future<?> delete(Path path,CompoundTag expected){var stable=expected.copy();return JournalWorker.submit(()->{if(!read(path).equals(stable))throw new IOException("Transfer journal changed before retirement");JournalPublication.delete(path);return null;});}
    public static Future<CompoundTag> reconcile(Path file,CompoundTag expected,boolean allowMissing){var intent=expected.copy();intent.remove("Stage");return JournalWorker.submit(()->{
        final CompoundTag actual;try{actual=read(file);}catch(NoSuchFileException absent){if(!allowMissing)throw absent;try{com.vandorlabs.persistence.JournalFiles.syncDirectory(file.getParent());}catch(NoSuchFileException noParent){/* No source mutation was admitted without an initial file. */}return null;}
        var checked=actual.copy();checked.remove("Stage");if(!checked.equals(intent)||!actual.contains("Stage",Tag.TAG_STRING)||!Set.of("PREPARED","ARMED","APPLIED","ROLLED_BACK").contains(actual.getString("Stage")))throw new IOException("Transfer journal intent changed during recovery");com.vandorlabs.persistence.JournalFiles.syncDirectory(file.getParent());return actual.copy();
    });}
    public static Future<List<Saved>> load(Path directory){return JournalWorker.submit(()->{
        final java.nio.file.attribute.BasicFileAttributes attributes;try{attributes=Files.readAttributes(directory,java.nio.file.attribute.BasicFileAttributes.class);}catch(NoSuchFileException absent){return List.of();}if(!attributes.isDirectory())throw new IOException("Transfer journal parent is not a directory");var result=new ArrayList<Saved>();long bytes=0,decoded=0;try(var paths=Files.newDirectoryStream(directory,"*.nbt")){for(var path:paths){if(result.size()>=MAX_FILES)throw new IOException("Transfer journal operation bound");long size=Files.size(path);if(size<1||size>MAX_FILE_BYTES||(bytes+=size)>MAX_NBT_BYTES)throw new IOException("Transfer journal directory byte bound");var record=read(path);decoded=Math.addExact(decoded,record.sizeInBytes());if(decoded>MAX_NBT_BYTES)throw new IOException("Transfer journal directory expanded byte bound");result.add(new Saved(path,record));}}return List.copyOf(result);
    });}
    private NativeTransferJournalFiles(){}
}
