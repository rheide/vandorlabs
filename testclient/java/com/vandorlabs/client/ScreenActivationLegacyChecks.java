package com.vandorlabs.client;
import com.vandorlabs.tiles.DisplayActivation;
import net.minecraft.nbt.*;
/** Actual legacy four-state display activation before extraction. */
public final class ScreenActivationLegacyChecks {
 public static void main(String[] args)throws Exception{
  java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(int mode=-1;mode<=3;mode++)for(int length=0;length<=5;length++)for(int kind=0;kind<8;kind++){
   NBTTagCompound root=new NBTTagCompound(),saved=new NBTTagCompound();saved.setInteger("Mode",mode);int[] t=new int[length],p=new int[length],s=new int[length];java.util.Arrays.fill(t,-1);java.util.Arrays.fill(p,-1);java.util.Arrays.fill(s,-1);
   if(length>0){t[0]=kind==1?-2:kind==2?Integer.MAX_VALUE:kind==3?0:-1;p[0]=kind==4?3:kind==5?2:-1;s[0]=kind==6?3:kind==7?2:-1;}saved.setIntArray("Textures",t);saved.setIntArray("Playback",p);saved.setIntArray("Speeds",s);root.setTag("DisplayActivation",saved);root.setString("Foreign","retained");
   DisplayActivation value=new DisplayActivation();value.read(root);digest(hash,value,root);cases++;
   value.inheritUnset(0,2,1);digest(hash,value,root);cases++;
   for(int m=0;m<3;m++){value.configure(m,m,0,m,m);value.inheritUnset(-1,1,2);digest(hash,value,root);cases++;}
  }
  for(int kind=0;kind<4;kind++){NBTTagCompound root=new NBTTagCompound();if(kind==1)root.setString("DisplayActivation","future");if(kind==2)root.setTag("DisplayActivation",new NBTTagCompound());if(kind==3){NBTTagCompound child=new NBTTagCompound();child.setString("Mode","2");child.setIntArray("Textures",new int[]{-1,-1,-1,-1});child.setIntArray("Playback",new int[]{-1,-1,-1,-1});child.setIntArray("Speeds",new int[]{-1,-1,-1,-1});root.setTag("DisplayActivation",child);}DisplayActivation value=new DisplayActivation();value.read(root);digest(hash,value,root);cases++;}
  StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format("%02x",b&255));if(!out.toString().equals("ff9da69468cea1e5270f0c21420912ff37fd7bea9b881bf6c0e31cf7a68bb783"))throw new AssertionError("Actual source changed "+out);System.out.println("Actual Screen activation reference PASS cases="+cases+" sha256="+out);
 }
 private static void digest(java.security.MessageDigest hash,DisplayActivation value,NBTTagCompound input)throws Exception{StringBuilder text=new StringBuilder().append(value.mode);for(int i=0;i<4;i++)text.append(':').append(value.texture(i)).append(',').append(value.playback(i)).append(',').append(value.speed(i));NBTTagCompound saved=input.copy();value.write(saved);text.append(canonical(saved));hash.update(text.toString().getBytes("UTF-8"));}
 private static String canonical(NBTTagCompound tag){StringBuilder out=new StringBuilder("{");for(String key:new java.util.TreeSet<String>(tag.getKeySet())){NBTBase value=tag.getTag(key);out.append(key).append(':').append(value instanceof NBTTagCompound?canonical((NBTTagCompound)value):value.toString()).append(';');}return out.append('}').toString();}
}
