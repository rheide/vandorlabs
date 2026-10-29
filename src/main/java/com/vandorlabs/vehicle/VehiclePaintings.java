package com.vandorlabs.vehicle;

import com.vandorlabs.VandorLabs;
import java.util.*;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Native paintings travel in the controller's saved data and the existing ownership journal. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class VehiclePaintings {
    static final String KEY="VandorVehiclePaintings";
    private static final int LIMIT=64;
    private static final Map<EntityPainting,Guard> GUARDS=new IdentityHashMap<>();
    private static final class Guard {
        final boolean invulnerable,blocked;int references;
        Guard(EntityPainting entity){invulnerable=entity.getIsInvulnerable();blocked=entity.updateBlocked;}
    }
    private VehiclePaintings(){}
    static List<EntityPainting> attached(World world,VehicleStructure structure,BlockPos origin){
        List<EntityPainting> result=new ArrayList<>();
        for(EntityPainting painting:world.getEntitiesWithinAABB(EntityPainting.class,structure.bounds.offset(origin).grow(4))){
            if(painting.isDead)continue;
            EnumFacing face=painting.facingDirection,across=face.rotateYCCW();BlockPos base=painting.getHangingPosition().offset(face.getOpposite());
            int width=Math.max(1,painting.getWidthPixels()/16),height=Math.max(1,painting.getHeightPixels()/16),inside=0;
            for(int x=0;x<width;x++)for(int y=0;y<height;y++)if(structure.at(base.offset(across,x+(width-1)/-2).up(y+(height-1)/-2).subtract(origin))!=null)inside++;
            if(inside==0)continue;
            if(inside!=width*height||painting.getClass()!=EntityPainting.class||!painting.onValidSurface())throw new IllegalArgumentException("A painting must be supported entirely by this vehicle");
            if(result.size()==LIMIT)throw new IllegalArgumentException("A vehicle supports up to 64 paintings");
            result.add(painting);
        }
        result.sort(Comparator.comparing(e->e.getUniqueID().toString()));return result;
    }
    static void hold(List<EntityPainting> held,List<EntityPainting> paintings){
        for(EntityPainting painting:paintings)if(!held.contains(painting)){
            Guard guard=GUARDS.computeIfAbsent(painting,Guard::new);guard.references++;held.add(painting);painting.updateBlocked=true;painting.setEntityInvulnerable(true);
        }
    }
    static void release(List<EntityPainting> held){
        for(EntityPainting painting:held){Guard guard=GUARDS.get(painting);if(guard!=null&&--guard.references==0){painting.updateBlocked=guard.blocked;painting.setEntityInvulnerable(guard.invulnerable);GUARDS.remove(painting);}}
        held.clear();
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event){if(GUARDS.containsKey(event.getTarget()))event.setCanceled(true);}
    /** Temporary transfer holds never become saved entity flags, including a pre-journal crash. */
    @SubscribeEvent public static void saving(ChunkDataEvent.Save event){
        if(GUARDS.isEmpty())return;
        NBTTagList entities=event.getData().getCompoundTag("Level").getTagList("Entities",10);
        for(int i=0;i<entities.tagCount();i++){NBTTagCompound data=entities.getCompoundTagAt(i);if(!data.hasUniqueId("UUID"))continue;
            for(Map.Entry<EntityPainting,Guard> entry:GUARDS.entrySet())if(entry.getKey().world==event.getWorld()&&entry.getKey().getUniqueID().equals(data.getUniqueId("UUID"))){data.setBoolean("Invulnerable",entry.getValue().invulnerable);data.setBoolean("UpdateBlocked",entry.getValue().blocked);break;}}
    }
    static VehicleStructure capture(VehicleStructure source,BlockPos origin,List<EntityPainting> paintings){
        if(paintings.isEmpty()&&source.paintings().tagCount()==0)return source;
        NBTTagList list=new NBTTagList();for(EntityPainting painting:paintings){Guard guard=GUARDS.get(painting);NBTTagCompound saved=painting.writeToNBT(new NBTTagCompound());if(guard!=null){saved.setBoolean("Invulnerable",guard.invulnerable);saved.setBoolean("UpdateBlocked",guard.blocked);}translateEntity(saved,BlockPos.ORIGIN.subtract(origin));list.appendTag(saved);}
        List<VehicleStructure.Cell> cells=new ArrayList<>();VehicleStructure.Cell controller=source.controller();
        for(VehicleStructure.Cell cell:source.cells){NBTTagCompound data=cell.tileData();if(data!=null){clear(data);if(cell.pos.equals(controller.pos)&&list.tagCount()>0){NBTTagCompound forge=data.getCompoundTag("ForgeData");forge.setTag(KEY,list);data.setTag("ForgeData",forge);}}
            cells.add(new VehicleStructure.Cell(cell.pos,cell.state,data));}
        return new VehicleStructure(cells,source.seat,source.facing,source.gearCount);
    }
    public static NBTTagList entries(VehicleStructure structure){
        NBTTagList result=structure.paintings();
        if(result.tagCount()>LIMIT)throw new IllegalArgumentException("Too many saved vehicle paintings");
        Set<UUID> ids=new HashSet<>();for(int i=0;i<result.tagCount();i++){NBTTagCompound data=result.getCompoundTagAt(i);
            if(!data.hasUniqueId("UUID")||!ids.add(data.getUniqueId("UUID"))||data.getByte("Facing")<0||data.getByte("Facing")>3||!validMotive(data.getString("Motive")))throw new IllegalArgumentException("Invalid saved vehicle painting");
            NBTTagList position=data.getTagList("Pos",6),angles=data.getTagList("Rotation",5);
            if(position.tagCount()!=3||angles.tagCount()!=2)throw new IllegalArgumentException("Invalid saved painting coordinates");
            for(int n=0;n<3;n++)if(!Double.isFinite(position.getDoubleAt(n))||Math.abs(position.getDoubleAt(n))>VehicleStructure.MAX_AXIS+8)throw new IllegalArgumentException("Invalid saved painting position");
            if(!Float.isFinite(angles.getFloatAt(0))||!Float.isFinite(angles.getFloatAt(1)))throw new IllegalArgumentException("Invalid saved painting angles");
            for(String key:new String[]{"TileX","TileY","TileZ"})if(Math.abs((long)data.getInteger(key))>VehicleStructure.MAX_AXIS+8)throw new IllegalArgumentException("Painting is outside the bounded vehicle");
        }
        return result;
    }
    private static boolean validMotive(String motive){for(EntityPainting.EnumArt art:EntityPainting.EnumArt.values())if(art.title.equals(motive))return true;return false;}
    static void checkpoint(WorldServer world,VehicleStructure structure,BlockPos origin,Set<net.minecraft.world.chunk.Chunk> chunks){NBTTagList list=entries(structure);for(int i=0;i<list.tagCount();i++){NBTTagList pos=list.getCompoundTagAt(i).getTagList("Pos",6);chunks.add(world.getChunkFromBlockCoords(new BlockPos(pos.getDoubleAt(0)+origin.getX(),pos.getDoubleAt(1)+origin.getY(),pos.getDoubleAt(2)+origin.getZ())));}}
    private static boolean has(NBTTagCompound data){return data.hasKey("ForgeData",10)&&data.getCompoundTag("ForgeData").hasKey(KEY,9);}
    private static void clear(NBTTagCompound data){if(!has(data))return;NBTTagCompound forge=data.getCompoundTag("ForgeData");if(!forge.hasKey(KEY,9))return;forge.removeTag(KEY);if(forge.hasNoTags())data.removeTag("ForgeData");}
    public static void translated(NBTTagCompound data,BlockPos delta){if(!has(data))return;NBTTagList list=data.getCompoundTag("ForgeData").getTagList(KEY,10);for(int i=0;i<list.tagCount();i++)translateEntity(list.getCompoundTagAt(i),delta);}
    private static void translateEntity(NBTTagCompound data,BlockPos delta){
        data.setInteger("TileX",data.getInteger("TileX")+delta.getX());data.setInteger("TileY",data.getInteger("TileY")+delta.getY());data.setInteger("TileZ",data.getInteger("TileZ")+delta.getZ());
        NBTTagList old=data.getTagList("Pos",6),pos=new NBTTagList();if(old.tagCount()!=3)throw new IllegalArgumentException("Invalid saved painting position");
        pos.appendTag(new NBTTagDouble(old.getDoubleAt(0)+delta.getX()));pos.appendTag(new NBTTagDouble(old.getDoubleAt(1)+delta.getY()));pos.appendTag(new NBTTagDouble(old.getDoubleAt(2)+delta.getZ()));data.setTag("Pos",pos);
    }
    static void rotate(NBTTagCompound tile,int width,int depth,int turns){
        if(!has(tile))return;
        NBTTagList list=tile.getCompoundTag("ForgeData").getTagList(KEY,10);Rotation rotation=Rotation.values()[turns];
        for(int i=0;i<list.tagCount();i++){NBTTagCompound data=list.getCompoundTagAt(i);BlockPos at=VehicleRotation.cell(new BlockPos(data.getInteger("TileX"),data.getInteger("TileY"),data.getInteger("TileZ")),width,depth,turns);
            data.setInteger("TileX",at.getX());data.setInteger("TileY",at.getY());data.setInteger("TileZ",at.getZ());data.setByte("Facing",(byte)rotation.rotate(EnumFacing.getHorizontal(data.getByte("Facing"))).getHorizontalIndex());
            NBTTagList old=data.getTagList("Pos",6),pos=new NBTTagList();double x=old.getDoubleAt(0),z=old.getDoubleAt(2);pos.appendTag(new NBTTagDouble(turns==1?depth-z:turns==2?width-x:turns==3?z:x));pos.appendTag(new NBTTagDouble(old.getDoubleAt(1)));pos.appendTag(new NBTTagDouble(turns==1?x:turns==2?depth-z:turns==3?width-x:z));data.setTag("Pos",pos);
            NBTTagList angles=data.getTagList("Rotation",5);NBTTagList next=new NBTTagList();next.appendTag(new NBTTagFloat((angles.getFloatAt(0)+turns*90)%360));next.appendTag(new NBTTagFloat(angles.getFloatAt(1)));data.setTag("Rotation",next);
        }
    }
    static void remove(WorldServer world,VehicleStructure structure,BlockPos origin){
        NBTTagList list=entries(structure);for(int i=0;i<list.tagCount();i++){NBTTagCompound data=list.getCompoundTagAt(i);world.getChunkFromBlockCoords(origin.add(data.getInteger("TileX"),data.getInteger("TileY"),data.getInteger("TileZ")));NBTTagList position=data.getTagList("Pos",6);world.getChunkFromBlockCoords(new BlockPos(position.getDoubleAt(0)+origin.getX(),position.getDoubleAt(1)+origin.getY(),position.getDoubleAt(2)+origin.getZ()));net.minecraft.entity.Entity existing=world.getEntityFromUuid(data.getUniqueId("UUID"));if(existing!=null){if(!(existing instanceof EntityPainting))throw new IllegalArgumentException("Painting UUID is owned by another entity");world.removeEntityDangerously(existing);}}
    }
    static void publish(WorldServer world,VehicleStructure structure,BlockPos origin){
        NBTTagList list=entries(structure);for(int i=0;i<list.tagCount();i++){NBTTagCompound data=list.getCompoundTagAt(i).copy();translateEntity(data,origin);world.getChunkFromBlockCoords(new BlockPos(data.getInteger("TileX"),data.getInteger("TileY"),data.getInteger("TileZ")));
            NBTTagList position=data.getTagList("Pos",6);world.getChunkFromBlockCoords(new BlockPos(position.getDoubleAt(0),position.getDoubleAt(1),position.getDoubleAt(2)));
            net.minecraft.entity.Entity existing=world.getEntityFromUuid(data.getUniqueId("UUID"));if(existing!=null){if(!(existing instanceof EntityPainting))throw new IllegalArgumentException("Painting UUID is owned by another entity");existing.readFromNBT(data);continue;}
            EntityPainting painting=new EntityPainting(world);painting.readFromNBT(data);if(!painting.onValidSurface()||!world.spawnEntity(painting))throw new IllegalArgumentException("Painting could not be restored; vehicle recovery retained");
        }
    }
    static NBTTagCompound nativeTile(NBTTagCompound data){if(data==null||!has(data))return data;NBTTagCompound result=data.copy();clear(result);return result;}
}
