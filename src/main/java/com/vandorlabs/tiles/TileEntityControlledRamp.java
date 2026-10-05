package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockControlledRamp;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.ramp.ControllerPlatform;
import com.vandorlabs.ramp.ControllerRecovery;
import com.vandorlabs.ramp.RampGeometry;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.persistence.RampCellData;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.persistence.LegacyBlockStates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Non-ticking occupied/next-step cell. Carries its own recovery journal and timeline. */
public class TileEntityControlledRamp extends TileEntity {
    /** World geometry follows loaded terrain visibility, like diagonal walls. */
    @Override public double getMaxRenderDistanceSquared() { return Double.MAX_VALUE; }

    public BlockPos controller=BlockPos.ORIGIN;
    public IBlockState source=Blocks.STONE.getDefaultState();
    public int startOffset,treadPixels=8;
    private int startHalfSteps,endHalfSteps=-6;
    public void setHalfOffsets(int start,int end) {
        startHalfSteps=start; endHalfSteps=end;
    }
    public double startOffsetValue() { return startHalfSteps/2D; }
    public double endOffsetValue() { return endHalfSteps/2D; }
    // Keep the legacy magnitude/sign fields for old saves and integrations.
    public int endOffset() { return top?-drop:drop; }
    public int sourceY,row,length=1,drop=3,segments=2,duration=60;
    public int travelAxis=RampGeometry.VERTICAL;
    public int speed=1;
    public int interpolation;
    public boolean extendSegments;
    public final List<BlockPos> origins=new ArrayList<>();
    public final Map<BlockPos,NBTTagCompound> sourceTileTags=new LinkedHashMap<>();
    public double low,high=1;
    public boolean top=true,elevator;
    private boolean open,moving;
    private double startPose;
    private long startTick;

