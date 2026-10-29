package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Process termination barriers reuse the vehicle's real journal and chunk checkpoints. */
public final class VehiclePaintingRecoveryChecks {
    private static java.io.File file(WorldServer world){return new java.io.File(world.getSaveHandler().getWorldDirectory(),"painting-fixture.dat");}
    static void prepare(WorldServer world,VehicleStructure structure,BlockPos origin,EntityPainting painting,String phase)throws Exception{
        NBTTagCompound data=new NBTTagCompound();data.setTag("Painting",painting.writeToNBT(new NBTTagCompound()));data.setLong("Controller",origin.add(structure.controller().pos).toLong());
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(file(world))){java.io.DataOutputStream stream=new java.io.DataOutputStream(output);CompressedStreamTools.write(data,stream);stream.flush();output.getFD().sync();}
        Set<net.minecraft.world.chunk.Chunk> chunks=new LinkedHashSet<>();for(VehicleStructure.Cell cell:structure.cells)chunks.add(world.getChunkFromBlockCoords(origin.add(cell.pos)));chunks.add(world.getChunkFromBlockCoords(painting.getPosition()));
        try(TransferCheckpoint checkpoint=new TransferCheckpoint(world,chunks)){long until=System.nanoTime()+45000000000L;while(!checkpoint.step()){if(System.nanoTime()>until)throw new IllegalStateException("Painting baseline checkpoint timeout");Thread.sleep(50);}}
        if(phase.contains("assemble"))barrier(world,phase,"painting-detached");else if(phase.contains("before-journal"))barrier(world,phase,"painting-before-journal");
    }
    static void parking(WorldServer world,String phase){barrier(world,phase,"painting-parked");}
    private static void barrier(WorldServer world,String phase,String requested){VehicleTransfer.testFault=point->{if(!requested.equals(point))return;try{
        NBTTagCompound fixture;try(java.io.DataInputStream input=new java.io.DataInputStream(new java.io.FileInputStream(file(world)))){fixture=CompressedStreamTools.read(input);}
        NBTTagList position=fixture.getCompoundTag("Painting").getTagList("Pos",6);BlockPos at=new BlockPos(position.getDoubleAt(0),position.getDoubleAt(1),position.getDoubleAt(2));
        try(TransferCheckpoint checkpoint=new TransferCheckpoint(world,Collections.singleton(world.getChunkFromBlockCoords(at)))){long until=System.nanoTime()+45000000000L;while(!checkpoint.step()){if(System.nanoTime()>until)throw new IllegalStateException("Painting partial checkpoint timeout");Thread.sleep(50);}}
        System.out.println("[vandorlabs] painting-durable-crash "+phase+" partial entity chunk saved before the vehicle ownership checkpoint");
        java.io.File marker=new java.io.File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(java.io.FileOutputStream output=new java.io.FileOutputStream(marker)){output.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.getFD().sync();}
        new java.util.concurrent.CountDownLatch(1).await();
    }catch(Exception error){throw new IllegalStateException(error);}};}
    public static boolean verify(WorldServer world,String phase)throws Exception{
        if(VehicleTransfer.pending(world))VehicleTransfer.recover(world);
        NBTTagCompound fixture;try(java.io.DataInputStream input=new java.io.DataInputStream(new java.io.FileInputStream(file(world)))){fixture=CompressedStreamTools.read(input);}
        NBTTagCompound original=fixture.getCompoundTag("Painting");UUID id=original.getUniqueId("UUID");NBTTagList position=original.getTagList("Pos",6);BlockPos at=new BlockPos(position.getDoubleAt(0),position.getDoubleAt(1),position.getDoubleAt(2));world.getChunkFromBlockCoords(at);BlockPos controller=BlockPos.fromLong(fixture.getLong("Controller"));world.getChunkFromBlockCoords(controller);
        int nativeOwners=0,carriedOwners=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead){
            if(entity instanceof EntityPainting&&entity.getUniqueID().equals(id)){nativeOwners++;if(entity.getIsInvulnerable()||entity.updateBlocked)throw new IllegalStateException("Temporary painting hold flags survived recovery");}
            if(entity instanceof EntityGroundVehicle&&((EntityGroundVehicle)entity).structure!=null){NBTTagList entries=VehiclePaintings.entries(((EntityGroundVehicle)entity).structure);for(int i=0;i<entries.tagCount();i++)if(entries.getCompoundTagAt(i).getUniqueId("UUID").equals(id))carriedOwners++;}
        }
        boolean parking=phase.contains("park");if(nativeOwners!=(parking?0:1)||carriedOwners!=(parking?1:0))throw new IllegalStateException("Painting recovery owner mismatch native="+nativeOwners+" carried="+carriedOwners);
        if(parking?!world.isAirBlock(controller):world.isAirBlock(controller))throw new IllegalStateException("Painting/block ownership disagrees after recovery");
        for(net.minecraft.entity.item.EntityItem item:world.getEntitiesWithinAABB(net.minecraft.entity.item.EntityItem.class,new net.minecraft.util.math.AxisAlignedBB(controller).grow(32)))if(item.getItem().getItem()==net.minecraft.init.Items.PAINTING)throw new IllegalStateException("Painting recovery published a duplicate item drop");
        System.out.println("[vandorlabs] arm-durable-recovery PASS "+phase+" painting exactly-one-owner native-or-carried partial-entity-chunk cold-restart no-item-duplicates");return true;
    }
}
