package com.vandorlabs.gear;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Arm sections temporarily owned by their controller while native payload blocks occupy their cells. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class ArmStoredSections {
    final List<VehicleStructure.Cell> cells;
    private final Map<BlockPos,VehicleStructure.Cell> byPosition=new HashMap<>();
    private final VehicleStructure normalized;
    private final BlockPos offset;
    private ArmStoredSections(List<VehicleStructure.Cell> relative){
        int x=Integer.MAX_VALUE,y=x,z=x;for(VehicleStructure.Cell cell:relative){x=Math.min(x,cell.pos.getX());y=Math.min(y,cell.pos.getY());z=Math.min(z,cell.pos.getZ());}
        offset=new BlockPos(x,y,z);cells=Collections.unmodifiableList(new ArrayList<>(relative));List<VehicleStructure.Cell> compact=new ArrayList<>();
        for(VehicleStructure.Cell cell:cells){byPosition.put(cell.pos,cell);compact.add(new VehicleStructure.Cell(cell.pos.subtract(offset),cell.state,VehicleTileData.translated(cell.tileData(),BlockPos.ORIGIN.subtract(offset))));}
        normalized=VehicleStructure.display(compact);
    }
    VehicleStructure.Cell at(BlockPos relative){return byPosition.get(relative);}
    NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setLong("Offset",offset.toLong());tag.setTag("Blocks",normalized.write());return tag;}

    static boolean canStore(GearTileEntity arm,BlockPos at){
        if(arm.mode()!=com.vandorlabs.gear.core.GearMotion.Mode.EXTEND||arm.isLandingAssembly()||arm.progress()!=0||!arm.members().contains(at))return false;
        for(BlockPos column:arm.columns())if(arm.getPos().add(column).equals(at))return false;
        World world=arm.getWorld();TileEntity raw=world.getTileEntity(at);
        return world.getBlockState(at).getBlock()==arm.getBlockType()&&raw instanceof GearTileEntity
                &&((GearTileEntity)raw).loadedController()==arm&&!((GearTileEntity)raw).hasRetainedPayload();
    }
    static ArmStoredSections capture(GearTileEntity arm,Collection<BlockPos> positions){
        List<VehicleStructure.Cell> cells=new ArrayList<>();World world=arm.getWorld();
        for(BlockPos at:positions){if(!canStore(arm,at))throw new IllegalArgumentException("Arm section changed before endpoint restoration");
            cells.add(new VehicleStructure.Cell(at.subtract(arm.getPos()),world.getBlockState(at),VehicleTileData.translated(world.getTileEntity(at).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(arm.getPos()))));}
        return cells.isEmpty()?null:new ArmStoredSections(cells);
    }
    public static NBTTagCompound retain(java.util.List<VehicleStructure.Cell> relative){return read(new ArmStoredSections(relative).write()).write();}
    public static java.util.List<VehicleStructure.Cell> retained(NBTTagCompound tag){return read(tag).cells;}
    static ArmStoredSections read(NBTTagCompound tag){
        VehicleStructure normalized=VehicleStructure.readDisplay(tag.getCompoundTag("Blocks"));BlockPos offset=BlockPos.fromLong(tag.getLong("Offset"));List<VehicleStructure.Cell> cells=new ArrayList<>();for(VehicleStructure.Cell cell:normalized.cells)cells.add(new VehicleStructure.Cell(offset.add(cell.pos),cell.state,VehicleTileData.translated(cell.tileData(),offset)));
        if(cells.isEmpty()||cells.size()>512)throw new IllegalArgumentException("Invalid stored arm sections");ArmStoredSections stored=new ArmStoredSections(cells);if(stored.cells.isEmpty()||stored.cells.size()>512)throw new IllegalArgumentException("Invalid stored arm sections");
        for(VehicleStructure.Cell cell:stored.cells){
            if(!(cell.state.getBlock() instanceof GearBlock)||((GearBlock)cell.state.getBlock()).kind()!=GearBlock.Kind.EXTENDER||!cell.hasTileData()||!(TileEntity.create(null,cell.tileData()) instanceof GearTileEntity)
                    ||cell.pos.equals(BlockPos.ORIGIN)||Math.abs((long)cell.pos.getX())>80||Math.abs((long)cell.pos.getY())>80||Math.abs((long)cell.pos.getZ())>80
                    ||cell.tileData().getBoolean("GearController")||!cell.tileData().hasKey("GearOwnerOffset",4)||!BlockPos.fromLong(cell.tileData().getLong("GearOwnerOffset")).equals(BlockPos.ORIGIN.subtract(cell.pos))||cell.tileData().hasKey("ArmStoredSections")||cell.tileData().hasKey("ArmPayload"))throw new IllegalArgumentException("Invalid stored arm section");
        }return stored;
    }
    static Set<BlockPos> positions(GearTileEntity arm){Set<BlockPos> result=new HashSet<>();if(arm.storedSections!=null)for(VehicleStructure.Cell cell:arm.storedSections.cells)result.add(arm.getPos().add(cell.pos));return result;}
    static Map<BlockPos,TileEntity> prepare(GearTileEntity arm,Set<BlockPos> removing){
        Map<BlockPos,TileEntity> result=new LinkedHashMap<>();if(arm.storedSections==null)return result;World world=arm.getWorld();
        for(VehicleStructure.Cell cell:arm.storedSections.cells){BlockPos at=arm.getPos().add(cell.pos);
            if(!GearAssembly.loaded(world,at)||!world.isAirBlock(at)&&!removing.contains(at))throw new IllegalArgumentException("Clear the arm section's construction cell before extending");
            VehicleService.permission(com.vandorlabs.tiles.WorldActionAuthorization.actor(arm),at);
            TileEntity tile=TileEntity.create(null,VehicleTileData.translated(cell.tileData(),arm.getPos()));if(!(tile instanceof GearTileEntity))throw new IllegalArgumentException("Cannot restore stored arm section");result.put(at,tile);
        }return result;
    }
    static void install(GearTileEntity arm,ArmStoredSections stored,Map<BlockPos,TileEntity> prepared){
        World world=arm.getWorld();if(stored==null)return;
        for(VehicleStructure.Cell cell:stored.cells){BlockPos at=arm.getPos().add(cell.pos);world.removeTileEntity(at);
            if(!world.setBlockState(at,cell.state,2)&&world.getBlockState(at)!=cell.state)throw new IllegalArgumentException("Cannot restore arm section");
            TileEntity tile=prepared.get(at);world.removeTileEntity(at);tile.setWorld(world);tile.validate();world.setTileEntity(at,tile);
            if(world.getTileEntity(at)!=tile)throw new IllegalArgumentException("Cannot install arm section data");tile.markDirty();
        }
    }
    static boolean restoreEmpty(GearTileEntity arm){
        if(arm.storedSections==null)return true;
        try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){
            Map<BlockPos,TileEntity> prepared=prepare(arm,Collections.emptySet());
            if(!ArmTransactions.ready(arm,"restore-sections",positions(arm))){arm.awaitPayloadJournal();return false;}
            install(arm,arm.storedSections,prepared);arm.storedSections=null;arm.markDirty();ArmTransactions.applied(arm);return false;
        }catch(RuntimeException|LinkageError failure){ArmTransactions.failed(arm);throw failure;}
    }
    static NBTTagCompound rotate(NBTTagCompound tag,int turns,World parent){
        ArmStoredSections before=read(tag);List<VehicleStructure.Cell> cells=new ArrayList<>();net.minecraft.util.Rotation rotation=net.minecraft.util.Rotation.values()[Math.floorMod(turns,4)];
        for(VehicleStructure.Cell cell:before.cells){BlockPos at=cell.pos;for(int i=0;i<Math.floorMod(turns,4);i++)at=new BlockPos(-at.getZ(),at.getY(),at.getX());
            NBTTagCompound data=cell.tileData();GearData.rotate(data,turns,parent);data.setInteger("x",at.getX());data.setInteger("y",at.getY());data.setInteger("z",at.getZ());cells.add(new VehicleStructure.Cell(at,cell.state.withRotation(rotation),data));}
        return new ArmStoredSections(cells).write();
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void breaking(net.minecraftforge.event.world.BlockEvent.BreakEvent event){
        TileEntity raw=event.getWorld().getTileEntity(event.getPos());if(!(raw instanceof GearTileEntity))return;
        GearTileEntity root=((GearTileEntity)raw).loadedController();if(root==null||!root.hasStoredSections())return;
        event.setCanceled(true);event.getPlayer().sendStatusMessage(new net.minecraft.util.text.TextComponentString("Extend the arms before removing arm sections"),true);
    }
}
