package com.vandorlabs.gear;

import com.vandorlabs.vehicle.TransferCheckpoint;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** One Core detach across the x15/16 boundary, saved partially after durable source removal. */
final class ArmCoreRecoveryChecks {
    private static final BlockPos ROOT=new BlockPos(2735,110,240);
    private static int stage,ticks;private static TransferCheckpoint checkpoint;
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Core arm recovery stage="+stage+": "+why);}
    static boolean active(String phase){return phase.contains("core-detach-partial");}
    private static File expected(WorldServer world){return new File(world.getSaveHandler().getWorldDirectory(),"core-arm-expected.nbt");}
    static boolean run(WorldServer world,EntityPlayerMP actor,String phase)throws Exception{
        ArmCoreFixture.enableCaps();world.getChunkFromBlockCoords(ROOT);world.getChunkFromBlockCoords(ROOT.east(4));
        if(phase.startsWith("verify")){
            if(ArmTransactions.locked(world,ROOT))return false;NBTTagCompound data;try(InputStream input=new FileInputStream(expected(world))){data=CompressedStreamTools.readCompressed(input);}
            ArmPayload before=ArmPayload.read(data);GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);need(arm!=null&&arm.payload()!=null&&arm.payload().materialized(),"one native attachment owner restored");
            need(before.blocks.matches(world,ROOT.add(before.offset)),"all Core/Part states, links, chip and original opaque capability bytes restored exactly");need(arm.payload().matchesPlaced(arm)&&arm.payload().blocks.cells.size()==27,"restored arm snapshot agrees with every native reservation");ArmCorePayload.validate(arm.payload().blocks);
            need(new ArmCorePayload.Discovery(world).at(ROOT.up(2)).cells.size()==27,"whole Core still formed across chunks");
            System.out.println("[vandorlabs] arm-durable-recovery PASS "+phase+" whole27-cell Core original chip and opaque capabilities native exactly once partial cross-chunk save");return true;
        }
        if(stage==0){ArmCoreFixture.setup(world,actor,0);ArmTransactions.testFault=null;stage=1;ticks=0;return false;}
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
        if(stage==1){if(++ticks<20)return false;ArmPayload before=ArmPayload.discover(arm,actor);need(before!=null&&before.blocks.cells.size()==27,"complete Core baseline");ByteArrayOutputStream encoded=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(before.write(),encoded);try(FileOutputStream output=new FileOutputStream(expected(world))){output.write(encoded.toByteArray());output.getFD().sync();}checkpoint=new TransferCheckpoint(world,Arrays.asList(world.getChunkFromBlockCoords(ROOT),world.getChunkFromBlockCoords(ROOT.east(4))));stage=2;return false;}
        if(stage==2){if(!checkpoint.step())return false;checkpoint.close();checkpoint=null;ArmTransactions.testFault=point->{if(!point.equals("detach"))return;try{try(TransferCheckpoint partial=new TransferCheckpoint(world,Collections.singleton(world.getChunkFromBlockCoords(ROOT.east(4))))){long until=System.nanoTime()+15000000000L;while(!partial.step()){if(System.nanoTime()>until)throw new IllegalStateException("Core partial-save timeout");Thread.sleep(5);}}File marker=new File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(FileOutputStream output=new FileOutputStream(marker)){output.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.getFD().sync();}System.out.println("[vandorlabs] arm-durable-crash "+phase+" real Core journal durable; source chunk partially saved");System.out.flush();new java.util.concurrent.CountDownLatch(1).await();}catch(Exception failure){throw new IllegalStateException(failure);}};stage=3;}
        arm.preparePayloadMotion();return false;
    }
    private ArmCoreRecoveryChecks(){}
}
