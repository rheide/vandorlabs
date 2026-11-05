package com.vandorlabs.persistence;

/** Current panel/standalone glazing playback. Byte presence is deliberately strict. */
public final class GlassPlaybackData {
    public static final String MODE="GlassPlaybackMode",SPEED="GlassPlaybackSpeed";
    /** One stateless adapter may write arbitrary tag instances without per-save wrappers. */
    public interface ByteTag<T> { void putByte(T tag,String key,byte value); void remove(T tag,String key); }
    private GlassPlaybackData(){}
    public static boolean valid(int value){return value>=0&&value<=2;}
    public static int read(boolean typedByte,byte value){return typedByte&&valid(value)?value:1;}
    public static <T> void write(ByteTag<T> writer,T tag,int mode,int speed){
        if(mode!=1||speed!=1)writeSettings(writer,tag,mode,speed);
        else{writer.remove(tag,MODE);writer.remove(tag,SPEED);}
    }
    /** Copy/editor settings always include both bytes, even at the default state. */
    public static <T> void writeSettings(ByteTag<T> writer,T tag,int mode,int speed){writer.putByte(tag,MODE,(byte)mode);writer.putByte(tag,SPEED,(byte)speed);}
}
