package com.vandorlabs.client;

import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.vehicle.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Actual two-arm native construction, read-only peer preparation; no motion or capture installation. */
public final class VehicleArmCouplerPeersLiveChecks {
    private static int stage,frames,checks;private static VehicleScan scan;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Arm peer fixture: "+why+" stage="+stage+" frames="+frames);}
    public static boolean tick(Minecraft mc,File output)throws Exception{
        mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;need(++frames<1200,"bounded actual source assembly");WorldServer world=mc.getIntegratedServer().getWorld(0);
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->{try{java.lang.reflect.Method setup=VehicleArmCouplerMultiLiveChecks.class.getDeclaredMethod("setup",WorldServer.class,EntityPlayerMP.class);setup.setAccessible(true);setup.invoke(null,world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));java.lang.reflect.Field baseField=VehicleArmCouplerMultiLiveChecks.class.getDeclaredField("BASE"),seatField=VehicleArmCouplerMultiLiveChecks.class.getDeclaredField("SEAT");baseField.setAccessible(true);seatField.setAccessible(true);BlockPos base=(BlockPos)baseField.get(null);scan=new VehicleScan(world,base.add((BlockPos)seatField.get(null)));EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);for(int x:new int[]{0,6}){BlockPos controller=base.add(x,2,0);com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER.onBlockPlacedBy(world,controller,world.getBlockState(controller),actor,new net.minecraft.item.ItemStack(com.vandorlabs.blocks.ModBlocks.VEHICLE_CONTROLLER));}scan.holdSource();scan.measureWeight();}catch(Exception invalid){throw new IllegalStateException(invalid);}}).get();stage=1;frames=0;return false;}
        if(stage==1){if(frames<20)return false;if(!mc.getIntegratedServer().callFromMainThread(()->scan.step(256)).get())return false;mc.getIntegratedServer().addScheduledTask(()->{try{
            EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);need(scan.result!=null,"actual native scan: "+scan.error);EntityGroundVehicle craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);need(craft.couplerAssociationGeneration()!=null&&craft.couplerScopeReady(),"genuine NativeReceipt assembly owner before any view/capture");VehicleStructure source=craft.structure;UUID scope=craft.couplerAssociationGeneration();VehicleArmCouplerPairs.Snapshot pairs=VehicleArmCouplerPairs.discoverForParking(VehicleArmCouplers.observe(craft));need(pairs.pairs.size()==2,"actual native parallel pairs");
            VehicleArmCouplerPeers.Prepared prepared=VehicleArmCouplerPeers.prepare(craft,pairs);need(prepared.peers().size()==2&&prepared.recheck(),"two native passive capture recipes, one per independent root");need(prepared.retainedBytes()>0&&prepared.retainedBytes()<=VehicleArmCouplerPeers.MAX_RETAINED_BYTES&&prepared.examinedRows()<=VehicleArmCouplerPeers.MAX_ROWS,"explicit aggregate budgets");Set<BlockPos> nativeHeads=new HashSet<>();
            for(VehicleArmCouplerPeers.Peer peer:prepared.peers()){GearTileEntity root=(GearTileEntity)craft.view.tiles.get(peer.root());need(root!=null&&root.payload()==null&&root.progress()==1&&root.target(),"live native arm unchanged");com.vandorlabs.gear.ArmPayload payload=peer.payload();need(payload.materialized()&&payload.blocks.cells.size()==1&&peer.nativeHeads().size()==1,"actual extender tip-only recipe preserves articulation cut");for(BlockPos head:peer.nativeHeads())need(nativeHeads.add(head),"distinct native physical owner for both recipes");NBTTagCompound edited=payload.write();edited.setString("FixturePrivateEdit","not retained");com.vandorlabs.gear.ArmPayload copy=peer.payload();need(copy!=payload&&!copy.write().hasKey("FixturePrivateEdit")&&copy.write().equals(payload.write()),"defensive metadata-only payload getter");}
            need(craft.structure==source&&scope.equals(craft.couplerAssociationGeneration()),"no Source replacement or scope mint");
            GearTileEntity changed=(GearTileEntity)craft.view.tiles.get(prepared.peers().get(1).root());ChannelList old=changed.getRedstoneChannels();int revision=changed.motionRevision();changed.setRedstoneChannels(ChannelList.of(6154999));need(changed.motionRevision()==revision&&craft.structure==source,"actual carried setting can change without revision or Source update");need(!prepared.recheck(),"primitive channel change invalidates prepared recipes");changed.setRedstoneChannels(old);VehicleArmCouplerPeers.Prepared fresh=VehicleArmCouplerPeers.prepare(craft,pairs);need(fresh.recheck()&&fresh.peers().size()==2,"complete batch reprepare after real setting restore");
            boolean refused=false;try{CompletableFuture.supplyAsync(fresh::recheck).get();}catch(ExecutionException expected){refused=expected.getCause() instanceof IllegalStateException;}need(refused,"worker recheck refused before world access");need(craft.structure==source&&scope.equals(craft.couplerAssociationGeneration()),"all refusal/getter checks retain exact installed Source/scope");
            System.out.println("[vandorlabs][reprolab] coupler-peers-live PASS checks="+checks+" peers=2 rows="+fresh.examinedRows()+" retainedBytes="+fresh.retainedBytes()+" actualNativePlacement/receipt/readOnlyDiscovery/tipOnly/defensiveDecode/configFreshness/noRevisionShortcut/workerRefusal noMotionClaim");
        }catch(Exception failure){throw new IllegalStateException(failure);}finally{scan.releaseSource();}}).get();stage=2;return true;}
        return true;
    }
    private VehicleArmCouplerPeersLiveChecks(){}
}
