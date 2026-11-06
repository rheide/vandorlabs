package com.vandorlabs.neoforge.screen;

import net.minecraft.nbt.*;
/** Actual legacy four-state display activation before extraction. */
public final class ScreenActivationChecks {
 public static void main(String[] args)throws Exception{
  java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(int mode=-1;mode<=3;mode++)for(int length=0;length<=5;length++)for(int kind=0;kind<8;kind++){
   CompoundTag root=new CompoundTag(),saved=new CompoundTag();saved.putInt("Mode",mode);int[] t=new int[length],p=new int[length],s=new int[length];java.util.Arrays.fill(t,-1);java.util.Arrays.fill(p,-1);java.util.Arrays.fill(s,-1);
   if(length>0){t[0]=kind==1?-2:kind==2?Integer.MAX_VALUE:kind==3?0:-1;p[0]=kind==4?3:kind==5?2:-1;s[0]=kind==6?3:kind==7?2:-1;}saved.putIntArray("Textures",t);saved.putIntArray("Playback",p);saved.putIntArray("Speeds",s);root.put("DisplayActivation",saved);root.putString("Foreign","retained");
   NativeScreenActivation value=NativeScreenActivation.read(root);digest(hash,value,root);cases++;
   value=value.inheritUnset(0,2,1);digest(hash,value,root);cases++;
   for(int m=0;m<3;m++){value=value.configure(m,m,0,m,m);value=value.inheritUnset(-1,1,2);digest(hash,value,root);cases++;}
  }
  for(int kind=0;kind<4;kind++){CompoundTag root=new CompoundTag();if(kind==1)root.putString("DisplayActivation","future");if(kind==2)root.put("DisplayActivation",new CompoundTag());if(kind==3){CompoundTag child=new CompoundTag();child.putString("Mode","2");child.putIntArray("Textures",new int[]{-1,-1,-1,-1});child.putIntArray("Playback",new int[]{-1,-1,-1,-1});child.putIntArray("Speeds",new int[]{-1,-1,-1,-1});root.put("DisplayActivation",child);}NativeScreenActivation value=NativeScreenActivation.read(root);digest(hash,value,root);cases++;}
  var full=NativeScreenActivation.LEGACY.configure(2,0,0,1,1).inheritUnset(0,1,1);for(int i=0;i<10000;i++)if(full.inheritUnset(0,1,1)!=full)throw new AssertionError("No-op activation copied");int[] exposed=full.data().textures();exposed[0]=99;if(full.data().texture(0)!=0)throw new AssertionError("Mutable activation array escaped");StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!out.toString().equals("ff9da69468cea1e5270f0c21420912ff37fd7bea9b881bf6c0e31cf7a68bb783"))throw new AssertionError("Native differs from frozen source "+out);System.out.println("Native Screen activation parity PASS cases="+cases+" sha256="+out);
 }
 private static void digest(java.security.MessageDigest hash,NativeScreenActivation value,CompoundTag input)throws Exception{StringBuilder text=new StringBuilder().append(value.data().mode);for(int i=0;i<4;i++)text.append(':').append(value.data().texture(i)).append(',').append(value.data().playback(i)).append(',').append(value.data().speed(i));CompoundTag saved=input.copy();value.write(saved);text.append(canonical(saved));hash.update(text.toString().getBytes("UTF-8"));}
 private static String canonical(CompoundTag tag){StringBuilder out=new StringBuilder("{");for(String key:new java.util.TreeSet<String>(tag.getAllKeys())){Tag value=tag.get(key);out.append(key).append(':').append(value instanceof CompoundTag?canonical((CompoundTag)value):value.toString()).append(';');}return out.append('}').toString();}
}
