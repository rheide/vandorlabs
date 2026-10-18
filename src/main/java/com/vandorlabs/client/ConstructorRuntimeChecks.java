package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Independent world-space frames: every core corner, frame axis and placed facing. */
final class ConstructorRuntimeChecks {
    static void run(World world){
        Block matter=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_matter_block"));
        BlockPos minimum=new BlockPos(150,225,150);int count=0;
        for(String id:new String[]{"assembly_press","synthesis_tower","enclosed_gantry"})for(boolean turned:new boolean[]{false,true})for(int corner=0;corner<4;corner++)for(EnumFacing facing:EnumFacing.HORIZONTALS){
            BlockConstructorCore block=(BlockConstructorCore)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+id+"_core"));
            int width=turned?3:block.width,depth=turned?block.width:3;
            BlockPos root=minimum.add((corner&1)==0?0:width-1,0,(corner&2)==0?0:depth-1);
            java.util.Map<BlockPos,Boolean> original=new java.util.HashMap<>();
            try{
                world.setBlockState(root,block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing));
                TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(root);
                for(int y=0;y<block.height;y++)for(int x=0;x<width;x++)for(int z=0;z<depth;z++){
                    BlockPos p=minimum.add(x,y,z);if(p.equals(root))continue;
                    boolean frame=y==0 || (x==0 || x==width-1) && (z==0 || z==depth-1) || y==block.height-1 && (x==0 || x==width-1 || z==0 || z==depth-1);
                    original.put(p,frame);world.setBlockState(p,frame?matter.getDefaultState():Blocks.AIR.getDefaultState());
                }
                BlockPos middle=minimum.add(1,1,1);world.setBlockState(middle,Blocks.DIRT.getDefaultState());
                require(!tile.assemble(),"occupied interior rejects assembly "+id+facing);
                require(tile.assemblyProblem().contains("Interior must be air"),"actionable obstruction diagnostic");world.setBlockToAir(middle);
                BlockPos missing=minimum.add(1,0,0);world.setBlockToAir(missing);
                require(!tile.assemble(),"missing base rejects assembly");world.setBlockState(missing,matter.getDefaultState());
                require(tile.assemble() && tile.formed(),"frame assembles "+id+facing+" corner="+corner+" turned="+turned);
                for(BlockPos p:original.keySet())require(world.getTileEntity(p) instanceof TileEntityConstructorPart && ((TileEntityConstructorPart)world.getTileEntity(p)).owner().equals(root),"owned world-space footprint");
                AxisAlignedBB bounds=tile.getRenderBoundingBox();require(bounds.minX==minimum.getX() && bounds.minZ==minimum.getZ() && bounds.maxX==minimum.getX()+width && bounds.maxZ==minimum.getZ()+depth,"render origin and rotated bounds");
                TileEntityConstructor reloaded=new TileEntityConstructor();reloaded.setWorld(world);reloaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(reloaded.pivot().equals(tile.pivot()) && reloaded.cell(0,0,0).equals(tile.cell(0,0,0)),"saved corner offset");
                TileEntityConstructor client=new TileEntityConstructor();client.setWorld(world);client.setPos(root);client.onDataPacket(null,tile.getUpdatePacket());
                require(client.pivot().equals(tile.pivot()),"client update corner offset");
                tile.dismantle();require(!tile.formed(),"manual dismantling");checkRestored(world,original,matter);
                require(tile.assemble(),"reassembly from non-default corner");world.setBlockToAir(root);checkRestored(world,original,matter);count++;
            }finally{
                world.setBlockToAir(root);for(BlockPos p:original.keySet())world.setBlockToAir(p);
            }
        }
        System.out.println("[vandorlabs][reprolab] constructors PASS "+count+" independent frames all corners axes facings diagnostics persistence sync dismantling bounds");
    }
    private static void checkRestored(World world,java.util.Map<BlockPos,Boolean> original,Block matter){for(java.util.Map.Entry<BlockPos,Boolean> entry:original.entrySet())require(entry.getValue()?world.getBlockState(entry.getKey()).getBlock()==matter:world.isAirBlock(entry.getKey()),"restored frame without matter loss");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
