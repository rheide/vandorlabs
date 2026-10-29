package com.vandorlabs.vehicle;

import com.vandorlabs.copier.CopyWorld;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/** Actual process death after only one fragment chunk reaches region storage. */
public final class VehicleCouplingRecoveryChecks {
    private static final BlockPos BASE=new BlockPos(1019,150,340);
    private static long recoveryDeadline;
    private static java.io.File file(WorldServer world){return new java.io.File(world.getSaveHandler().getWorldDirectory(),"coupling-fixture.dat");}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Coupling cold restart: "+why);}
    private static void checkpoint(WorldServer world,Collection<Chunk> chunks)throws Exception{
        try(TransferCheckpoint checkpoint=new TransferCheckpoint(world,chunks)){long deadline=System.nanoTime()+45000000000L;while(!checkpoint.step()){require(System.nanoTime()<deadline,"partial chunk checkpoint timed out");Thread.sleep(25);}}
    }
    public static boolean run(WorldServer world,EntityPlayerMP actor,String phase)throws Exception{
        if(phase.startsWith("verify-"))return verify(world,phase);
        if(phase.contains("latch"))return latchCrash(world,actor,phase);
        // Do not close this fixture: the harness kills the JVM at the barrier.
        VehicleCouplingRuntimeChecks.Fixture fixture=new VehicleCouplingRuntimeChecks.Fixture(world,actor,BASE);
        boolean detach=phase.contains("detach");if(detach)fixture.submit(fixture.attachPlan());
        VehicleCouplingPlan plan=detach?fixture.detachPlan():fixture.attachPlan();
        require((fixture.chest.getX()>>4)!=(fixture.moduleHead.getX()>>4),"fragment genuinely crosses a chunk boundary");
        NBTTagCompound saved=new NBTTagCompound();saved.setUniqueId("UUID",fixture.craft.getUniqueID());saved.setTag("Before",plan.before.write());saved.setTag("Fragment",plan.fragment.write());saved.setLong("Origin",plan.fragmentOrigin.toLong());saved.setBoolean("Detach",detach);
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(file(world))){java.io.DataOutputStream stream=new java.io.DataOutputStream(output);CompressedStreamTools.write(saved,stream);stream.flush();output.getFD().sync();}
        Set<Chunk> chunks=new LinkedHashSet<>();chunks.add(world.getChunkFromBlockCoords(BASE));for(VehicleStructure.Cell cell:plan.fragment.cells)chunks.add(world.getChunkFromBlockCoords(plan.fragmentOrigin.add(cell.pos)));checkpoint(world,chunks);
        boolean rollback=phase.contains("rollback");
        VehicleCouplingTransfer.fault=point->{
            if(rollback&&point.equals("install")){try{checkpoint(world,chunks);}catch(Exception error){throw new IllegalStateException(error);}throw new IllegalStateException("begin durable rollback crash fixture");}
            if(!point.equals(rollback?"rollback":"install"))return;try{
            checkpoint(world,Collections.singleton(world.getChunkFromBlockCoords(fixture.chest)));
            System.out.println("[vandorlabs] coupling-durable-crash "+phase+" installed journal durable; cargo chunk saved, coupler/entity chunk retains baseline");System.out.flush();
            java.io.File marker=new java.io.File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(java.io.FileOutputStream output=new java.io.FileOutputStream(marker)){output.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.getFD().sync();}
            new java.util.concurrent.CountDownLatch(1).await();
        }catch(Exception failure){throw new IllegalStateException(failure);}};
        VehicleCouplingTransfer.submit(actor,plan,()->{},failure->{throw new IllegalStateException(failure);});VehicleCouplingTransfer.drainForChecks(world);throw new IllegalStateException("Coupling crash barrier was not reached");
    }
    private static boolean latchCrash(WorldServer world,EntityPlayerMP actor,String phase)throws Exception{
        VehicleCouplingRuntimeChecks.Fixture f=new VehicleCouplingRuntimeChecks.Fixture(world,actor,BASE);
        BlockPos leverPos=f.chest.up(),screenPos=BASE.up(7);int channel=64883;
        net.minecraft.block.Block lever=net.minecraft.block.Block.getBlockFromName("vandorlabs:industrial_power_lever");
        world.setBlockState(leverPos.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);world.setBlockState(leverPos,lever.getDefaultState(),2);com.vandorlabs.tiles.DeferredTileLoad.flush(world);
        com.vandorlabs.tiles.TileEntityRedstoneChannel source=(com.vandorlabs.tiles.TileEntityRedstoneChannel)world.getTileEntity(leverPos);
        com.vandorlabs.redstone.RedstoneFixtureTransmitters.equip(source,com.vandorlabs.redstone.ChannelList.of(channel));source.setLocalOn(true);f.submit(f.attachPlan());
        world.setBlockState(screenPos,com.vandorlabs.blocks.ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState(),2);com.vandorlabs.tiles.DeferredTileLoad.flush(world);
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector screen=(com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)world.getTileEntity(screenPos);
        com.vandorlabs.tiles.RedstoneScreenContents rows=screen.redstoneScreen(0);require(rows.configureRows(Collections.singletonList("Held toggle"),Collections.singletonList(com.vandorlabs.redstone.ChannelList.of(channel)),0),"cold latch screen configures");com.vandorlabs.redstone.RedstoneFixtureTransmitters.equip(screen,com.vandorlabs.redstone.ChannelList.of(channel));
        VehicleCouplingPlan plan=f.detachPlan();
        NBTTagCompound saved=new NBTTagCompound();saved.setUniqueId("UUID",f.craft.getUniqueID());saved.setTag("Before",plan.after.write());saved.setTag("Fragment",plan.fragment.write());saved.setLong("Origin",plan.fragmentOrigin.toLong());saved.setLong("Lever",leverPos.toLong());
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(file(world))){java.io.DataOutputStream stream=new java.io.DataOutputStream(output);CompressedStreamTools.write(saved,stream);stream.flush();output.getFD().sync();}
        final boolean[] retired={false};VehicleCouplingTransfer.fault=point->{if(point.equals("retire")){retired[0]=true;throw new IllegalStateException("hold committed latch fixture");}};
        VehicleCouplingTransfer.submit(actor,plan,()->{},error->{});VehicleCouplingTransfer.drainForChecks(world);require(retired[0],"committed detach reaches retirement failure");VehicleCouplingTransfer.fault=null;
        require(rows.rows().get(0).latchedLevel(channel)==15,"cold latch begins ON");rows.toggleRow(0);
        VehicleCouplingTransfer.fault=point->{if(!point.equals("latches"))return;try{
            // The committed chunk checkpoint still contains ON; only the WAL
            // contains OFF. Kill before any new affected-chunk checkpoint.
            java.io.File marker=new java.io.File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(java.io.FileOutputStream output=new java.io.FileOutputStream(marker)){output.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.getFD().sync();}
            System.out.println("[vandorlabs] coupling-durable-crash "+phase+" committed OFF latch WAL; native region retains ON");System.out.flush();new java.util.concurrent.CountDownLatch(1).await();
        }catch(Exception error){throw new IllegalStateException(error);}};
        VehicleCouplingTransfer.recover(world);throw new IllegalStateException("Committed latch crash barrier was not reached");
    }
    private static boolean verify(WorldServer world,String phase)throws Exception{
        NBTTagCompound saved;try(java.io.DataInputStream input=new java.io.DataInputStream(new java.io.FileInputStream(file(world)))){saved=CompressedStreamTools.read(input);}
        NBTTagCompound fragmentTag=saved.getCompoundTag("Fragment");
        // Attachment scans retain their complete structure kind; detached pieces
        // use display snapshots. Both decoders retain their strict kind checks.
        VehicleStructure fragment=fragmentTag.getBoolean("DisplayOnly")?VehicleStructure.readDisplay(fragmentTag):VehicleStructure.read(fragmentTag);BlockPos origin=BlockPos.fromLong(saved.getLong("Origin"));world.getChunkFromBlockCoords(BASE);for(VehicleStructure.Cell cell:fragment.cells)world.getChunkFromBlockCoords(origin.add(cell.pos));
        if(recoveryDeadline==0)recoveryDeadline=System.nanoTime()+30000000000L;
        VehicleCouplingTransfer.beforeTick(world);VehicleCouplingTransfer.recover(world);if(VehicleCouplingTransfer.pendingCount(world)>0){require(System.nanoTime()<recoveryDeadline,"pending journal did not recover within 30 seconds for "+phase);return false;}
        EntityGroundVehicle craft=null;int owners=0;for(Entity entity:world.loadedEntityList)if(!entity.isDead&&entity instanceof EntityGroundVehicle&&entity.getUniqueID().equals(saved.getUniqueId("UUID"))){craft=(EntityGroundVehicle)entity;owners++;}
        require(owners==1&&craft!=null&&!craft.ownershipBlocked&&!craft.transferInProgress,"exactly one usable vehicle owner");require(craft.structure.write().equals(saved.getCompoundTag("Before")),"uncommitted install restores exact original hull");
        int worldCargo=0,carriedCargo=0;for(VehicleStructure.Cell cell:fragment.cells){BlockPos at=origin.add(cell.pos);if(world.getTileEntity(at) instanceof TileEntityChest){TileEntityChest chest=(TileEntityChest)world.getTileEntity(at);require(chest.getStackInSlot(0).getCount()==7&&chest.getName().equals("Coupled cargo"),"native cargo exact");worldCargo++;}}
        for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityChest){TileEntityChest chest=(TileEntityChest)tile;require(chest.getStackInSlot(0).getCount()==7&&chest.getName().equals("Coupled cargo"),"carried cargo exact");carriedCargo++;}
        require(worldCargo+carriedCargo==1&&worldCargo==(saved.getBoolean("Detach")?0:1),"exactly one seven-diamond cargo owner");
        if(phase.contains("latch")){com.vandorlabs.tiles.TileEntityRedstoneChannel lever=(com.vandorlabs.tiles.TileEntityRedstoneChannel)world.getTileEntity(BlockPos.fromLong(saved.getLong("Lever")));require(lever!=null&&!lever.isLocalOn()&&lever.latchedLevel(64883)==0,"committed latch OFF survives old native chunk checkpoint");}
        else require(saved.getBoolean("Detach")?CopyWorld.empty(world,fragment,origin):fragment.matches(world,origin),"all fragment cells roll back together");
        System.out.println("[vandorlabs] arm-durable-recovery PASS "+phase+" coupling partial-crosschunk-save cold-restart exact-hull exactly-one-cargo-owner");return true;
    }
}
