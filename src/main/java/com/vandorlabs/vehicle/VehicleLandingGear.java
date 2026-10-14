package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Restore fully extended gear as part of the checked, atomic parking footprint. */
public final class VehicleLandingGear {
    private VehicleLandingGear(){}
    public static void retract(EntityGroundVehicle craft){
        List<VehicleStructure.Cell> cells=new ArrayList<>();boolean changed=false;
        for(VehicleStructure.Cell cell:craft.structure.cells){
            if(cell.state.getBlock() instanceof BlockTelescopicLandingGear && !cell.state.getValue(BlockTelescopicLandingGear.LOWER) && cell.state.getValue(BlockTelescopicLandingGear.EXTENDED)){
                cells.add(new VehicleStructure.Cell(cell.pos,cell.state.withProperty(BlockTelescopicLandingGear.EXTENDED,false),cell.tileData()));changed=true;
            }else cells.add(cell);
        }
        if(changed)craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),true);
        // Share the same cover predicate as placed gear; the captured world is read-only.
        for(net.minecraft.tileentity.TileEntity raw:new ArrayList<>(craft.view.tiles.values()))if(raw instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor){
            com.vandorlabs.tiles.TileEntityProgrammableTrapdoor cover=(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)raw;
            if(cover.isCover()){
                boolean open=com.vandorlabs.tiles.LandingGearCovers.needsOpen(cover);
                boolean actual=raw.getBlockType().getActualState(craft.view.getBlockState(raw.getPos()),craft.view,raw.getPos()).getValue(net.minecraft.block.BlockDoor.OPEN);
                if(open!=actual)VehicleDoors.toggle(craft,raw.getPos());
            }
        }
    }
    public static void tick(EntityGroundVehicle craft){
        if(craft.view==null || craft.ownershipBlocked)return;
        Set<BlockPos> changed=new HashSet<>();
        for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof TileEntityLandingGear){
            TileEntityLandingGear tile=(TileEntityLandingGear)raw;float before=tile.progress;tile.update();if(before!=tile.progress)changed.add(tile.getPos());
        }
        if(changed.isEmpty())return;
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:craft.structure.cells)cells.add(changed.contains(cell.pos)?new VehicleStructure.Cell(cell.pos,cell.state,craft.view.getTileEntity(cell.pos).writeToNBT(new NBTTagCompound())):cell);
        craft.updateCanopies(new VehicleStructure(cells,craft.structure.seat,craft.structure.facing,craft.structure.gearCount),true);
    }
    public static VehicleRotation.Plan parking(VehicleStructure source,BlockPos origin){
        Map<BlockPos,VehicleStructure.Cell> cells=new LinkedHashMap<>();
        // Rebuild reservations from the restored roots. Rotated 2x2 footprints
        // and old, longer reservations must not leave unused invisible cells.
        for(VehicleStructure.Cell cell:source.cells)if(!(cell.state.getBlock() instanceof BlockTelescopicLandingGear)
                || !cell.state.getValue(BlockTelescopicLandingGear.LOWER))cells.put(cell.pos,cell);
        int minX=0,minY=0,minZ=0;
        for(VehicleStructure.Cell cell:source.cells){
            if(!(cell.state.getBlock() instanceof BlockTelescopicLandingGear) || cell.state.getValue(BlockTelescopicLandingGear.LOWER))continue;
            NBTTagCompound tag=cell.tileData();if(tag==null)throw new IllegalArgumentException("Landing gear is missing its configuration.");
            int size=tag.getInteger("GearSize"),pixels=tag.hasKey("ExtensionPixels")?tag.getInteger("ExtensionPixels"):16;
            int count=(int)Math.ceil(pixels/16D)+(size>=3?1:0),radius=size>=3?1:0,minimum=size==4?0:-radius;
            tag.setFloat("Progress",pixels/16F);
            cells.put(cell.pos,new VehicleStructure.Cell(cell.pos,cell.state.withProperty(BlockTelescopicLandingGear.EXTENDED,true),tag));
            for(int y=0;y<=count;y++)for(int x=minimum;x<=radius;x++)for(int z=minimum;z<=radius;z++){
                if(x==0&&y==0&&z==0)continue;
                BlockPos p=cell.pos.add(x,-y,z);VehicleStructure.Cell existing=cells.get(p);
                if(existing!=null){
                    NBTTagCompound owner=existing.tileData();
                    if(existing.state.getBlock()!=cell.state.getBlock() || !existing.state.getValue(BlockTelescopicLandingGear.LOWER) || owner==null
                            || !p.add(owner.getInteger("GearOwnerX"),owner.getInteger("GearOwnerDistance"),owner.getInteger("GearOwnerZ")).equals(cell.pos))
                        throw new IllegalArgumentException("Extended landing gear overlaps another craft block.");
                }
                TileEntityLandingGear part=new TileEntityLandingGear();part.setPos(p);part.setOwner(cell.pos);
                cells.put(p,new VehicleStructure.Cell(p,cell.state.withProperty(BlockTelescopicLandingGear.LOWER,true).withProperty(BlockTelescopicLandingGear.EXTENDED,true),part.writeToNBT(new NBTTagCompound())));
                minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            }
        }
        BlockPos shift=new BlockPos(-minX,-minY,-minZ);List<VehicleStructure.Cell> result=new ArrayList<>();
        for(VehicleStructure.Cell cell:cells.values())result.add(new VehicleStructure.Cell(cell.pos.add(shift),cell.state,cell.hasTileData()?VehicleTileData.translated(cell.tileData(),shift):null));
        return new VehicleRotation.Plan(new VehicleStructure(result,source.seat.add(shift),source.facing,source.gearCount),origin.subtract(shift));
    }
}
