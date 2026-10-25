package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.cockpit.*;
import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Simulate structure editors that translate x/y/z without knowing custom NBT links. */
final class CockpitCopyRuntimeChecks {
    static void run(WorldServer world,net.minecraft.entity.player.EntityPlayerMP actor){
        int cases=0;
        BlockPos source=new BlockPos(400,104,120),delta=new BlockPos(20,0,0);
        for(String id:CockpitData.IDS)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean legacy:new boolean[]{false,true}){
            net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id);
            world.setBlockState(source,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing),2);
            CockpitAssembly.install(world,source,actor);
            TileEntityCockpit original=(TileEntityCockpit)world.getTileEntity(source);
            java.util.List<BlockPos> footprint=original.data().cells(source,facing);
            try{
                for(BlockPos p:footprint){
                    TileEntity old=world.getTileEntity(p);NBTTagCompound tag=old.writeToNBT(new NBTTagCompound());
                    if(legacy)tag.removeTag("AnchorOffset");
                    BlockPos next=p.add(delta);tag.setInteger("x",next.getX());tag.setInteger("y",next.getY());tag.setInteger("z",next.getZ());
                    world.setBlockState(next,world.getBlockState(p),2);world.setTileEntity(next,TileEntity.create(world,tag));
                }
                TileEntityCockpit copy=(TileEntityCockpit)world.getTileEntity(source.add(delta));
                for(BlockPos p:footprint){
                    require(CockpitAssembly.root(world,p)==original,"original owner changed");
                    require(CockpitAssembly.root(world,p.add(delta))==copy,"copied part routes to original");
                }
                copy.setSideTexture(12);require(original.sideTexture()!=12,"copy shares settings");
                CockpitAssembly.remove(world,source.add(delta));
                for(BlockPos p:footprint)require(world.getTileEntity(p)!=null,"copied cockpit removal damaged original");
                cases++;
            }finally{
                for(BlockPos p:footprint){world.setBlockToAir(p.add(delta));world.setBlockToAir(p);}
            }
        }
        System.out.println("[vandorlabs][reprolab] cockpit-worldedit-copy PASS cases="+cases+" relative-links legacy-absolute-links independent-settings independent-dismantle");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
