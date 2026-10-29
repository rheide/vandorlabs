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

/** Explicit cardinal restoration adapters, validated before removing world blocks. */
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
        TaxiModularGear.requireParkable(craft);
        craft.signals.persist();
        TaxiTelescopicGear.requireParkable(craft);
        int turns=turns(craft.rotationYaw);VehicleStructure placed=craft.rotatedSnapshot(turns);
        double centerX=craft.posX+craft.structure.bounds.maxX/2,centerZ=craft.posZ+craft.structure.bounds.maxZ/2;
        Plan parking=VehicleLandingGear.parking(placed,new BlockPos(Math.round(centerX-placed.bounds.maxX/2),Math.round(craft.posY),Math.round(centerZ-placed.bounds.maxZ/2)));
        // Retraction can lower the chassis onto the ground. Keep its support height
        // when extending the restored feet, then validate the raised hull normally.
        VehicleCollision extended=VehicleTransfer.geometry(craft.world,parking.structure);
        int supportedY=(int)Math.round(craft.posY+craft.collision.bounds.minY-extended.bounds.minY);
        return new Plan(parking.structure,new BlockPos(parking.origin.getX(),Math.max(parking.origin.getY(),supportedY),parking.origin.getZ()));
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
    /** Refuse unsupported private data before conversion; a craft must be parkable at every heading. */
    public static void validate(VehicleStructure source,VehicleWorld view){
        VehicleForeignCompat.validate(source,view);
        for(VehicleStructure.Cell cell:source.cells){
            try{
                if(!MovingBlockSupport.supported(cell,view.getTileEntity(cell.pos)))throw new IllegalArgumentException("No lossless block-data adapter for "+cell.state.getBlock().getRegistryName()+". Remove this block before moving or copying the structure.");
                if(cell.state.getMaterial().isLiquid() || cell.state.getBlockHardness(view,cell.pos)<0&&!(cell.state.getBlock() instanceof BlockControlledRamp))throw new IllegalArgumentException("Unsupported moving block: "+cell.state.getBlock().getRegistryName());
                MovingBlockSupport.requireExactTileData(cell,view.getTileEntity(cell.pos));
                IBlockState cycle=cell.state;
                for(int i=0;i<4;i++)cycle=state(cycle,Rotation.CLOCKWISE_90);
                if(cycle!=cell.state)throw new IllegalArgumentException("Block rotation does not round-trip: "+cell.state.getBlock().getRegistryName());
            }catch(LinkageError error){VehicleCompatibility.warn(cell.state.getBlock(),"rotation preflight",error);throw new IllegalArgumentException("Cannot safely rotate "+cell.state.getBlock().getRegistryName(),error);}
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static IBlockState state(IBlockState source,Rotation rotation) {
        IBlockState rotated=BuildingBlockCompat.rotate(source,rotation);
        // Vandor families also include directional blocks whose base class does not override withRotation.
        if(source.getBlock().getRegistryName().getResourceDomain().equals("vandorlabs"))
            for(IProperty property:source.getPropertyKeys())if(source.getValue(property) instanceof EnumFacing) {
                EnumFacing facing=rotation.rotate((EnumFacing)source.getValue(property));
                if(property.getAllowedValues().contains(facing))rotated=rotated.withProperty(property,facing);
            }
        if(MovingBlockSupport.inertBuilding(source)){
            for(IProperty property:source.getPropertyKeys()){
                String key=property.getName();EnumFacing side=null;
                for(EnumFacing direction:EnumFacing.HORIZONTALS)if(direction.getName().equals(key))side=direction;
                if(side==null||!(source.getValue(property) instanceof Boolean))continue;
                String target=rotation.rotate(side).getName();
                for(IProperty destination:source.getPropertyKeys())if(destination.getName().equals(target)&&destination.getValueClass()==Boolean.class)
                    rotated=rotated.withProperty(destination,source.getValue(property));
            }
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
            if(!MovingBlockSupport.supported(before,view.getTileEntity(before.pos)))throw new IllegalArgumentException("Rotation is not supported for "+before.state.getBlock().getRegistryName()+". Rotate the craft back to its original heading before exiting Taxi Mode.");
            BlockPos pos=cell(before.pos,width,depth,turns);NBTTagCompound data=before.tileData();
            if(data!=null) {
                data.setInteger("x",pos.getX());data.setInteger("y",pos.getY());data.setInteger("z",pos.getZ());
                BuildingBlockCompat.rotateTile(before.state,data,rotation);VehiclePaintings.rotate(data,width,depth,turns);
                String domain=before.state.getBlock().getRegistryName().getResourceDomain();
                if(domain.equals("vandorlabs")) {
                    if(data.getString("id").equals("vandorlabs:modular_gear")||data.getString("id").equals("vandorlabs:track"))com.vandorlabs.gear.GearData.rotate(data,turns,view.parent);
                    for(String key:new String[]{"Anchor","Pair","TrapdoorPartner","TrapdoorSquare","RampJournalOrigin"})if(data.hasKey(key,4)) {
                        BlockPos link=BlockPos.fromLong(data.getLong(key));
                        if(key.equals("TrapdoorSquare") && !(view.getTileEntity(before.pos) instanceof TileEntityProgrammableDiagonalTrapdoor)) {
                            BlockPos min=cell(link,width,depth,turns);
                            for(BlockPos corner:new BlockPos[]{link.east(),link.south(),link.east().south()}){BlockPos p=cell(corner,width,depth,turns);min=new BlockPos(Math.min(min.getX(),p.getX()),min.getY(),Math.min(min.getZ(),p.getZ()));}
                            data.setLong(key,min.toLong());
                        } else data.setLong(key,cell(link,width,depth,turns).toLong());
                    }
                    if(data.hasKey("CanopyFootprint",11)){int[] offsets=data.getIntArray("CanopyFootprint");for(int i=0;i+2<offsets.length;i+=3){BlockPos delta=vector(new BlockPos(offsets[i],offsets[i+1],offsets[i+2]),turns);offsets[i]=delta.getX();offsets[i+1]=delta.getY();offsets[i+2]=delta.getZ();}data.setIntArray("CanopyFootprint",offsets);}
                    for(String key:new String[]{"OwnerOffset","AnchorOffset","PairOffset","ConstructorOwner","NavigationOwner","NavigationOrigin"})if(data.hasKey(key,4))data.setLong(key,vector(BlockPos.fromLong(data.getLong(key)),turns).toLong());
                    NBTTagList list=data.getTagList("TrapdoorAssembly",4),updated=new NBTTagList();
                    for(int n=0;n<list.tagCount();n++)updated.appendTag(new NBTTagLong(cell(BlockPos.fromLong(((NBTTagLong)list.get(n)).getLong()),width,depth,turns).toLong()));
                    if(data.hasKey("TrapdoorAssembly",9))data.setTag("TrapdoorAssembly",updated);
                    for(String key:new String[]{"TrapdoorCoverFacing","DiagonalFacing","DiagonalGroupFacing","GlassPanelFacing","NavigationFacing"})facing(data,key,rotation,true);
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
                    VehicleForeignCompat.rotate(view.getTileEntity(before.pos),data,rotation);
                    facing(data,"facing",rotation,false);
                    NBTTagCompound old=data.copy();
                    for(EnumFacing side:EnumFacing.values())if(old.hasKey("sideConfig_"+side.getIndex(),3))data.setInteger("sideConfig_"+rotation.rotate(side).getIndex(),old.getInteger("sideConfig_"+side.getIndex()));
                } else if(domain.equals("mekanism")||MekanismTankCompat.reactorGlass(view.getTileEntity(before.pos))){
                    facing(data,"facing",rotation,false);
                    if(view.getTileEntity(before.pos)!=null&&view.getTileEntity(before.pos).getClass().getName().equals("mekanism.common.tile.TileEntityGlowPanel"))facing(data,"side",rotation,false);
                }
            }
            try{cells.add(new VehicleStructure.Cell(pos,state(before.state,rotation),data));}
            catch(RuntimeException | LinkageError failure){throw new IllegalArgumentException("Cannot rotate "+before.state.getBlock().getRegistryName()+" safely. Rotate back to the original heading before parking.",failure);}
        }
        return source.displayOnly?VehicleStructure.display(cells):new VehicleStructure(cells,cell(source.seat,width,depth,turns),rotation.rotate(source.facing),source.gearCount);
    }
    /** Rotate the retained expanded ramp and its recovery journal as one assembly. */
    private static void rotateRampData(NBTTagCompound data,int width,int depth,int turns,Rotation rotation){
        facing(data,"RampDirection",rotation,true);facing(data,"Facing",rotation,true);
        // Saved map boxes are block-local geometry, and must turn with the ramp.
        // Keep their order and micro-unit precision identical to the tile writer.
        if(data.hasKey("DynmapBoxes",8)&&!data.getString("DynmapBoxes").isEmpty()){
            StringBuilder boxes=new StringBuilder();
            for(String box:data.getString("DynmapBoxes").split(";")){
                String[] coordinates=box.split(",");
                if(coordinates.length!=6)throw new IllegalArgumentException("Invalid saved ramp map geometry");
                double[] values=new double[6];for(int i=0;i<6;i++)values[i]=Double.parseDouble(coordinates[i]);
                for(int i=0;i<turns;i++){double minX=values[0],maxX=values[3];values[0]=1-values[5];values[3]=1-values[2];values[2]=minX;values[5]=maxX;}
                if(boxes.length()>0)boxes.append(';');
                for(int i=0;i<6;i++){if(i>0)boxes.append(',');boxes.append(Math.round(values[i]*1000000D)/1000000D);}
            }
            data.setString("DynmapBoxes",boxes.toString());
        }
        for(String key:new String[]{"OriginalState","SourceState"})if(data.hasKey(key,8)){
            IBlockState original=com.vandorlabs.persistence.LegacyBlockStates.decode(data.getString(key));
            if(original!=null){IBlockState rotated=state(original,rotation);data.setString(key,com.vandorlabs.persistence.LegacyBlockStates.encode(rotated));data.setTag(key.equals("OriginalState")?"Original":"Source",NBTUtil.writeBlockState(new NBTTagCompound(),rotated));}
        }
        for(String key:new String[]{"Sources","Cells","Origins","RampSourceTiles","RampCanopyBlockers"}){
            NBTTagList list=data.getTagList(key,10);
            for(int n=0;n<list.tagCount();n++){
                NBTTagCompound entry=list.getCompoundTagAt(n);
                BlockPos old=entry.hasKey("X",3)?new BlockPos(entry.getInteger("X"),entry.getInteger("Y"),entry.getInteger("Z")):BlockPos.fromLong(entry.getLong("Pos"));
                BlockPos next=cell(old,width,depth,turns);
                entry.setInteger("X",next.getX());entry.setInteger("Y",next.getY());entry.setInteger("Z",next.getZ());if(entry.hasKey("Pos",4))entry.setLong("Pos",next.toLong());
                if(entry.hasKey("Tile",10)){
                    NBTTagCompound saved=entry.getCompoundTag("Tile");saved.setInteger("x",next.getX());saved.setInteger("y",next.getY());saved.setInteger("z",next.getZ());
                    if(saved.hasKey(com.vandorlabs.compat.RampStairs.STATE,8)){
                        IBlockState stair=com.vandorlabs.persistence.LegacyBlockStates.decode(saved.getString(com.vandorlabs.compat.RampStairs.STATE));
                        if(stair!=null)saved.setString(com.vandorlabs.compat.RampStairs.STATE,com.vandorlabs.persistence.LegacyBlockStates.encode(state(stair,rotation)));
                    }
                    for(String direction:new String[]{"DiagonalFacing","DiagonalGroupFacing","GlassPanelFacing","NavigationFacing"})facing(saved,direction,rotation,true);
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
