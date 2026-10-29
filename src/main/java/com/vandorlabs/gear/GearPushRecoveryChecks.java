package com.vandorlabs.gear;

import com.vandorlabs.vehicle.TransferCheckpoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** A pushed native block and a captured Surface remain single owners across a partial chunk save. */
final class GearPushRecoveryChecks {
    private static final BlockPos ROOT=new BlockPos(15,100,0),SOURCE=ROOT.east(3).up(),TARGET=ROOT.east(4).up();
    private static int stage;private static TransferCheckpoint checkpoint;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Push recovery: "+message);}
    static boolean run(WorldServer world,EntityPlayerMP actor,String phase)throws Exception{
        world.getChunkFromBlockCoords(ROOT);world.getChunkFromBlockCoords(TARGET);
        if(phase.startsWith("verify-")){
            if(ArmTransactions.locked(world,ROOT))return false;
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            require(arm!=null&&arm.payload()!=null&&!arm.payload().materialized()&&arm.payload().blocks.cells.size()==2,"captured Surface ownership survives cold restart");
            int iron=0;for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-1,-1,-1),ROOT.add(6,3,1)))if(world.getBlockState(at).getBlock()==Blocks.IRON_BLOCK)iron++;
            require(iron==1&&world.getBlockState(SOURCE).getBlock()==Blocks.IRON_BLOCK&&world.isAirBlock(TARGET),"exactly one usable pushed block rolls back to its source");
            System.out.println("[vandorlabs] arm-durable-recovery PASS "+phase+" native-push source-target exactly-one-owner retained-Surface real-process-termination cross-chunk-partial-save");return true;
        }
        if(stage==0){
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-1,-1,-1),ROOT.add(6,3,1))){world.removeTileEntity(at);world.setBlockToAir(at);}
            for(int n=0;n<4;n++){BlockPos at=ROOT.east(n);world.setBlockState(at,GearRegistry.GEAR_EXTENDER.getDefaultState(),2);GearTileEntity tile=(GearTileEntity)world.getTileEntity(at);tile.setMountDirection(EnumFacing.EAST);tile.operator(actor);}
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm.rebuild(),"native arm forms");
            net.minecraft.block.Block surface=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface");world.setBlockState(ROOT.east(4),surface.getDefaultState(),2);world.setBlockState(ROOT.east(4).up(),surface.getDefaultState(),2);arm.target(false);
            try(AutoCloseable matrix=ArmTransactions.matrix()){
                require(arm.preparePayloadMotion(),"capture source baseline");NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());saved.setBoolean("ArmRetractingStroke",true);arm.readFromNBT(saved);arm.captureProgress(0);arm.operator(actor);require(arm.payload().restore(arm,0),"native retracted payload baseline");
                NBTTagCompound settings=arm.settings();settings.setBoolean("PushBlocks",true);arm.configure(settings);arm.target(true);require(arm.preparePayloadMotion(),"retained moving Surface baseline");arm.captureProgress(.37);
            }
            world.setBlockState(SOURCE,Blocks.IRON_BLOCK.getDefaultState(),2);arm.markDirty();checkpoint=new TransferCheckpoint(world,Arrays.asList(world.getChunkFromBlockCoords(ROOT),world.getChunkFromBlockCoords(TARGET)));stage=1;return false;
        }
        if(stage==1){if(!checkpoint.step())return false;checkpoint=null;ArmTransactions.testFault=point->{
            if(!point.equals(phase.contains("prepared")?"prepared":"push"))return;
            try{
                if(phase.endsWith("partial"))try(TransferCheckpoint partial=new TransferCheckpoint(world,Collections.singleton(world.getChunkFromBlockCoords(TARGET)))){long until=System.nanoTime()+15000000000L;while(!partial.step()){if(System.nanoTime()>until)throw new IllegalStateException("Push partial-save timeout");Thread.sleep(5);}}
                java.io.File marker=new java.io.File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(java.io.FileOutputStream output=new java.io.FileOutputStream(marker)){output.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.getFD().sync();}
                new java.util.concurrent.CountDownLatch(1).await();
            }catch(Exception failure){throw new IllegalStateException(failure);}
        };stage=2;}
        GearPushing.prepare((GearTileEntity)world.getTileEntity(ROOT),.5);return false;
    }
}
