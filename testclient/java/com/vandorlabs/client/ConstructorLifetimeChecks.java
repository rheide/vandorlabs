package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** A render list can outlive its core block, especially during shadow passes. */
final class ConstructorLifetimeChecks {
    static void run(){
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityConstructor.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_data_check_constructor"));
        int cases=0;BlockPos pos=new BlockPos(8,80,8);
        for(FrameMachineDesign spec:FrameMachineDesign.all())for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean prime:new boolean[]{false,true}){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);BlockConstructorCore block=new BlockConstructorCore(spec);
            IBlockState formed=block.getDefaultState().withProperty(BlockVandorDirectional.FACING,facing).withProperty(BlockConstructorCore.FORMED,true);world.setBlockState(pos,formed,2);
            TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(pos);
            if(prime){tile.getBlockType();tile.getRenderBoundingBox();tile.hasFastRenderer();}
            // Retain the stale tile as RenderGlobal/OptiFine can, including the
            // interval where block state changes before the tile is invalidated.
            world.states.put(pos,Blocks.AIR.getDefaultState());
            check(tile,pos);world.states.put(pos,Blocks.STONE.getDefaultState());check(tile,pos);
            tile.invalidate();check(tile,pos);world.states.put(pos,formed);check(tile,pos);
            TileEntityConstructor detached=new TileEntityConstructor();detached.setPos(pos);check(detached,pos);
            cases++;
        }
        System.out.println("Constructor lifetime PASS cases="+cases+" all designs facings primed/unprimed stale air/stone invalidated detached tiles safe bounds render path NBT");
    }
    private static void check(TileEntityConstructor tile,BlockPos pos){
        AxisAlignedBB bounds=tile.getRenderBoundingBox();if(!bounds.equals(new AxisAlignedBB(pos)))throw new AssertionError("Stale core must have local finite bounds");
        if(tile.formed()||tile.mount()!=null||tile.getOutputLevel()!=0||!tile.hasFastRenderer()||tile.assemble())throw new AssertionError("Stale constructor must remain inert");
        tile.pivot();tile.facing();if(tile.writeToNBT(new NBTTagCompound())==null)throw new AssertionError("Stale save");
    }
}
