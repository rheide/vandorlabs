package com.vandorlabs.vehicle;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
/** Actual known-state power callbacks over a capability-free snapshot; unknown producers remain pending. */
public final class CouplerPowerChecks {
    private static int checks;private static volatile Boolean sink;
    private static void need(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    public static final class TileProducer extends Block {
        static int queries,factories;
        TileProducer(){super(Material.CIRCUITS);setRegistryName("coupler_probe:tile_power");}
        @Override public boolean isNormalCube(IBlockState state){return false;}
        @Override public boolean canProvidePower(IBlockState state){return true;}
        @Override public int getWeakPower(IBlockState state,IBlockAccess world,BlockPos pos,EnumFacing side){queries++;world.getTileEntity(pos);throw new AssertionError("Tile access must stay pending");}
        @Override public boolean hasTileEntity(IBlockState state){return true;}
        @Override public net.minecraft.tileentity.TileEntity createTileEntity(World world,IBlockState state){factories++;throw new AssertionError("No tile/capability factory is permitted");}
    }
    private static VehicleArmCouplerPower snapshot(Block block){return new VehicleArmCouplerPower(VehicleStructure.display(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null),new VehicleStructure.Cell(BlockPos.ORIGIN.east(),block.getDefaultState(),null))));}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();TileProducer tile=new TileProducer();external.couplerprobe.UnknownPowerBlock unknown=new external.couplerprobe.UnknownPowerBlock();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(tile);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(unknown);
        VehicleArmCouplerPower empty=snapshot(Blocks.AIR),on=snapshot(Blocks.REDSTONE_BLOCK),pending=snapshot(tile),foreign=snapshot(unknown);
        need(Boolean.FALSE.equals(empty.powered(BlockPos.ORIGIN)),"head-only/air payload resolves immediately without prepared view");need(Boolean.TRUE.equals(on.powered(BlockPos.ORIGIN)),"actual vanilla local redstone resolves");need(pending.powered(BlockPos.ORIGIN)==null&&TileProducer.queries>0&&TileProducer.factories==0,"tile-dependent known callback stays pending without manufacturing a capability");need(foreign.powered(BlockPos.ORIGIN)==null&&external.couplerprobe.UnknownPowerBlock.calls==0,"unknown callback not invoked");
        int calls=TileProducer.queries;for(int i=0;i<10000;i++){sink=empty.powered(BlockPos.ORIGIN);sink=on.powered(BlockPos.ORIGIN);sink=pending.powered(BlockPos.ORIGIN);}need(TileProducer.queries==calls,"immutable layout caches pending and resolved local power");
        java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();if(base instanceof com.sun.management.ThreadMXBean){com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++){sink=empty.powered(BlockPos.ORIGIN);sink=on.powered(BlockPos.ORIGIN);sink=pending.powered(BlockPos.ORIGIN);}long bytes=bean.getThreadAllocatedBytes(thread)-before;need(bytes==0,"cached power queries allocate "+bytes);}
        System.out.println("Captured Coupler power PASS checks="+checks+" air/known power/unknown pending/no tile factories/cached30000queries0B");
    }
}
