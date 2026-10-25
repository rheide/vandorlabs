package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.property.IExtendedBlockState;

/** Actual legacy light adapter and immutable snapshot contract over asymmetric neighbour light. */
final class LegacyHousingLightingChecks {
    private LegacyHousingLightingChecks(){}
    static void run(){
        MixedLight world=new MixedLight();int expected=0x00F000E0;
        need(ProgrammableHousingState.neighborLight(world,BlockPos.ORIGIN)==expected,"different bright sky/block neighbours combine");need(world.reads==6,"six neighbour reads only");
        for(net.minecraft.block.Block block:new net.minecraft.block.Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK}){
            IBlockState state=block.getDefaultState();need(block.getPackedLightmapCoords(state,world,BlockPos.ORIGIN)==expected,"actual block light adapter "+block.getRegistryName());
            IBlockState snapshot=((IExtendedBlockState)state).withProperty(ProgrammableHousingState.LIGHT,0x00400070);int before=world.reads;need(ProgrammableHousingState.light(snapshot,world,BlockPos.ORIGIN)==0x00400070&&world.reads==before,"immutable extended light snapshot");
        }
        need(Math.max(0x00F00020,0x003000E0)!=expected,"whole packed maximum negative control");
        System.out.println("[vandorlabs][reprolab] housing-lighting PASS actualcube/slab/trigger light adapters, mixedsky/torch componentMAX and immutable snapshot");
    }
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Legacy housing light: "+message);}
    private static final class MixedLight implements IBlockAccess {
        int reads;
        @Override public int getCombinedLight(BlockPos pos,int minimum){reads++;return pos.equals(BlockPos.ORIGIN.up())?0x00F00020:pos.equals(BlockPos.ORIGIN.west())?0x003000E0:0;}
        @Override public TileEntity getTileEntity(BlockPos pos){throw new AssertionError("Light adapter queried tile");}
        @Override public IBlockState getBlockState(BlockPos pos){throw new AssertionError("Light adapter queried state");}
        @Override public boolean isAirBlock(BlockPos pos){throw new AssertionError("Light adapter queried air");}
        @Override public Biome getBiome(BlockPos pos){throw new AssertionError("Light adapter queried biome");}
        @Override public int getStrongPower(BlockPos pos,EnumFacing side){throw new AssertionError("Light adapter queried power");}
        @Override public WorldType getWorldType(){return WorldType.DEFAULT;}
        @Override public boolean isSideSolid(BlockPos pos,EnumFacing side,boolean fallback){throw new AssertionError("Light adapter queried solidity");}
    }
}
