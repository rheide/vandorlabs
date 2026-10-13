package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Explicit cardinal restoration adapters. Unknown components are accepted only at zero rotation. */
public final class VehicleRotation {
    private VehicleRotation(){}
    public static final class Plan {
        public final VehicleStructure structure;
        public final BlockPos origin;
        Plan(VehicleStructure s,BlockPos p){structure=s;origin=p;}
    }
    public static int turns(float yaw) {
        int nearest=Math.round(yaw/90F);
        if(!Float.isFinite(yaw) || Math.abs(MathHelper.wrapDegrees(yaw-nearest*90F))>12)
            throw new IllegalArgumentException("Align the craft to a 90-degree heading before parking (within 12 degrees).");
        return Math.floorMod(nearest,4);
    }
    public static Plan plan(EntityGroundVehicle craft) {
        int turns=turns(craft.rotationYaw);VehicleStructure placed=craft.rotatedSnapshot(turns);
        double centerX=craft.posX+craft.structure.bounds.maxX/2,centerZ=craft.posZ+craft.structure.bounds.maxZ/2;
        return new Plan(placed,new BlockPos(Math.round(centerX-placed.bounds.maxX/2),Math.round(craft.posY),Math.round(centerZ-placed.bounds.maxZ/2)));
    }
    public static BlockPos cell(BlockPos p,int width,int depth,int turns) {
        switch(Math.floorMod(turns,4)) {
            case 1:return new BlockPos(depth-1-p.getZ(),p.getY(),p.getX());
            case 2:return new BlockPos(width-1-p.getX(),p.getY(),depth-1-p.getZ());
            case 3:return new BlockPos(p.getZ(),p.getY(),width-1-p.getX());
            default:return p;
        }
    }
    private static BlockPos vector(BlockPos p,int turns) {
        switch(turns){case 1:return new BlockPos(-p.getZ(),p.getY(),p.getX());case 2:return new BlockPos(-p.getX(),p.getY(),-p.getZ());case 3:return new BlockPos(p.getZ(),p.getY(),-p.getX());default:return p;}
    }
    private static boolean supported(VehicleStructure.Cell cell,TileEntity tile) {
        if(cell.tileData()!=null && tile==null)return false;
        String domain=cell.state.getBlock().getRegistryName().getResourceDomain();
        String name=tile==null?"":tile.getClass().getName();
        // Malisis may replace vanilla registry entries while keeping the minecraft namespace.
        if((domain.equals("minecraft") || domain.equals("malisisdoors")) &&
                (name.equals("net.malisis.doors.tileentity.DoorTileEntity") || name.equals("net.malisis.doors.tileentity.TrapDoorTileEntity")))return true;
        if(domain.equals("minecraft"))return cell.state.getBlock().getClass().getName().startsWith("net.minecraft.block.");
        if(domain.equals("vandorlabs")) {
            if(tile instanceof TileEntityRampController){
                TileEntityRampController ramp=(TileEntityRampController)tile;
                return !ramp.isMoving() && !cell.tileData().getBoolean("RecoveryPending")
                        && (cell.tileData().getBoolean("VehicleRampFrozen") || !ramp.attached() && ramp.vehicleCells().isEmpty());
            }
            return true;
        }
        if(domain.equals("immersiveengineering"))return name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityLadder")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitorLV")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitorMV")
                || name.equals("blusunrize.immersiveengineering.common.blocks.metal.TileEntityCapacitorHV");
        if(domain.equals("mekanism"))return name.equals("mekanism.common.tile.TileEntityEnergyCube");
        return domain.equals("malisisdoors") && tile==null && upperDoor(cell.state);
    }
    private static boolean upperDoor(IBlockState state) {
        return state.getBlock() instanceof net.minecraft.block.BlockDoor
                && state.getValue(net.minecraft.block.BlockDoor.HALF)==net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static IBlockState state(IBlockState source,Rotation rotation) {
        IBlockState rotated=source.withRotation(rotation);
        // Vandor families also include directional blocks whose base class does not override withRotation.
        if(source.getBlock().getRegistryName().getResourceDomain().equals("vandorlabs"))
            for(IProperty property:source.getPropertyKeys())if(source.getValue(property) instanceof EnumFacing) {
                EnumFacing facing=rotation.rotate((EnumFacing)source.getValue(property));
                if(property.getAllowedValues().contains(facing))rotated=rotated.withProperty(property,facing);
            }
        if(source.getBlock() instanceof BlockGlassWall) {
            boolean turned=source.getValue(BlockGlassWall.ROTATED);int depth=source.getValue(BlockGlassWall.DEPTH);
            for(int n=0;n<rotation.ordinal();n++){if(turned)depth=depth==1?2:depth==2?1:0;turned=!turned;}
            rotated=rotated.withProperty(BlockGlassWall.ROTATED,turned).withProperty(BlockGlassWall.DEPTH,depth);
        }
        if(source.getBlock() instanceof BlockVandorSwitch && source.getValue(BlockVandorSwitch.FACING).getAxis()==EnumFacing.Axis.Y)
            rotated=rotated.withProperty(BlockVandorSwitch.ROTATION,(source.getValue(BlockVandorSwitch.ROTATION)+rotation.ordinal())&3);
        return rotated;
    }
    private static void facing(NBTTagCompound data,String key,Rotation rotation,boolean horizontal) {
        if(!data.hasKey(key,3))return;
        EnumFacing before=horizontal?EnumFacing.getHorizontal(data.getInteger(key)):EnumFacing.getFront(data.getInteger(key));
        EnumFacing after=rotation.rotate(before);data.setInteger(key,horizontal?after.getHorizontalIndex():after.getIndex());
    }
    public static VehicleStructure rotate(VehicleStructure source,VehicleWorld view,int turns) {
        turns=Math.floorMod(turns,4);if(turns==0)return source;
        int width=(int)source.bounds.maxX,depth=(int)source.bounds.maxZ;
        Rotation rotation=Rotation.values()[turns]; // NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell before:source.cells) {
            if(!supported(before,view.getTileEntity(before.pos)))throw new IllegalArgumentException("Rotation is not supported for "+before.state.getBlock().getRegistryName()+". Rotate the craft back to its original heading before exiting Taxi Mode.");
            BlockPos pos=cell(before.pos,width,depth,turns);NBTTagCompound data=before.tileData();
            if(data!=null) {
                data.setInteger("x",pos.getX());data.setInteger("y",pos.getY());data.setInteger("z",pos.getZ());
                String domain=before.state.getBlock().getRegistryName().getResourceDomain();
                if(domain.equals("vandorlabs")) {
                    for(String key:new String[]{"Anchor","Pair","TrapdoorPartner","TrapdoorSquare"})if(data.hasKey(key,4)) {
                        BlockPos link=BlockPos.fromLong(data.getLong(key));
                        if(key.equals("TrapdoorSquare") && !(view.getTileEntity(before.pos) instanceof TileEntityProgrammableDiagonalTrapdoor)) {
                            BlockPos min=cell(link,width,depth,turns);
                            for(BlockPos corner:new BlockPos[]{link.east(),link.south(),link.east().south()}){BlockPos p=cell(corner,width,depth,turns);min=new BlockPos(Math.min(min.getX(),p.getX()),min.getY(),Math.min(min.getZ(),p.getZ()));}
                            data.setLong(key,min.toLong());
                        } else data.setLong(key,cell(link,width,depth,turns).toLong());
                    }
                    for(String key:new String[]{"OwnerOffset","ConsoleOwnerOffset","AnchorOffset","PairOffset"})if(data.hasKey(key,4))data.setLong(key,vector(BlockPos.fromLong(data.getLong(key)),turns).toLong());
                    NBTTagList list=data.getTagList("TrapdoorAssembly",4),updated=new NBTTagList();
                    for(int n=0;n<list.tagCount();n++)updated.appendTag(new NBTTagLong(cell(BlockPos.fromLong(((NBTTagLong)list.get(n)).getLong()),width,depth,turns).toLong()));
                    if(data.hasKey("TrapdoorAssembly",9))data.setTag("TrapdoorAssembly",updated);
                    for(String key:new String[]{"TrapdoorCoverFacing","DiagonalFacing","DiagonalGroupFacing","GlassPanelFacing"})facing(data,key,rotation,true);
                    facing(data,"MountFace",rotation,false);
                    if(before.state.getBlock() instanceof BlockVandorSwitch && before.state.getValue(BlockVandorSwitch.FACING).getAxis()==EnumFacing.Axis.Y && data.hasKey("MountRotation",3))
                        data.setInteger("MountRotation",(data.getInteger("MountRotation")+turns)&3);
                    if(view.getTileEntity(before.pos) instanceof TileEntityRampController || view.getTileEntity(before.pos) instanceof TileEntityControlledRamp)
                        rotateRampData(data,width,depth,turns,rotation);
                    if(data.hasKey("GearOwnerX",3)){BlockPos offset=vector(new BlockPos(data.getInteger("GearOwnerX"),0,data.getInteger("GearOwnerZ")),turns);data.setInteger("GearOwnerX",offset.getX());data.setInteger("GearOwnerZ",offset.getZ());}
                } else if(domain.equals("minecraft")) {
                    if(data.hasKey("Rot",99) && before.state.getBlock() instanceof net.minecraft.block.BlockSkull)
                        data.setByte("Rot",(byte)((data.getByte("Rot")+turns*4)&15));
                    if(data.hasKey("facing",3) && before.state.getBlock() instanceof net.minecraft.block.BlockPistonMoving){
                        facing(data,"facing",rotation,false);
                        net.minecraft.block.Block carried=net.minecraft.block.Block.getBlockById(data.getInteger("blockId"));
                        if(carried!=null)data.setInteger("blockData",carried.getMetaFromState(state(carried.getStateFromMeta(data.getInteger("blockData")),rotation)));
                    }
                    if(before.state.getBlock() instanceof net.minecraft.block.BlockStructure){
                        BlockPos offset=vector(new BlockPos(data.getInteger("posX"),data.getInteger("posY"),data.getInteger("posZ")),turns);
                        if("SAVE".equals(data.getString("mode"))){
                            int sizeX=data.getInteger("sizeX"),sizeZ=data.getInteger("sizeZ");
                            BlockPos opposite=vector(new BlockPos(data.getInteger("posX")+Math.max(0,sizeX-1),data.getInteger("posY"),data.getInteger("posZ")+Math.max(0,sizeZ-1)),turns);
                            offset=new BlockPos(Math.min(offset.getX(),opposite.getX()),offset.getY(),Math.min(offset.getZ(),opposite.getZ()));
                            if((turns&1)!=0){data.setInteger("sizeX",sizeZ);data.setInteger("sizeZ",sizeX);}
                        }
                        data.setInteger("posX",offset.getX());data.setInteger("posZ",offset.getZ());
                        try{data.setString("rotation",Rotation.values()[(Rotation.valueOf(data.getString("rotation")).ordinal()+turns)&3].name());}catch(IllegalArgumentException ignored){}
                    }
                } else if(domain.equals("immersiveengineering")) {
                    facing(data,"facing",rotation,false);
                    NBTTagCompound old=data.copy();
                    for(EnumFacing side:EnumFacing.values())if(old.hasKey("sideConfig_"+side.getIndex(),3))data.setInteger("sideConfig_"+rotation.rotate(side).getIndex(),old.getInteger("sideConfig_"+side.getIndex()));
                } else if(domain.equals("mekanism"))facing(data,"facing",rotation,false);
            }
            try{cells.add(new VehicleStructure.Cell(pos,state(before.state,rotation),data));}
            catch(RuntimeException | LinkageError failure){throw new IllegalArgumentException("Cannot rotate "+before.state.getBlock().getRegistryName()+" safely. Rotate back to the original heading before parking.",failure);}
        }
        return new VehicleStructure(cells,cell(source.seat,width,depth,turns),rotation.rotate(source.facing),source.gearCount);
    }
    /** Rotate the retained expanded ramp and its recovery journal as one assembly. */
    private static void rotateRampData(NBTTagCompound data,int width,int depth,int turns,Rotation rotation){
        facing(data,"RampDirection",rotation,true);facing(data,"Facing",rotation,true);
        for(String key:new String[]{"OriginalState","SourceState"})if(data.hasKey(key,8)){
            IBlockState original=com.vandorlabs.persistence.LegacyBlockStates.decode(data.getString(key));
            if(original!=null){IBlockState rotated=state(original,rotation);data.setString(key,com.vandorlabs.persistence.LegacyBlockStates.encode(rotated));data.setTag(key.equals("OriginalState")?"Original":"Source",NBTUtil.writeBlockState(new NBTTagCompound(),rotated));}
        }
        for(String key:new String[]{"Sources","Cells","Origins","RampSourceTiles"}){
            NBTTagList list=data.getTagList(key,10);
            for(int n=0;n<list.tagCount();n++){
                NBTTagCompound entry=list.getCompoundTagAt(n);
                BlockPos old=entry.hasKey("X",3)?new BlockPos(entry.getInteger("X"),entry.getInteger("Y"),entry.getInteger("Z")):BlockPos.fromLong(entry.getLong("Pos"));
                BlockPos next=cell(old,width,depth,turns);
                entry.setInteger("X",next.getX());entry.setInteger("Y",next.getY());entry.setInteger("Z",next.getZ());if(entry.hasKey("Pos",4))entry.setLong("Pos",next.toLong());
                if(entry.hasKey("Tile",10)){
                    NBTTagCompound saved=entry.getCompoundTag("Tile");saved.setInteger("x",next.getX());saved.setInteger("y",next.getY());saved.setInteger("z",next.getZ());
                    for(String direction:new String[]{"DiagonalFacing","DiagonalGroupFacing","GlassPanelFacing"})facing(saved,direction,rotation,true);
                    facing(saved,"MountFace",rotation,false);
                }
            }
        }
        if(data.hasKey("Controller",4)){
            BlockPos old=data.hasKey("ControllerX",3)?new BlockPos(data.getInteger("ControllerX"),data.getInteger("ControllerY"),data.getInteger("ControllerZ")):BlockPos.fromLong(data.getLong("Controller"));
            BlockPos next=cell(old,width,depth,turns);data.setLong("Controller",next.toLong());data.setInteger("ControllerX",next.getX());data.setInteger("ControllerY",next.getY());data.setInteger("ControllerZ",next.getZ());
        }
        NBTTagList sources=data.getTagList("Sources",10);
        if(data.hasKey("MinAlong",3) && sources.tagCount()>0){
            EnumFacing direction=EnumFacing.getHorizontal(data.getInteger("Facing"));int min=Integer.MAX_VALUE;
            for(int n=0;n<sources.tagCount();n++){NBTTagCompound entry=sources.getCompoundTagAt(n);min=Math.min(min,entry.getInteger("X")*direction.getFrontOffsetX()+entry.getInteger("Z")*direction.getFrontOffsetZ());}
            data.setInteger("MinAlong",min);
        }
    }
}
