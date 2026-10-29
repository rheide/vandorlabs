package com.vandorlabs.gear;

import com.vandorlabs.vehicle.TransferCheckpoint;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.*;
import java.util.*;

/** Disposable process-halting regression. Enabled only by the explicit repro-lab phase. */
public final class ArmRecoveryRuntimeChecks {
    private static final BlockPos ROOT=new BlockPos(15,100,0),PAYLOAD=ROOT.east(2);
    private static int stage,ticks;private static TransferCheckpoint checkpoint;
    public static boolean paused(GearTileEntity arm){return System.getProperty("vandorlabs.reprolab")!=null&&!System.getProperty("vandorlabs.armRecoveryPhase","").isEmpty()&&arm.getPos().equals(ROOT);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Arm recovery: "+why);}
    @SideOnly(Side.CLIENT) public static boolean tick(Minecraft mc)throws Exception{
        if(++ticks<30)return false;require(ticks<1500,"bounded durable checkpoint/restart");String phase=System.getProperty("vandorlabs.armRecoveryPhase","");
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0),phase)).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP actor,String phase)throws Exception{
        if(phase.contains("push-"))return GearPushRecoveryChecks.run(world,actor,phase);
        if(phase.startsWith("authorization-"))return ArmAuthorizationRestartChecks.run(world,actor,phase.equals("authorization-verify"));
        world.getChunkFromBlockCoords(ROOT);world.getChunkFromBlockCoords(PAYLOAD);
        if(phase.startsWith("verify-")){
            if(ArmTransactions.locked(world,ROOT))return false;GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm!=null&&(arm.payload()!=null||phase.contains("harvest-committed")),"original arm and snapshot survive cold restart");boolean restoring=phase.contains("restore");
            if(phase.contains("harvest")){
                boolean committed=phase.contains("committed");int diamonds=0,chests=0;for(net.minecraft.entity.item.EntityItem item:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(ROOT).grow(6))){if(item.getItem().getItem()==Items.DIAMOND)diamonds+=item.getItem().getCount();if(item.getItem().getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.CHEST))chests+=item.getItem().getCount();}
                require(world.isAirBlock(PAYLOAD),"harvest has no restored world duplicate");if(committed)require(arm.payload()==null&&diamonds==37&&chests==1,"committed harvest owns drops once");else{require(arm.payload()!=null&&!arm.payload().materialized()&&diamonds==0&&chests==0,"uncommitted harvest rolls back all drops");require(((IInventory)arm.payload().view(arm).getTileEntity(BlockPos.ORIGIN)).getStackInSlot(0).getCount()==37,"retained chest contents");}
                System.out.println("[vandorlabs] arm-durable-recovery PASS "+phase+" mined chest payload or drops exactly one owner 37 diamonds");return true;
            }
            if(restoring){if(phase.contains("endpoint"))require(!arm.hasStoredSections()&&world.getBlockState(ROOT.east()).getBlock()==GearRegistry.GEAR_EXTENDER,"endpoint rollback restores the displaced arm section exactly once");require(world.isAirBlock(PAYLOAD)&&!arm.payload().materialized(),"partial target save is rolled back before publication");TileEntity retained=arm.payload().view(arm).getTileEntity(BlockPos.ORIGIN);require(retained instanceof IInventory&&((IInventory)retained).getStackInSlot(0).getCount()==37,"captured inventory retained exactly once");}
            else{TileEntity tile=world.getTileEntity(phase.contains("endpoint")?ROOT.east():PAYLOAD);require(tile instanceof IInventory&&((IInventory)tile).getStackInSlot(0).getItem()==Items.DIAMOND&&((IInventory)tile).getStackInSlot(0).getCount()==37,"removed source inventory recovered exactly once");require(arm.payload().materialized()&&arm.payload().matchesPlaced(arm),"snapshot records world ownership, not a second usable inventory");}
            System.out.println("[vandorlabs] arm-durable-recovery PASS "+phase+" real process halt cold restart cross-chunk ownership 37 diamonds exact verified disk checkpoint");return true;
        }
        if(stage==0){
            for(BlockPos pos:BlockPos.getAllInBox(ROOT.add(-1,-1,-1),ROOT.add(4,2,2))){world.removeTileEntity(pos);world.setBlockToAir(pos);}
            for(int n=0;n<2;n++){BlockPos pos=ROOT.east(n);world.setBlockState(pos,GearRegistry.GEAR_EXTENDER.getDefaultState(),2);GearTileEntity arm=(GearTileEntity)world.getTileEntity(pos);arm.setMountDirection(EnumFacing.EAST);arm.operator(actor);}
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);require(arm.rebuild(),"two-section cross-chunk fixture");world.setBlockState(PAYLOAD,Blocks.CHEST.getDefaultState(),2);((IInventory)world.getTileEntity(PAYLOAD)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,37));
            if(phase.startsWith("restore")||phase.startsWith("harvest")){try(AutoCloseable matrix=ArmTransactions.matrix()){require(arm.preparePayloadMotion(),"prepare captured baseline");}arm.captureProgress(phase.startsWith("harvest")?0:.5);arm.target(true);if(phase.startsWith("harvest")){actor.setGameType(net.minecraft.world.GameType.SURVIVAL);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Items.DIAMOND_AXE));net.minecraft.util.math.Vec3d center=arm.payload().transform(arm,0,.5,.5,.5).add(new net.minecraft.util.math.Vec3d(ROOT));actor.connection.setPlayerLocation(center.x,center.y-actor.getEyeHeight(),center.z+3,180,0);}}
            else{world.disableLevelSaving=true;require(!arm.preparePayloadMotion()&&!world.isAirBlock(PAYLOAD),"disabled saving refuses source mutation");world.disableLevelSaving=false;}
            if(phase.contains("endpoint")){
                if(!phase.startsWith("restore"))try(AutoCloseable matrix=ArmTransactions.matrix()){require(arm.preparePayloadMotion(),"capture endpoint baseline");}
                NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());saved.setBoolean("ArmRetractingStroke",true);arm.readFromNBT(saved);arm.captureProgress(0);arm.operator(actor);
                if(!phase.startsWith("restore"))try(AutoCloseable matrix=ArmTransactions.matrix()){require(arm.payload().restore(arm,0)&&arm.hasStoredSections(),"native endpoint baseline stores displaced arm");}
            }
            arm.markDirty();checkpoint=new TransferCheckpoint(world,Arrays.asList(world.getChunkFromBlockCoords(ROOT),world.getChunkFromBlockCoords(PAYLOAD)));stage=1;return false;
        }
        if(stage==1){if(!checkpoint.step())return false;checkpoint=null;ArmTransactions.testFault=point->{
                String requested=phase.startsWith("harvest")?(phase.endsWith("partial")?"harvest":phase):phase.startsWith("prepared")?"prepared":phase.startsWith("restore")?"restore":"detach";if(!point.equals(requested))return;
                try{if(phase.endsWith("partial")){try(TransferCheckpoint partial=new TransferCheckpoint(world,Collections.singleton(world.getChunkFromBlockCoords(PAYLOAD)))){long until=System.nanoTime()+15000000000L;while(!partial.step()){if(System.nanoTime()>until)throw new IllegalStateException("Partial-save fixture timeout");Thread.sleep(5);}}}
                    System.out.println("[vandorlabs] arm-durable-crash "+phase+" journal durable; stopping without world shutdown/save");System.out.flush();java.io.File marker=new java.io.File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(java.io.FileOutputStream output=new java.io.FileOutputStream(marker)){output.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.getFD().sync();}new java.util.concurrent.CountDownLatch(1).await();
                }catch(Exception failure){throw new IllegalStateException(failure);}
            };stage=2;}
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
        if(phase.startsWith("harvest"))ArmPayloadHarvest.dig(actor,ROOT,BlockPos.ORIGIN,false);else if(phase.startsWith("restore"))arm.payload().restore(arm,phase.contains("endpoint")?0:1);else arm.preparePayloadMotion();return false;
    }
    private ArmRecoveryRuntimeChecks(){}
}
