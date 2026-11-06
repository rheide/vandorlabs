package com.vandorlabs.client;
import com.google.gson.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
/** Actual resource-aware released border loader over every authored Frame choice. */
public final class TrapdoorBorderLegacyOracle {
 public static void main(String[] args)throws Exception{
  Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);for(Field f:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==Minecraft.class){f.setAccessible(true);f.set(null,mc);}
  Object manager=Proxy.newProxyInstance(IResourceManager.class.getClassLoader(),new Class[]{IReloadableResourceManager.class},(proxy,method,values)->{
   if(method.getName().equals("getResource")){ResourceLocation file=(ResourceLocation)values[0];InputStream input=TrapdoorBorderLegacyOracle.class.getResourceAsStream("/assets/"+file.getResourceDomain()+"/"+file.getResourcePath());if(input==null)input=new SharedTextureResources().getInputStream(file);final InputStream stream=input;return Proxy.newProxyInstance(IResource.class.getClassLoader(),new Class[]{IResource.class},(resource,call,a)->call.getName().equals("getInputStream")?stream:call.getName().equals("getResourceLocation")?file:call.getName().equals("getResourcePackName")?"oracle":call.getReturnType()==boolean.class?false:null);}
   return null;
  });for(Field f:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&IResourceManager.class.isAssignableFrom(f.getType())){f.setAccessible(true);f.set(mc,manager);}
  File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();int profiles=0;try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(int i=0;i<ScreenHousingTextures.BUILTIN_COUNT;i++){int choice=ScreenHousingTextures.choiceAt(i);if(!FrameAppearance.valid(choice))continue;JsonObject row=new JsonObject();row.addProperty("choice",choice);row.addProperty("aspect",FrameAppearance.aspect(choice));row.addProperty("border",FrameAppearance.border(choice));JsonArray borders=new JsonArray();for(double border:ScreenFrameBorders.get(choice))borders.add(border);row.add("borders",borders);out.println(new Gson().toJson(row));profiles++;}}
  SegmentedReferenceGolden.check(file,"fd5460307525f6f15e2320e6c752094ac5a59104975fa826a3796a02e633a484");
  if(profiles!=70)throw new AssertionError("Authored Frame coverage "+profiles);System.out.println("Trapdoor actual authored border PASS profiles="+profiles+" resource-aware128px frames/asymmetric alpha extents/aspect1and2");
 }
}
