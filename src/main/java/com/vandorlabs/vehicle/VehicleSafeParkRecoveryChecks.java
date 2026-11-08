package com.vandorlabs.vehicle;

import java.io.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/** Real async Safe Park, newer destination chunks, process kill and ordinary cold recovery. */
public final class VehicleSafeParkRecoveryChecks {
    private static final BlockPos BASE=new BlockPos(200,101,200);
    private static int stage,ticks,checks;private static EntityGroundVehicle original;private static TransferCheckpoint baseline;
    private static File fixture(WorldServer world){return new File(world.getSaveHandler().getWorldDirectory(),"safe-park-fixture.nbt");}
    private static File journal(WorldServer world){return new File(world.getSaveHandler().getWorldDirectory(),"data/vandorlabs-vehicle-"+world.provider.getDimension()+".nbt");}
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("Safe Park cold recovery: "+why+" stage="+stage+" ticks="+ticks);}
    private static NBTTagCompound read(File file)throws IOException{try(InputStream in=new FileInputStream(file)){return CompressedStreamTools.readCompressed(in);}}
    private static void write(File file,NBTTagCompound tag)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(tag,bytes);try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes.toByteArray());out.getFD().sync();}}
    public static boolean run(WorldServer world,EntityPlayerMP player,String phase)throws Exception {
        if(phase.contains("safe-park-arm-"))return VehicleSafeParkArmRecoveryChecks.run(world,player,phase);
        need(++ticks<1500,"bounded restart fixture");
        if(phase.startsWith("verify-"))return verify(world);
        if(stage==0){
            original=VehicleSafeParkFixture.create(world,player,BASE);
            // Only a flat patch in a different chunk can admit the destination.
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(-20,-1,-20),BASE.add(25,-1,20)))world.setBlockToAir(p);
            for(BlockPos p:BlockPos.getAllInBox(BASE.add(14,-1,-2),BASE.add(25,-1,4)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
            Vec3d control=original.toWorld(new Vec3d(original.structure.controller().pos).addVector(.5,.5,.5));player.setPositionAndUpdate(control.x,control.y,control.z-3);
            NBTTagCompound data=new NBTTagCompound();data.setTag("Expected",original.writeToNBT(new NBTTagCompound()));write(fixture(world),data);
            Set<Chunk> chunks=new LinkedHashSet<>();for(int x=(BASE.getX()-20)>>4;x<=(BASE.getX()+25)>>4;x++)for(int z=(BASE.getZ()-20)>>4;z<=(BASE.getZ()+20)>>4;z++)chunks.add(world.getChunkFromBlockCoords(new BlockPos(x<<4,BASE.getY(),z<<4)));chunks.add(world.getChunkFromBlockCoords(original.getPosition()));
            baseline=new TransferCheckpoint(world,chunks);stage=1;return false;
        }
        if(stage==1){
            if(!baseline.step())return false;baseline.close();baseline=null;
            VehicleTransfer.testFault=point->{if(!point.equals("painting-parked"))return;try{barrier(world,phase);}catch(Exception e){throw new IllegalStateException(e);}};
            VehicleSafePark.submit(player,original,original.structure.controller().pos);stage=2;
        }
        return false;
    }
    private static void barrier(WorldServer world,String phase)throws Exception {
        NBTTagCompound record=read(journal(world));need(record.getBoolean("HoverMode")&&record.getDouble("HoverTarget")==original.hoverTarget,"durable record retains original flight state");
        BlockPos at=BlockPos.fromLong(record.getLong("Origin"));VehicleStructure parked=VehicleStructure.read(record.getCompoundTag("ParkedCraft"));Set<Chunk> targets=new LinkedHashSet<>();
        for(VehicleStructure.Cell cell:parked.cells){BlockPos target=at.add(cell.pos);need((target.getX()>>4)!=(original.getPosition().getX()>>4),"all newer target cells are outside the old vehicle chunk");targets.add(world.getChunkFromBlockCoords(target));}
        NBTTagCompound metadata=read(fixture(world));metadata.setTag("ParkedCraft",parked.write());metadata.setLong("Destination",at.toLong());write(fixture(world),metadata);
        try(TransferCheckpoint partial=new TransferCheckpoint(world,targets)){long deadline=System.nanoTime()+45_000_000_000L;while(!partial.step()){if(System.nanoTime()>deadline)throw new IllegalStateException("Safe Park partial checkpoint timeout");Thread.sleep(50);}}
        System.out.println("[vandorlabs] arm-durable-crash "+phase+" actual Safe Park journal durable; newer destination chunks, old airborne owner chunk");System.out.flush();
        File marker=new File(world.getSaveHandler().getWorldDirectory(),"arm-crash-ready.txt");try(FileOutputStream out=new FileOutputStream(marker)){out.write(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0].getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.getFD().sync();}
        new java.util.concurrent.CountDownLatch(1).await();
    }
    private static boolean verify(WorldServer world)throws Exception {
        NBTTagCompound data=read(fixture(world)),expected=data.getCompoundTag("Expected");UUID id=expected.getUniqueId("UUID");NBTTagList position=expected.getTagList("Pos",6);
        world.getChunkFromBlockCoords(new BlockPos(position.getDoubleAt(0),position.getDoubleAt(1),position.getDoubleAt(2)));
        // VehicleService performs ordinary recovery; the fixture does not call recover itself.
        if(VehicleTransfer.pending(world))return false;
        Entity raw=world.getEntityFromUuid(id);if(raw==null)return false;need(raw instanceof EntityGroundVehicle,"recovered owner type");EntityGroundVehicle craft=(EntityGroundVehicle)raw;
        need(!craft.isDead&&!craft.ownershipBlocked&&!craft.transferInProgress,"exactly usable recovered owner");need(craft.hover&&craft.hoverTarget==expected.getDouble("HoverTarget"),"cold recovery retains Hover mode and target height");need(craft.rotationYaw==expected.getTagList("Rotation",5).getFloatAt(0),"original unsnapped heading retained");
        need(craft.structure!=null&&craft.structure.write().equals(expected.getCompoundTag("Craft")),"original complete source snapshot retained");
        need(craft.posX==position.getDoubleAt(0)&&Math.abs(craft.posY-position.getDoubleAt(1))<1e-6&&craft.posZ==position.getDoubleAt(2),"original airborne position retained");
        VehicleStructure parked=VehicleStructure.read(data.getCompoundTag("ParkedCraft"));BlockPos at=BlockPos.fromLong(data.getLong("Destination"));for(VehicleStructure.Cell cell:parked.cells){BlockPos p=at.add(cell.pos);world.getChunkFromBlockCoords(p);need(world.isAirBlock(p)&&world.getTileEntity(p)==null,"newer native destination owner removed: "+p);}
        int owners=0;for(Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(id))owners++;need(owners==1,"one live vehicle owner after old entity chunk loads");
        int cargo=0;for(net.minecraft.tileentity.TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityChest){TileEntityChest chest=(TileEntityChest)tile;need(chest.getStackInSlot(7).getItem()==Items.DIAMOND&&chest.getStackInSlot(7).getCount()==11,"eleven diamonds retained once");cargo++;}need(cargo==1,"one retained cargo inventory");
        need(world.getEntitiesWithinAABB(EntityItem.class,new AxisAlignedBB(BASE).grow(32),item->item.getItem().getItem()==Items.DIAMOND||item.getItem().getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.CHEST)).isEmpty(),"no duplicate cargo drops");
        System.out.println("[vandorlabs] arm-durable-recovery PASS safe-park-partial checks="+checks+" abrupt-kill newer-destination/old-owner-chunks Hover-target unsnapped-heading exact-source one-cargo-owner");return true;
    }
    private VehicleSafeParkRecoveryChecks(){}
}
