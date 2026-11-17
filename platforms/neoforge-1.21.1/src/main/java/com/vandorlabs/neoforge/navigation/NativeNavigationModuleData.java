package com.vandorlabs.neoforge.navigation;

import com.vandorlabs.navigation.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.*;

/** Authoritative compact circles. The spatial square index is transient and never expands coverage. */
public final class NativeNavigationModuleData {
 public static final int MAX_AREAS=1024,VERSION=1;
 public record Area(int x,int z,int radius){public Area{if(Math.abs((long)x)>NavigationMap.LIMIT||Math.abs((long)z)>NavigationMap.LIMIT||radius<1||radius>10000)throw new IllegalArgumentException("Navigation area");}}
 public static boolean item(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==NativeNavigationItems.MODULE;}
 private static CompoundTag root(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
 private static CompoundTag rootView(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();}
 private static CompoundTag data(ItemStack stack){return rootView(stack).getCompound("NavigationAreas");}
 public static boolean bound(ItemStack stack){return item(stack)&&data(stack).getInt("Version")==VERSION&&!NativeNavigationChip.dimension(data(stack)).isEmpty();}
 public static int count(ItemStack stack){var p=data(stack);if(!p.contains("Circles",11))return Math.clamp(p.getInt("AreaCount"),0,MAX_AREAS);int n=p.getIntArray("Circles").length;return n<=MAX_AREAS*3&&n%3==0?n/3:0;}
 public static List<Area> areas(ItemStack stack,Level level){if(!bound(stack)||!NativeNavigationChip.dimension(data(stack)).equals(NativeNavigationChip.dimension(level)))return List.of();int[] circles=data(stack).getIntArray("Circles");if(circles.length>MAX_AREAS*3||circles.length%3!=0)return List.of();var result=new ArrayList<Area>(circles.length/3);try{for(int i=0;i<circles.length;i+=3)result.add(new Area(circles[i],circles[i+1],circles[i+2]));}catch(IllegalArgumentException invalid){return List.of();}return List.copyOf(result);}
 private static boolean encloses(Area a,Area b){return a.radius>=b.radius&&Math.hypot((double)a.x-b.x,(double)a.z-b.z)+b.radius<=a.radius;}
 public static boolean record(ItemStack stack,Level level,List<Area> observed){if(!item(stack)||observed.isEmpty())return false;var envelope=rootView(stack);var old=data(stack);if(envelope.contains("NavigationAreas")&&!envelope.contains("NavigationAreas",10))return false;if(envelope.contains("NavigationAreas",10)&&(!old.getAllKeys().stream().allMatch(k->java.util.Set.of("Version","Dimension","DimensionKey","Circles").contains(k))||!old.contains("Version",3)||NativeNavigationChip.dimension(old).isEmpty()||old.contains("DimensionKey")&&(!old.contains("DimensionKey",8)||net.minecraft.resources.ResourceLocation.tryParse(old.getString("DimensionKey"))==null)))return false;if(rootView(stack).contains("NavigationAreas",10)&&(old.getInt("Version")!=VERSION||!old.contains("Circles",11))||bound(stack)&&!NativeNavigationChip.dimension(old).equals(NativeNavigationChip.dimension(level)))return false;var all=new ArrayList<>(areas(stack,level));if(old.getIntArray("Circles").length>0&&all.isEmpty())return false;boolean changed=false;for(var area:observed){if(all.stream().anyMatch(a->encloses(a,area)))continue;var remove=all.stream().filter(a->encloses(area,a)).toList();if(all.size()-remove.size()>=MAX_AREAS)continue;all.removeAll(remove);all.add(area);changed=true;}if(!changed)return false;var p=new CompoundTag();p.putInt("Version",VERSION);p.putString("DimensionKey",NativeNavigationChip.dimension(level));int[] circles=new int[all.size()*3];int at=0;for(var a:all){circles[at++]=a.x;circles[at++]=a.z;circles[at++]=a.radius;}p.putIntArray("Circles",circles);var tag=root(stack);tag.put("NavigationAreas",p);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));return true;}
 public static ItemStack summary(ItemStack stack){if(stack.isEmpty())return ItemStack.EMPTY;var copy=new ItemStack(stack.getItem(),1);var source=data(stack);String dimension=NativeNavigationChip.dimension(source);if(source.getInt("Version")==VERSION&&!dimension.isEmpty()&&dimension.length()<=128&&net.minecraft.resources.ResourceLocation.tryParse(dimension)!=null){var p=new CompoundTag();p.putInt("Version",VERSION);p.putString("DimensionKey",dimension);p.putInt("AreaCount",count(stack));var projected=new CompoundTag();projected.put("NavigationAreas",p);copy.set(DataComponents.CUSTOM_DATA,CustomData.of(projected));}return copy;}

 private NativeNavigationModuleData(){}
}