    public boolean isOpen() { return open; }
    public boolean isMoving() { return moving; }
    public double pose(double partial) {
        return moving?ControllerPlatform.pose(startPose,open,world.getTotalWorldTime()-startTick+partial,duration):startPose;
    }
    public void move(boolean target,double from,long tick,int ticks,boolean animate) {
        open=target; startPose=from; startTick=tick; duration=ticks; moving=animate;
        markDirty();
        IBlockState s=world.getBlockState(pos);
        world.notifyBlockUpdate(pos,s,s,2);
    }
    public List<AxisAlignedBB> boxes(double partial) {
        List<AxisAlignedBB> boxes=new ArrayList<>();
        for (RampGeometry.Box box:geometry(partial))
            boxes.add(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        return boxes;
    }
    public List<RampGeometry.Box> geometry(double partial) {
        List<RampGeometry.Box> boxes=new ArrayList<>();
        IBlockState state=world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockControlledRamp)) return boxes;
        EnumFacing face=state.getValue(BlockVandorDirectional.FACING);
        if (origins.isEmpty() && travelAxis==RampGeometry.VERTICAL && !extendSegments)
            return RampGeometry.boxesPixels(direction(face),pos.getY(),sourceY,low,high,
                    row,length,startOffsetValue(),endOffsetValue(),treadPixels,pose(partial),elevator,speed==0,interpolation);
        for (BlockPos origin:origins) {
            int originRow=origin.getX()*face.getFrontOffsetX()+origin.getZ()*face.getFrontOffsetZ()
                    -(pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row);
            for (int step=0;step<(elevator?1:ControllerPlatform.treadCount(treadPixels));step++) {
                RampGeometry.Box whole=RampGeometry.movingTread(direction(face),origin.getX(),origin.getY(),origin.getZ(),
                        low,high,originRow,length,startOffsetValue(),endOffsetValue(),treadPixels,step,pose(partial),pose(partial),
                        elevator,travelAxis,extendSegments,speed==0,interpolation);
                RampGeometry.Box clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());
                if (clipped!=null) boxes.add(clipped);
            }
        }
        if (extendSegments) mergeFill(boxes);
        return boxes;
    }

    /** Adjacent source columns can fill the same cell; draw their union once. */
    private static void mergeFill(List<RampGeometry.Box> boxes) {
        boolean changed;
        do {
            changed=false;
            for (int i=0;i<boxes.size() && !changed;i++)
                for (int j=i+1;j<boxes.size();j++) {
                    RampGeometry.Box a=boxes.get(i),b=boxes.get(j);
                    boolean sameY=Math.abs(a.minY-b.minY)<1e-8 && Math.abs(a.maxY-b.maxY)<1e-8;
                    boolean sameX=Math.abs(a.minX-b.minX)<1e-8 && Math.abs(a.maxX-b.maxX)<1e-8;
                    boolean sameZ=Math.abs(a.minZ-b.minZ)<1e-8 && Math.abs(a.maxZ-b.maxZ)<1e-8;
                    boolean alongX=sameY && sameZ && a.maxX>=b.minX-1e-8 && b.maxX>=a.minX-1e-8;
                    boolean alongZ=sameY && sameX && a.maxZ>=b.minZ-1e-8 && b.maxZ>=a.minZ-1e-8;
                    if (!alongX && !alongZ) continue;
                    boxes.set(i,new RampGeometry.Box(Math.min(a.minX,b.minX),a.minY,Math.min(a.minZ,b.minZ),
                            Math.max(a.maxX,b.maxX),a.maxY,Math.max(a.maxZ,b.maxZ)));
                    boxes.remove(j); changed=true; break;
                }
        } while (changed);
    }
    /** Map a moving slice back to its source block's top and side texture coordinates. */
    public double[] textureShift(RampGeometry.Box box,double partial) {
        if (travelAxis==RampGeometry.VERTICAL || extendSegments) return new double[]{0,0};
        EnumFacing face=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        EnumFacing side=travelAxis==RampGeometry.LEFT?face.rotateYCCW():face.rotateY();
        double pose=pose(partial);
        for (BlockPos origin:origins) {
            int originRow=origin.getX()*face.getFrontOffsetX()+origin.getZ()*face.getFrontOffsetZ()
                    -(pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row);
            for (int step=0;step<(elevator?1:ControllerPlatform.treadCount(treadPixels));step++) {
                RampGeometry.Box whole=RampGeometry.movingTread(direction(face),origin.getX(),origin.getY(),origin.getZ(),
                        low,high,originRow,length,startOffsetValue(),endOffsetValue(),treadPixels,step,pose,pose,
                        elevator,travelAxis,false,speed==0,interpolation);
                RampGeometry.Box clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());
                if (clipped==null || Math.abs(clipped.minX-box.minX)>1e-7
                        || Math.abs(clipped.maxX-box.maxX)>1e-7
                        || Math.abs(clipped.minY-box.minY)>1e-7
                        || Math.abs(clipped.maxY-box.maxY)>1e-7
                        || Math.abs(clipped.minZ-box.minZ)>1e-7
                        || Math.abs(clipped.maxZ-box.maxZ)>1e-7) continue;
                double offset=ControllerPlatform.offsetPixels(originRow,step,length,treadPixels,
                        startOffsetValue(),endOffsetValue(),pose,elevator,speed==0,interpolation);
                return new double[]{pos.getX()-origin.getX()-side.getFrontOffsetX()*offset,
                        pos.getZ()-origin.getZ()-side.getFrontOffsetZ()*offset};
            }
        }
        return new double[]{0,0};
    }
    public double sideTextureV(AxisAlignedBB box,double localY,double partial) {
        return sideTextureV(new RampGeometry.Box(box.minX,box.minY,box.minZ,
                box.maxX,box.maxY,box.maxZ),localY,partial);
    }
    public double sideTextureV(RampGeometry.Box box,double localY,double partial) {
        if (extendSegments) return 1-localY;
        EnumFacing face=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
        int step=RampGeometry.segmentAtPixels(direction(face),box,treadPixels,elevator);
        double offset=travelAxis==RampGeometry.VERTICAL && !extendSegments
                ?ControllerPlatform.offsetPixels(row,step,length,treadPixels,startOffsetValue(),endOffsetValue(),pose(partial),elevator,speed==0,interpolation):0;
        return ControllerPlatform.sideTextureV(pos.getY(),localY,sourceY,offset);
    }
    private static RampGeometry.Direction direction(EnumFacing face) {
        return RampGeometry.Direction.valueOf(face.name());
    }
    public boolean belongsTo(BlockPos owner) { return controller.equals(owner); }
    public int sourceHousing(RampGeometry.Box box,double partial,EnumFacing face) {
        return sourceMaterial(box,partial).texture(face);
    }

    /** Resolve saved source data once for the six faces of this slice. */
    public SourceMaterial sourceMaterial(RampGeometry.Box box,double partial) {
        if (sourceTileTags.isEmpty()) return SourceMaterial.NONE;
        BlockPos origin=originFor(box,partial);
        NBTTagCompound saved=sourceTileTags.get(origin);
        if (saved==null) saved=sourceTileTags.values().iterator().next();
        if (!saved.hasKey(SaveSchema.Screen.HOUSING_TEXTURE)) return SourceMaterial.NONE;
        int main=ScreenHousingTextures.clamp(saved.getInteger(SaveSchema.Screen.HOUSING_TEXTURE));
        FaceTextures faces=new FaceTextures(saved.getBoolean("FaceTexturesEnabled"),saved.getIntArray("FaceTextures"));
        EnumFacing facing=source.getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
        return new SourceMaterial(main,faces,((int)(180-facing.getHorizontalAngle())/90)&3);
    }

    /** A per-draw snapshot, never retained across edits to the saved source tags. */
    public static final class SourceMaterial {
        private static final SourceMaterial NONE=new SourceMaterial(-1,FaceTextures.DEFAULT,0);
        private final int main,rotation;
        private final FaceTextures faces;
        private SourceMaterial(int main,FaceTextures faces,int rotation) {
            this.main=main;this.faces=faces;this.rotation=rotation;
        }
        public int texture(EnumFacing face) {
            EnumFacing local=face;
            if (local.getAxis()!=EnumFacing.Axis.Y) for (int i=0;i<rotation;i++) local=local.rotateY();
            return faces.texture(local.getIndex(),main);
        }
    }

    private BlockPos originFor(RampGeometry.Box box,double partial) {
        double centerX=pos.getX()+(box.minX+box.maxX)*.5;
        double centerZ=pos.getZ()+(box.minZ+box.maxZ)*.5;
        for (BlockPos origin:origins)
            if (centerX>=origin.getX() && centerX<origin.getX()+1
                    && centerZ>=origin.getZ() && centerZ<origin.getZ()+1)
                return origin;
        return origins.isEmpty()?pos:origins.get(0);
    }

    public static void restoreSourceTile(net.minecraft.world.World world,BlockPos origin,
            NBTTagCompound saved) {
        if (saved==null) return;
        TileEntity tile=world.getTileEntity(origin);
        if (tile==null) return;
        NBTTagCompound copy=saved.copy();
        copy.setInteger("x",origin.getX());
        copy.setInteger("y",origin.getY());
        copy.setInteger("z",origin.getZ());
        tile.readFromNBT(copy);
        tile.markDirty();
        IBlockState state=world.getBlockState(origin);
        world.notifyBlockUpdate(origin,state,state,3);
    }
    public void restore() {
        if (world.getTileEntity(pos)!=this) return;
        List<BlockPos> savedOrigins=origins.isEmpty()
                ?java.util.Collections.singletonList(new BlockPos(pos.getX(),sourceY,pos.getZ())):origins;
        for (BlockPos origin:savedOrigins) {
            TileEntity other=world.getTileEntity(origin);
            if (world.isAirBlock(origin) || (other instanceof TileEntityControlledRamp
                    && ((TileEntityControlledRamp)other).belongsTo(controller))) {
                world.setBlockState(origin,source,3);
                restoreSourceTile(world,origin,sourceTileTags.get(origin));
            }
        }
        if (world.getTileEntity(pos)==this) world.setBlockToAir(pos);
    }
    public void recoverOnEvent() {
        if (world.isRemote || !world.isBlockLoaded(controller)) return;
        TileEntity owner=world.getTileEntity(controller);
        if (!(owner instanceof TileEntityRampController) || !((TileEntityRampController)owner).owns(this)) restore();
        else ((TileEntityRampController)owner).resumeAfterLoad();
    }
    @Override public void onLoad() {
        if (!world.isRemote) {
            ControllerRecovery.register(this);
            world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),1);
        }
    }
    @Override public void invalidate() { ControllerRecovery.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { ControllerRecovery.unregister(this); super.onChunkUnload(); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);tag.setInteger("RampInterpolation",interpolation);
        new RampCellData(sourceY,row,length,drop,segments,duration,low,high,top,
                elevator,open,moving,startPose,startTick,startOffset,endOffset(),treadPixels).write(new NbtPrimitiveData(tag));
        tag.setInteger(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,startHalfSteps);
        tag.setInteger(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,endHalfSteps);
        tag.setInteger(SaveSchema.Ramp.TRAVEL_AXIS,travelAxis);
        tag.setBoolean(SaveSchema.Ramp.EXTEND_SEGMENTS,extendSegments);
        tag.setInteger(SaveSchema.Ramp.SPEED_MODE,speed);
        NBTTagList originList=new NBTTagList();
        for (BlockPos origin:origins) {
            NBTTagCompound entry=new NBTTagCompound();
            entry.setInteger("X",origin.getX()); entry.setInteger("Y",origin.getY()); entry.setInteger("Z",origin.getZ());
            originList.appendTag(entry);
        }
        tag.setTag(SaveSchema.Ramp.ORIGINS,originList);
        NBTTagList tileList=new NBTTagList();
        for (Map.Entry<BlockPos,NBTTagCompound> source:sourceTileTags.entrySet()) {
            NBTTagCompound entry=new NBTTagCompound();
            entry.setInteger("X",source.getKey().getX());
            entry.setInteger("Y",source.getKey().getY());
            entry.setInteger("Z",source.getKey().getZ());
            entry.setTag("Tile",source.getValue().copy());
            tileList.appendTag(entry);
        }
        tag.setTag("RampSourceTiles",tileList);
        tag.setLong(SaveSchema.Ramp.CONTROLLER,controller.toLong());
        tag.setInteger(SaveSchema.Ramp.CONTROLLER_X,controller.getX());
        tag.setInteger(SaveSchema.Ramp.CONTROLLER_Y,controller.getY());
        tag.setInteger(SaveSchema.Ramp.CONTROLLER_Z,controller.getZ());
        tag.setTag(SaveSchema.Ramp.SOURCE,NBTUtil.writeBlockState(new NBTTagCompound(),source));
        tag.setString(SaveSchema.Ramp.SOURCE_STATE,LegacyBlockStates.encode(source));
        // Dynmap reads tile NBT but cannot run this cell's animated Minecraft renderer.
        // Give it the clipped cell geometry and the saved programmable finish.
        StringBuilder mapBoxes=new StringBuilder();
        if (world!=null) for (RampGeometry.Box box:geometry(0)) {
            if (mapBoxes.length()>0) mapBoxes.append(';');
            double[] coords={box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ};
            for (int i=0;i<coords.length;i++) {
                if (i>0) mapBoxes.append(',');
                mapBoxes.append(Math.max(0,Math.min(1,coords[i])));
            }
        }
        tag.setString("DynmapBoxes",mapBoxes.toString());
        int mapTexture=ScreenHousingTextures.LEGACY_COUNT;
        if ((source.getBlock()==com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_BLOCK
                || source.getBlock()==com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_SLAB)
                && !sourceTileTags.isEmpty()) {
            NBTTagCompound saved=sourceTileTags.values().iterator().next();
            mapTexture=ScreenHousingTextures.clamp(saved.getInteger(SaveSchema.Screen.HOUSING_TEXTURE));
            if(mapTexture>=ScreenHousingTextures.LEGACY_COUNT)mapTexture=0;
        }
        tag.setInteger("DynmapTexture",mapTexture);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);interpolation=Math.max(0,Math.min(2,tag.getInteger("RampInterpolation")));
        controller=tag.hasKey(SaveSchema.Ramp.CONTROLLER_X)
                ?new BlockPos(tag.getInteger(SaveSchema.Ramp.CONTROLLER_X),
                        tag.getInteger(SaveSchema.Ramp.CONTROLLER_Y),
                        tag.getInteger(SaveSchema.Ramp.CONTROLLER_Z))
                :BlockPos.fromLong(tag.getLong(SaveSchema.Ramp.CONTROLLER));
        source=tag.hasKey(SaveSchema.Ramp.SOURCE_STATE)
                ?LegacyBlockStates.decode(tag.getString(SaveSchema.Ramp.SOURCE_STATE)):null;
        if (source==null) source=NBTUtil.readBlockState(tag.getCompoundTag(SaveSchema.Ramp.SOURCE));
        RampCellData data=RampCellData.read(new NbtPrimitiveData(tag));
        sourceY=data.sourceY; row=data.row; length=data.length; drop=data.drop;
        segments=data.segments; duration=data.duration; low=data.low; high=data.high;
        top=data.top; elevator=data.elevator; open=data.open; moving=data.moving;
        startPose=data.startPose; startTick=data.startTick;
        treadPixels=data.treadPixels; segments=ControllerPlatform.treadCount(treadPixels);
        int savedTravel=tag.getInteger(SaveSchema.Ramp.TRAVEL_AXIS);
        startOffset=savedTravel==RampGeometry.LEFT?-data.startOffset:data.startOffset;
        int signedEnd=savedTravel==RampGeometry.LEFT?-data.endOffset:data.endOffset;
        drop=Math.abs(signedEnd); top=signedEnd<0;
        startHalfSteps=tag.hasKey(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,3)
                ?tag.getInteger(SaveSchema.Ramp.START_OFFSET_HALF_STEPS):startOffset*2;
        endHalfSteps=tag.hasKey(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,3)
                ?tag.getInteger(SaveSchema.Ramp.END_OFFSET_HALF_STEPS):signedEnd*2;
        if (savedTravel==RampGeometry.LEFT) {
            if (tag.hasKey(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,3)) startHalfSteps=-startHalfSteps;
            if (tag.hasKey(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,3)) endHalfSteps=-endHalfSteps;
        }
        startHalfSteps=Math.max(-ControllerPlatform.MAX_OFFSET_HALF_STEPS,
                Math.min(ControllerPlatform.MAX_OFFSET_HALF_STEPS,startHalfSteps));
        endHalfSteps=Math.max(-ControllerPlatform.MAX_OFFSET_HALF_STEPS,
                Math.min(ControllerPlatform.MAX_OFFSET_HALF_STEPS,endHalfSteps));
        startOffset=startHalfSteps/2; drop=(Math.abs(endHalfSteps)+1)/2; top=endHalfSteps<0;
        travelAxis=savedTravel==RampGeometry.LEFT?RampGeometry.RIGHT:savedTravel;
        extendSegments=tag.getBoolean(SaveSchema.Ramp.EXTEND_SEGMENTS);
        speed=tag.hasKey(SaveSchema.Ramp.SPEED_MODE)?tag.getInteger(SaveSchema.Ramp.SPEED_MODE):1;
        if (speed<0 || speed>2) speed=1;
        origins.clear();
        NBTTagList originList=tag.getTagList(SaveSchema.Ramp.ORIGINS,10);
        for (int i=0;i<Math.min(128,originList.tagCount());i++) {
            NBTTagCompound entry=originList.getCompoundTagAt(i);
            origins.add(new BlockPos(entry.getInteger("X"),entry.getInteger("Y"),entry.getInteger("Z")));
        }
        if (origins.isEmpty()) origins.add(new BlockPos(pos.getX(),sourceY,pos.getZ()));
        sourceTileTags.clear();
        NBTTagList tileList=tag.getTagList("RampSourceTiles",10);
        for (int i=0;i<Math.min(128,tileList.tagCount());i++) {
            NBTTagCompound entry=tileList.getCompoundTagAt(i);
            if (entry.hasKey("Tile",10)) sourceTileTags.put(new BlockPos(
                    entry.getInteger("X"),entry.getInteger("Y"),entry.getInteger("Z")),
                    entry.getCompoundTag("Tile").copy());
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos,0,getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
