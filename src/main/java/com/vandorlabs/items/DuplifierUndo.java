package com.vandorlabs.items;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One bounded settings-only operation per player; inventories and ownership are never restored. */
public final class DuplifierUndo {
    private static final class Entry {
        final Block block;final NBTTagCompound before;NBTTagCompound after;
        Entry(Block block,NBTTagCompound before){this.block=block;this.before=before.copy();}
    }
    private static final class Operation {
        final World world;final Map<BlockPos,Entry> entries=new LinkedHashMap<>();
        Operation(World world){this.world=world;}
    }
    private static final Map<EntityPlayer,Operation> history=new WeakHashMap<>(),pending=new WeakHashMap<>();
    private DuplifierUndo(){}
    private static NBTTagCompound capture(World world,BlockPos pos){
        NBTTagCompound settings=ProgrammableSettings.capture(world,pos);net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        if(settings!=null&&settings.hasKey(ProgrammableSettings.FACE_TEXTURES,10)&&tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)
            settings.getCompoundTag(ProgrammableSettings.FACE_TEXTURES).setIntArray("choices",((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).getFaceTextures().choices());
        return settings;
    }
    public static void begin(EntityPlayer player,World world){if(player!=null&&!world.isRemote)pending.put(player,new Operation(world));}
    public static void before(EntityPlayer player,World world,BlockPos pos){
        Operation operation=pending.get(player);if(operation==null||operation.world!=world||operation.entries.containsKey(pos)||operation.entries.size()>=DuplifierConnectedApply.MAX_CELLS)return;
        NBTTagCompound settings=capture(world,pos);
        if(settings!=null)operation.entries.put(pos.toImmutable(),new Entry(world.getBlockState(pos).getBlock(),settings));
    }
    public static void finish(EntityPlayer player){
        Operation operation=pending.remove(player);if(operation==null)return;
        Iterator<Map.Entry<BlockPos,Entry>> iterator=operation.entries.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,Entry> value=iterator.next();value.getValue().after=capture(operation.world,value.getKey());
            if(value.getValue().after==null||value.getValue().before.equals(value.getValue().after))iterator.remove();
        }
        if(!operation.entries.isEmpty())history.put(player,operation);
    }
    public static boolean undo(EntityPlayer player){
        Operation operation=history.get(player);if(operation==null||operation.world!=player.world||player.isSpectator())return false;
        // Preflight every target before changing any settings; never load chunks or overwrite later edits.
        for(Map.Entry<BlockPos,Entry> value:operation.entries.entrySet()){
            BlockPos pos=value.getKey();Entry entry=value.getValue();
            if(!operation.world.isBlockLoaded(pos)||!operation.world.isBlockModifiable(player,pos)
                    ||!player.canPlayerEdit(pos,EnumFacing.UP,player.getHeldItemMainhand())
                    ||operation.world.getBlockState(pos).getBlock()!=entry.block
                    ||!entry.after.equals(capture(operation.world,pos)))return false;
        }
        Iterator<Map.Entry<BlockPos,Entry>> iterator=operation.entries.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<BlockPos,Entry> value=iterator.next();
            if(!ProgrammableSettings.apply(operation.world,value.getKey(),value.getValue().before,player))return false;
            iterator.remove();
        }
        history.remove(player);return true;
    }
}
