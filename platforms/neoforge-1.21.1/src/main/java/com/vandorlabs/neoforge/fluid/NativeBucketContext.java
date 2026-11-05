package com.vandorlabs.neoforge.fluid;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
/** Explicit BucketItem callback boundary; FlowingFluid never enters this scope. */
public final class NativeBucketContext {
 private NativeBucketContext(){}
 private record Entry(Object level,long position,Object incoming){}
 private static final ThreadLocal<Entry> CURRENT=new ThreadLocal<>();
 public static boolean permits(Object level,BlockPos position){Entry entry=CURRENT.get();return entry!=null&&entry.level==level&&entry.position==position.asLong();}
 public static Object incoming(){Entry entry=CURRENT.get();return entry==null?null:entry.incoming;}
 public static <T> T with(Object level,BlockPos position,Supplier<T> action){return with(level,position,null,action);}
 public static <T> T with(Object level,BlockPos position,Object incoming,Supplier<T> action){Entry previous=CURRENT.get();CURRENT.set(new Entry(level,position.asLong(),incoming));try{return action.get();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
}
