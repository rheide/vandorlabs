package com.vandorlabs.turntable;

import com.vandorlabs.vehicle.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import java.io.*;
import java.util.*;

/** Immutable contents of the selected prism; entity NBT is frozen until restored. */
public final class TurntableSnapshot {
    public static final int MAX_BYTES=8*1024*1024,MAX_ENTITIES=256;
    public final TurntableSurfaceLayout layout;
    public final BlockPos origin;
    public final VehicleStructure blocks;
    public final List<FrozenEntity> entities;
    public final int height;
    public static final class FrozenEntity {
        public final UUID id,parent;
        private final NBTTagCompound data;
        FrozenEntity(UUID id,UUID parent,NBTTagCompound data){this.id=id;this.parent=parent;this.data=data.copy();}
        public NBTTagCompound data(){return data.copy();}
    }
    private TurntableSnapshot(TurntableSurfaceLayout layout,BlockPos origin,VehicleStructure blocks,List<FrozenEntity> entities,int height){
        this.layout=layout;this.origin=origin.toImmutable();this.blocks=blocks;this.entities=Collections.unmodifiableList(new ArrayList<>(entities));this.height=height;
    }
    public static TurntableSnapshot capture(WorldServer world,TurntableSurfaceLayout layout,int height,EntityPlayerMP actor)throws IOException{
        if(height<1 || height>16)throw new IllegalArgumentException("Scan height must be between 1 and 16 blocks");
        BlockPos a=layout.cell(0,0,1),b=layout.cell(layout.size-1,layout.size-1,height);
        BlockPos min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
        BlockPos max=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        if(min.getY()<0 || max.getY()>=world.getHeight() || !world.getWorldBorder().contains(min) || !world.getWorldBorder().contains(max) || !world.isAreaLoaded(min,max,false))
            throw new IllegalArgumentException("The entire scan volume must be inside the loaded world");
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        TurntableBoundaries boundaries=new TurntableBoundaries(world,min,max);
        for(BlockPos mutable:BlockPos.getAllInBox(min,max)){
            BlockPos pos=mutable.toImmutable();if(world.isAirBlock(pos))continue;
            VehicleService.permission(actor,pos);
            if(MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,world.getBlockState(pos),actor)))throw new IllegalArgumentException("A protection rule prevented scanning at "+pos);
            TileEntity tile=world.getTileEntity(pos);
            boundaries.check(pos,world.getBlockState(pos),tile);
            if(tile instanceof com.vandorlabs.tiles.TileEntityTurntableController || tile instanceof com.vandorlabs.tiles.TileEntityTurntableSurface)
                throw new IllegalArgumentException("Keep other turntable assemblies outside the scan volume");
            NBTTagCompound data=tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),new BlockPos(-min.getX(),-min.getY(),-min.getZ()));
            cells.add(new VehicleStructure.Cell(pos.subtract(min),world.getBlockState(pos),data));
        }
        AxisAlignedBB volume=new AxisAlignedBB(min,max.add(1,1,1));
        List<Entity> selected=new ArrayList<>();Set<UUID> ids=new HashSet<>();
        for(Entity entity:world.getEntitiesWithinAABBExcludingEntity(null,volume)){
            if(entity.isDead || entity instanceof EntityPlayer || entity.posX<volume.minX||entity.posX>=volume.maxX||entity.posY<volume.minY||entity.posY>=volume.maxY||entity.posZ<volume.minZ||entity.posZ>=volume.maxZ)continue;
            if(entity instanceof EntityTurntable)throw new IllegalArgumentException("Restore the other captured turntable before scanning");
            selected.add(entity);ids.add(entity.getUniqueID());
        }
        if(selected.size()>MAX_ENTITIES)throw new IllegalArgumentException("A turntable can capture at most 256 entities");
        selected.sort(Comparator.comparing(Entity::getUniqueID));List<FrozenEntity> frozen=new ArrayList<>();
        for(Entity entity:selected){
            Entity parent=entity.getRidingEntity();if(parent!=null && !ids.contains(parent.getUniqueID()))throw new IllegalArgumentException("Dismount entities that extend outside the scan volume");
            for(Entity passenger:entity.getPassengers())if(!ids.contains(passenger.getUniqueID()))throw new IllegalArgumentException("Dismount players and entities outside the scan volume");
            net.minecraft.util.ResourceLocation type=EntityList.getKey(entity);if(type==null)throw new IllegalArgumentException("This entity cannot be saved: "+entity.getName());
            NBTTagCompound tag=entity.writeToNBT(new NBTTagCompound());tag.setString("id",type.toString());
            tag.removeTag("Passengers");tag.removeTag("Riding");frozen.add(new FrozenEntity(entity.getUniqueID(),parent==null?null:parent.getUniqueID(),tag));
        }
        TurntableSnapshot snapshot=new TurntableSnapshot(layout,min,VehicleStructure.display(cells),frozen,height);snapshot.encode();return snapshot;
    }
    public NBTTagCompound write(){
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Schema",1);tag.setTag("Surface",layout.write());tag.setLong("Origin",origin.toLong());tag.setInteger("Height",height);tag.setTag("Blocks",blocks.write());
        NBTTagList list=new NBTTagList();for(FrozenEntity entity:entities){NBTTagCompound saved=new NBTTagCompound();saved.setUniqueId("Entity",entity.id);if(entity.parent!=null)saved.setUniqueId("Parent",entity.parent);saved.setTag("Data",entity.data());list.appendTag(saved);}tag.setTag("Entities",list);return tag;
    }
    public static TurntableSnapshot read(NBTTagCompound tag){
        int height=tag.getInteger("Height");NBTTagList list=tag.getTagList("Entities",10);
        if(tag.getInteger("Schema")!=1 || height<1 || height>16 || list.tagCount()>MAX_ENTITIES)throw new IllegalArgumentException("Invalid turntable snapshot");
        List<FrozenEntity> entities=new ArrayList<>();Set<UUID> ids=new HashSet<>();
        for(int i=0;i<list.tagCount();i++){NBTTagCompound e=list.getCompoundTagAt(i);if(!e.hasUniqueId("Entity") || !e.hasKey("Data",10) || !ids.add(e.getUniqueId("Entity")))throw new IllegalArgumentException("Invalid captured entity");entities.add(new FrozenEntity(e.getUniqueId("Entity"),e.hasUniqueId("Parent")?e.getUniqueId("Parent"):null,e.getCompoundTag("Data")));}
        for(FrozenEntity e:entities)if(e.parent!=null && (!ids.contains(e.parent) || e.parent.equals(e.id)))throw new IllegalArgumentException("Invalid captured mount");
        Map<UUID,UUID> parents=new HashMap<>();for(FrozenEntity e:entities)parents.put(e.id,e.parent);
        for(FrozenEntity e:entities){Set<UUID> seen=new HashSet<>();UUID at=e.id;while(at!=null){if(!seen.add(at))throw new IllegalArgumentException("Cyclic captured mount");at=parents.get(at);}}
        VehicleStructure blocks=VehicleStructure.readDisplay(tag.getCompoundTag("Blocks"));
        if(blocks.cells.size()>4096)throw new IllegalArgumentException("Oversize turntable snapshot");
        return new TurntableSnapshot(TurntableSurfaceLayout.read(tag.getCompoundTag("Surface")),BlockPos.fromLong(tag.getLong("Origin")),blocks,entities,height);
    }
    public byte[] encode()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(new FilterOutputStream(bytes){
            private int size;
            @Override public void write(int value)throws IOException{if(++size>MAX_BYTES)throw new IOException("Turntable contents exceed 8 MiB");out.write(value);}
            @Override public void write(byte[] data,int offset,int length)throws IOException{size+=length;if(size>MAX_BYTES)throw new IOException("Turntable contents exceed 8 MiB");out.write(data,offset,length);}
        });CompressedStreamTools.write(write(),output);output.flush();return bytes.toByteArray();
    }
    public static TurntableSnapshot decode(byte[] bytes)throws IOException{
        if(bytes.length>MAX_BYTES)throw new IOException("Oversize turntable snapshot");
        return read(CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes)),new NBTSizeTracker(MAX_BYTES*4L)));
    }
}
