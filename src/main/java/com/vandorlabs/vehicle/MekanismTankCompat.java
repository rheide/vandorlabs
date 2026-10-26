package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Native Mekanism 9 Dynamic Tank caches are saved in each member, sharing one inventory ID. */
public final class MekanismTankCompat {
    private static final String PREFIX="mekanism.common.tile.TileEntity";
    public static boolean tank(TileEntity tile){String name=tile==null?"":tile.getClass().getName();return name.equals(PREFIX+"DynamicTank")||name.equals(PREFIX+"DynamicValve");}
    public static boolean glass(TileEntity tile){return tile!=null&&tile.getClass().getName().equals(PREFIX+"StructuralGlass");}
    public static boolean component(TileEntity tile){return tank(tile)||glass(tile);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    /** Bounded native shell validation also prevents discovery/coupling from taking only one wall. */
    public static Set<BlockPos> required(World world,BlockPos start){
        TileEntity initial=world.getTileEntity(start);if(!component(initial))return Collections.emptySet();
        if(glass(initial))try{if(initial.getClass().getField("master").get(initial)==null)return Collections.emptySet();}catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot inspect Mekanism Structural Glass links",failure);}
        Set<BlockPos> cells=new LinkedHashSet<>(),visited=new HashSet<>();ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(start);boolean hasTank=false;
        int minX=start.getX(),maxX=minX,minY=start.getY(),maxY=minY,minZ=start.getZ(),maxZ=minZ;
        NBTTagCompound contents=null;String cacheId=null;
        while(!pending.isEmpty()){
            BlockPos at=pending.removeFirst();if(!visited.add(at))continue;require(world.isBlockLoaded(at,false),"Load the complete Mekanism Dynamic Tank");TileEntity tile=world.getTileEntity(at);if(!component(tile))continue;
            require(cells.size()<2048,"Mekanism tank component exceeds the native shell limit");cells.add(at.toImmutable());hasTank|=tank(tile);
            minX=Math.min(minX,at.getX());maxX=Math.max(maxX,at.getX());minY=Math.min(minY,at.getY());maxY=Math.max(maxY,at.getY());minZ=Math.min(minZ,at.getZ());maxZ=Math.max(maxZ,at.getZ());
            require(maxX-minX<18&&maxY-minY<18&&maxZ-minZ<18,"Separate adjoining Mekanism tank assemblies before moving them");
            if(tank(tile)){
                NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());if(data.hasKey("cachedID",8)){
                    String id=data.getString("cachedID");NBTTagCompound stored=contents(data);
                    if(cacheId==null){cacheId=id;contents=stored;}else require(cacheId.equals(id)&&contents.equals(stored),"Wait for the Mekanism tank contents to finish updating");
                }
                currentCache(tile);
            }
            for(EnumFacing side:EnumFacing.values())pending.add(at.offset(side));
        }
        if(!hasTank){
            // Unlinked Structural Glass is an ordinary building block. A live link to
            // another native multiblock must not be mistaken for a Dynamic Tank.
            if(!(world instanceof VehicleWorld))try{for(BlockPos at:cells)require(world.getTileEntity(at).getClass().getField("master").get(world.getTileEntity(at))==null,"Detach Structural Glass from its native multiblock before moving it");}catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot inspect Mekanism Structural Glass links",failure);}
            return cells;
        }
        require(maxX-minX>=2&&maxY-minY>=2&&maxZ-minZ>=2,"Include the complete Mekanism Dynamic Tank, valves and Structural Glass");
        for(BlockPos at:BlockPos.getAllInBox(new BlockPos(minX,minY,minZ),new BlockPos(maxX,maxY,maxZ))){
            require(world.isBlockLoaded(at,false),"Load the entire Mekanism tank shell and interior");int boundaries=(at.getX()==minX||at.getX()==maxX?1:0)+(at.getY()==minY||at.getY()==maxY?1:0)+(at.getZ()==minZ||at.getZ()==maxZ?1:0);
            if(boundaries==0)require(world.isAirBlock(at),"Mekanism Dynamic Tank interior must remain empty");
            else{require(cells.contains(at),"Include every wall of the Mekanism Dynamic Tank");if(boundaries>=2)require(world.getTileEntity(at).getClass().getName().equals(PREFIX+"DynamicTank"),"Mekanism tank edges and corners require Dynamic Tank blocks");}
        }
        return Collections.unmodifiableSet(cells);
    }
    private static NBTTagCompound contents(NBTTagCompound data){NBTTagCompound result=new NBTTagCompound();for(String key:new String[]{"cachedFluid","Items","editMode"})if(data.hasKey(key))result.setTag(key,data.getTag(key).copy());return result;}
    private static void currentCache(TileEntity tile){
        try{Object structure=tile.getClass().getField("structure").get(tile);if(structure==null)return;Object cache=tile.getClass().getField("cachedData").get(tile),fresh=tile.getClass().getMethod("getNewCache").invoke(tile);
            for(java.lang.reflect.Method method:fresh.getClass().getMethods())if(method.getName().equals("sync")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(structure)){method.invoke(fresh,structure);break;}
            NBTTagCompound a=new NBTTagCompound(),b=new NBTTagCompound();cache.getClass().getMethod("save",NBTTagCompound.class).invoke(cache,a);fresh.getClass().getMethod("save",NBTTagCompound.class).invoke(fresh,b);
            require(a.equals(b),"Wait for the Mekanism tank contents to finish updating");
        }catch(ReflectiveOperationException failure){throw new IllegalArgumentException("Cannot preserve Mekanism tank cache",failure);}
    }
    private MekanismTankCompat(){}
}
