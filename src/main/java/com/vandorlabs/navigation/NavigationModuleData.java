package com.vandorlabs.navigation;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** Persist observed circles, not chunks or generated pixels. Seed terrain is reconstructed on demand. */
public final class NavigationModuleData {
    public static final int MAX_AREAS=1024,VERSION=1;
    private NavigationModuleData(){}
    public static boolean item(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==com.vandorlabs.items.ModItems.NAVIGATION_DATA_MODULE;}
    private static NBTTagCompound data(ItemStack stack){return stack.hasTagCompound()?stack.getTagCompound().getCompoundTag("NavigationAreas"):new NBTTagCompound();}
    public static boolean bound(ItemStack stack){return item(stack)&&data(stack).getInteger("Version")==VERSION&&data(stack).hasKey("Dimension",3);}
    public static int dimension(ItemStack stack){return data(stack).getInteger("Dimension");}
    public static int count(ItemStack stack){if(!data(stack).hasKey("Circles",11))return Math.max(0,Math.min(MAX_AREAS,data(stack).getInteger("AreaCount")));int[] a=data(stack).getIntArray("Circles");return a.length<=MAX_AREAS*3&&a.length%3==0?a.length/3:0;}
    public static List<NavigationSources.Area> areas(ItemStack stack,int dimension){
        if(!bound(stack)||dimension(stack)!=dimension)return Collections.emptyList();int[] a=data(stack).getIntArray("Circles");if(a.length>MAX_AREAS*3||a.length%3!=0)return Collections.emptyList();List<NavigationSources.Area> result=new ArrayList<>(a.length/3);
        for(int i=0;i<a.length;i+=3){if(Math.abs((long)a[i])>NavigationMap.LIMIT||Math.abs((long)a[i+1])>NavigationMap.LIMIT||a[i+2]<1||a[i+2]>10000)return Collections.emptyList();result.add(new NavigationSources.Area(a[i],a[i+1],a[i+2]));}return result;
    }
    private static boolean encloses(NavigationSources.Area a,NavigationSources.Area b){return a.radius>=b.radius&&Math.hypot((double)a.x-b.x,(double)a.z-b.z)+b.radius<=a.radius;}
    public static boolean record(ItemStack stack,int dimension,List<NavigationSources.Area> observed){
        if(!item(stack)||observed.isEmpty()||stack.hasTagCompound()&&stack.getTagCompound().hasKey("NavigationAreas",10)&&(data(stack).getInteger("Version")!=VERSION||!data(stack).hasKey("Circles",11))||bound(stack)&&dimension(stack)!=dimension)return false;
        List<NavigationSources.Area> all=new ArrayList<>(areas(stack,dimension));if(bound(stack)&&data(stack).getIntArray("Circles").length>0&&all.isEmpty())return false;boolean changed=false;
        for(NavigationSources.Area area:observed){if(Math.abs((long)area.x)>NavigationMap.LIMIT||Math.abs((long)area.z)>NavigationMap.LIMIT||area.radius<1||area.radius>10000)continue;boolean contained=false;for(NavigationSources.Area old:all)if(encloses(old,area)){contained=true;break;}if(contained)continue;
            List<NavigationSources.Area> remove=new ArrayList<>();for(NavigationSources.Area old:all)if(encloses(area,old))remove.add(old);
            if(all.size()-remove.size()>=MAX_AREAS)continue;all.removeAll(remove);all.add(area);changed=true;
        }
        if(!changed)return false;int[] circles=new int[all.size()*3];int at=0;for(NavigationSources.Area area:all){circles[at++]=area.x;circles[at++]=area.z;circles[at++]=area.radius;}
        NBTTagCompound tag=stack.hasTagCompound()?stack.getTagCompound().copy():new NBTTagCompound(),payload=new NBTTagCompound();payload.setInteger("Version",VERSION);payload.setInteger("Dimension",dimension);payload.setIntArray("Circles",circles);tag.setTag("NavigationAreas",payload);stack.setTagCompound(tag);return true;
    }
    public static ItemStack summary(ItemStack stack){ItemStack copy=stack.copy();if(copy.hasTagCompound()){NBTTagCompound p=data(copy);int count=count(copy);p.removeTag("Circles");p.setInteger("AreaCount",count);}return copy;}
}
