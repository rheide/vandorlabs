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
import com.vandorlabs.compat.RampStairs;
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
    @Override public boolean shouldRenderInPass(int pass) {
        return pass==(com.vandorlabs.compat.RampMaterials.isFullGlass(source)?1:0);
    }

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
    public int escalator;
    public boolean moat,verticalMoatSources;
    public int moatGeometryRevision=2;
    public boolean usesNativeMoatGeometry(){return moat&&verticalMoatSources&&moatGeometryRevision>=1;}
    public boolean beltRunning;
    public boolean vehicleFrozen;
    public double beltDistance;
    public long beltTick;
    private double cachedBeltDistance=Double.NaN;
    private List<RampGeometry.Box> cachedBeltGeometry;
    private final Map<RampGeometry.Box,Double> beltTops=new java.util.IdentityHashMap<>();
    private final Map<RampGeometry.Box,BlockPos> beltMaterials=new java.util.IdentityHashMap<>();
    private final Map<RampGeometry.Box,double[]> moatTextureOffsets=new java.util.IdentityHashMap<>();
    public double beltDistance(double partial) {
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld)return beltDistance;
        if(world!=null && !world.isRemote) {
            TileEntity owner=world.getTileEntity(controller);
            if(owner instanceof TileEntityRampController)return ((TileEntityRampController)owner).beltDistance(0);
            return beltDistance;
        }
        double sign=(endOffsetValue()>=0?1:-1)*(escalator==1?1:-1);
        return beltDistance+(beltRunning?(world.getTotalWorldTime()-beltTick+partial)*com.vandorlabs.ramp.EscalatorGeometry.rate(speed)*sign:0);
    }
    public boolean extendSegments;
    public final List<BlockPos> origins=new ArrayList<>();
    public final Map<BlockPos,NBTTagCompound> sourceTileTags=new LinkedHashMap<>();
    public double low,high=1;
    public boolean top=true,elevator;
    private boolean open,moving;
    private double startPose;
    private long startTick;
    private GeometryKey cachedGeometryKey;
    private List<RampGeometry.Box> cachedGeometry,boxedGeometry;
    private List<AxisAlignedBB> cachedBoxes;

    /** Saved settings can be edited directly; compare them rather than relying on notifications. */
    private static final class GeometryKey {
        final BlockPos pos;final EnumFacing facing;final double pose,low,high;
        final int sourceY,row,length,start,end,tread,axis,interpolation,moatRevision;
        final boolean elevator,extend,instant,moat,verticalMoatSources;final List<BlockPos> origins;
        GeometryKey(TileEntityControlledRamp tile,EnumFacing facing,double pose){
            moat=tile.moat;verticalMoatSources=tile.verticalMoatSources;moatRevision=tile.moatGeometryRevision;pos=tile.pos;this.facing=facing;this.pose=pose;low=tile.low;high=tile.high;
            sourceY=tile.sourceY;row=tile.row;length=tile.length;start=tile.startHalfSteps;end=tile.endHalfSteps;tread=tile.treadPixels;axis=tile.travelAxis;interpolation=tile.interpolation;
            elevator=tile.elevator;extend=tile.extendSegments;instant=tile.speed==0;origins=new ArrayList<>();for(BlockPos origin:tile.origins)origins.add(origin.toImmutable());
        }
        boolean matches(TileEntityControlledRamp tile,EnumFacing facing,double pose){
            return this.facing==facing && this.pose==pose && pos.equals(tile.pos) && low==tile.low && high==tile.high
                    && sourceY==tile.sourceY && row==tile.row && length==tile.length && start==tile.startHalfSteps && end==tile.endHalfSteps
                    && tread==tile.treadPixels && axis==tile.travelAxis && interpolation==tile.interpolation && elevator==tile.elevator
                    && moatRevision==tile.moatGeometryRevision && extend==tile.extendSegments && moat==tile.moat && verticalMoatSources==tile.verticalMoatSources && instant==(tile.speed==0) && sameOrigins(tile.origins);
        }
        private boolean sameOrigins(List<BlockPos> current){
            if(origins.size()!=current.size())return false;
            for(int i=0;i<origins.size();i++)if(!origins.get(i).equals(current.get(i)))return false;
            return true;
        }
    }

    public boolean isOpen() { return open; }
    public boolean isMoving() { return moving || beltRunning; }
    public double pose(double partial) {
        return moving?ControllerPlatform.pose(startPose,open,world.getTotalWorldTime()-startTick+partial,duration):startPose;
    }
    public void move(boolean target,double from,long tick,int ticks,boolean animate) {
        cachedBeltDistance=Double.NaN;
        open=target; startPose=from; startTick=tick; duration=ticks; moving=animate;
        markDirty();
        IBlockState s=world.getBlockState(pos);
        world.notifyBlockUpdate(pos,s,s,2);
    }
    public List<AxisAlignedBB> boxes(double partial) {
        List<RampGeometry.Box> geometry=geometry(partial);
        if(geometry==boxedGeometry && cachedBoxes!=null)return cachedBoxes;
        List<AxisAlignedBB> boxes=new ArrayList<>();
        for (RampGeometry.Box box:geometry)
            boxes.add(new AxisAlignedBB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        boxedGeometry=geometry;cachedBoxes=java.util.Collections.unmodifiableList(boxes);return cachedBoxes;
    }
    public List<RampGeometry.Box> geometry(double partial) {
        IBlockState state=world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockControlledRamp)) return java.util.Collections.emptyList();
        EnumFacing face=state.getValue(BlockVandorDirectional.FACING);
        if(escalator!=0 && !moving && pose(partial)>=1) {
            double distance=beltDistance(partial);
            if(distance==cachedBeltDistance && cachedBeltGeometry!=null)return cachedBeltGeometry;
            List<RampGeometry.Box> boxes=new ArrayList<>();
            beltMaterials.clear();beltTops.clear();
            for(BlockPos origin:origins) {
                // Only the source lane matching this cell can intersect it.
                if(face.getAxis()==EnumFacing.Axis.X?origin.getZ()!=pos.getZ():origin.getX()!=pos.getX())continue;
                int originRow=origin.getX()*face.getFrontOffsetX()+origin.getZ()*face.getFrontOffsetZ()
                        -(pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row);
                double originStart=com.vandorlabs.ramp.EscalatorGeometry.phase(originRow+distance,length*16);
                if(!(originStart<row+1 && originStart+1>row)
                        && !(originStart-length<row+1 && originStart+1-length>row))continue;
                List<RampGeometry.Box> moved=RampStairs.supported(source)
                        ?RampStairs.escalator(world,sourceState(origin),direction(face),origin,originRow,length,treadPixels,endOffsetValue(),distance,interpolation)
                        :com.vandorlabs.ramp.EscalatorGeometry.treads(direction(face),origin.getX(),origin.getY(),origin.getZ(),low,high,originRow,length,treadPixels,endOffsetValue(),distance,interpolation);
                for(RampGeometry.Box whole:moved) {
                    RampGeometry.Box clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());
                    if(clipped!=null){boxes.add(clipped);beltMaterials.put(clipped,origin);beltTops.put(clipped,whole.maxY-pos.getY());}
                }
            }
            cachedBeltDistance=distance;cachedBeltGeometry=boxes;
            return boxes;
        }
        double pose=pose(partial);
        if(!moving && cachedGeometryKey!=null && cachedGeometryKey.matches(this,face,pose))return cachedGeometry;
        List<RampGeometry.Box> result=geometryUncached(face,pose);
        if(!moving){cachedGeometryKey=new GeometryKey(this,face,pose);cachedGeometry=java.util.Collections.unmodifiableList(result);return cachedGeometry;}
        return result;
    }
    private List<RampGeometry.Box> geometryUncached(EnumFacing face,double pose){
        if(usesNativeMoatGeometry()){
            beltMaterials.clear();moatTextureOffsets.clear();List<RampGeometry.Box> result=new ArrayList<>();
            for(BlockPos origin:origins){List<RampGeometry.Box> nativeShapes=RampStairs.supported(source)?RampStairs.boxes(world,sourceState(origin)):java.util.Collections.singletonList(new RampGeometry.Box(0,low,0,1,high,1));
                for(RampGeometry.Box shape:nativeShapes)for(RampGeometry.Box piece:com.vandorlabs.ramp.MoatGeometry.pieces(direction(face),shape,treadPixels)){
                    int step=com.vandorlabs.ramp.MoatGeometry.step(direction(face),piece,treadPixels);RampGeometry.Box nativeBox=com.vandorlabs.ramp.MoatGeometry.world(piece,origin.getX(),origin.getY(),origin.getZ());
                    RampGeometry.Box whole=com.vandorlabs.ramp.MoatGeometry.transform(direction(face),sourceY,length,treadPixels,startOffsetValue(),endOffsetValue(),pose,speed==0,interpolation,origin.getY(),step,nativeBox,moatGeometryRevision==1?nativeBox:com.vandorlabs.ramp.MoatGeometry.world(shape,origin.getX(),origin.getY(),origin.getZ())),clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());
                    if(clipped!=null){result.add(clipped);beltMaterials.put(clipped,origin);moatTextureOffsets.put(clipped,com.vandorlabs.ramp.MoatGeometry.point(direction(face),sourceY,length,treadPixels,startOffsetValue(),endOffsetValue(),pose,speed==0,interpolation,origin.getY(),step,0,0,0));}
                }
            }
            return result;
        }
        if(RampStairs.supported(source))return stairGeometry(face,pose);
        if(moat){beltMaterials.clear();List<RampGeometry.Box> boxes=new ArrayList<>();int hinge=pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row;
            for(BlockPos origin:origins)for(RampGeometry.Box whole:verticalMoatSources?com.vandorlabs.ramp.RigidMoatGeometry.boxes(direction(face),hinge,sourceY,length,treadPixels,low,high,startOffsetValue(),endOffsetValue(),pose,speed==0,origin.getX(),origin.getY(),origin.getZ()):com.vandorlabs.ramp.LegacyMoatGeometry.boxes(direction(face),hinge,sourceY,low,high,startOffsetValue(),endOffsetValue(),pose,speed==0,origin.getX(),origin.getY(),origin.getZ())){RampGeometry.Box clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());if(clipped!=null){boxes.add(clipped);beltMaterials.put(clipped,origin);}}
            return boxes;}
        if (origins.isEmpty() && travelAxis==RampGeometry.VERTICAL && !extendSegments)
            return RampGeometry.boxesPixels(direction(face),pos.getY(),sourceY,low,high,
                    row,length,startOffsetValue(),endOffsetValue(),treadPixels,pose,elevator,speed==0,interpolation);
        List<RampGeometry.Box> boxes=new ArrayList<>();
        for (BlockPos origin:origins) {
            int originRow=origin.getX()*face.getFrontOffsetX()+origin.getZ()*face.getFrontOffsetZ()
                    -(pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row);
            for (int step=0;step<(elevator?1:ControllerPlatform.treadCount(treadPixels));step++) {
                RampGeometry.Box whole=RampGeometry.movingTread(direction(face),origin.getX(),origin.getY(),origin.getZ(),
                        low,high,originRow,length,startOffsetValue(),endOffsetValue(),treadPixels,step,pose,pose,
                        elevator,travelAxis,extendSegments,speed==0,interpolation);
                RampGeometry.Box clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());
                if (clipped!=null) boxes.add(clipped);
            }
        }
        if (extendSegments) mergeFill(boxes);
        return boxes;
    }
    private List<RampGeometry.Box> stairGeometry(EnumFacing face,double pose){
        beltMaterials.clear();List<RampGeometry.Box> result=new ArrayList<>();
        int hinge=pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row;
        for(BlockPos origin:origins){
            int originRow=origin.getX()*face.getFrontOffsetX()+origin.getZ()*face.getFrontOffsetZ()-hinge;
            List<RampGeometry.Box> moved=moat
                    ?(verticalMoatSources?RampStairs.rigidMoat(world,sourceState(origin),direction(face),hinge,sourceY,length,treadPixels,startOffsetValue(),endOffsetValue(),pose,speed==0,origin):RampStairs.legacyMoat(world,sourceState(origin),direction(face),hinge,sourceY,high,startOffsetValue(),endOffsetValue(),pose,speed==0,origin))
                    :RampStairs.treads(world,sourceState(origin),direction(face),origin,originRow,length,startOffsetValue(),endOffsetValue(),treadPixels,pose,elevator,travelAxis,extendSegments,speed==0,interpolation);
            for(RampGeometry.Box whole:moved){RampGeometry.Box clipped=RampGeometry.clip(whole,pos.getX(),pos.getY(),pos.getZ());if(clipped!=null){result.add(clipped);beltMaterials.put(clipped,origin);}}
        }
        return result;
    }
    public IBlockState sourceState(BlockPos origin){return RampStairs.state(source,sourceTileTags.get(origin));}

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
        if(usesNativeMoatGeometry()){BlockPos origin=originFor(box,partial);double[] d=moatTextureOffsets.get(box);return d==null?new double[]{0,0}:new double[]{pos.getX()-origin.getX()-d[0],pos.getZ()-origin.getZ()-d[2]};}
        if(escalator!=0 && !moving && pose(partial)>=1) {
            BlockPos origin=originFor(box,partial);
            EnumFacing face=world.getBlockState(pos).getValue(BlockVandorDirectional.FACING);
            double along=(pos.getX()+(box.minX+box.maxX)*.5)*face.getFrontOffsetX()
                    +(pos.getZ()+(box.minZ+box.maxZ)*.5)*face.getFrontOffsetZ();
            // Reverse-facing lanes measure their start at the far edge of the cell.
            int min=pos.getX()*face.getFrontOffsetX()+pos.getZ()*face.getFrontOffsetZ()-row;
            double coordinate=along-min+(face==EnumFacing.NORTH || face==EnumFacing.WEST?1:0);
            double sourceCoordinate=com.vandorlabs.ramp.EscalatorGeometry.phase(coordinate-beltDistance(partial),length*16);
            double displacement=coordinate-sourceCoordinate;
            return new double[]{pos.getX()-origin.getX()-face.getFrontOffsetX()*displacement,
                    pos.getZ()-origin.getZ()-face.getFrontOffsetZ()*displacement};
        }
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
        if(usesNativeMoatGeometry()){double[] d=moatTextureOffsets.get(box);return d==null?1-localY:ControllerPlatform.sideTextureV(pos.getY(),localY,originFor(box,partial).getY(),d[1]);}
        if(escalator!=0 && !moving && pose(partial)>=1)return 1-(localY-beltTops.getOrDefault(box,box.maxY)+high);
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
        return sourceMaterial(originFor(box,partial));
    }
    public SourceMaterial sourceMaterial(BlockPos origin) {
        if (sourceTileTags.isEmpty() || !source.getPropertyKeys().contains(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING)
                && !(source.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableStairs)) return SourceMaterial.NONE;
        NBTTagCompound saved=sourceTileTags.get(origin);
        if (saved==null) saved=sourceTileTags.values().iterator().next();
        if (!saved.hasKey(SaveSchema.Screen.HOUSING_TEXTURE)) return SourceMaterial.NONE;
        int main=ScreenHousingTextures.clamp(saved.getInteger(SaveSchema.Screen.HOUSING_TEXTURE));
        FaceTextures faces=new FaceTextures(saved.getBoolean("FaceTexturesEnabled"),saved.getIntArray("FaceTextures"));
        EnumFacing facing=source.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableStairs
                ?sourceState(origin).getValue(net.minecraft.block.BlockStairs.FACING):source.getValue(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING);
        int layout=saved.hasKey("MainTextureLayout",3) && com.vandorlabs.render.TextureLayoutMode.valid(saved.getInteger("MainTextureLayout"))?saved.getInteger("MainTextureLayout"):-1;
        if(source.getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableStairs)
            return new SourceMaterial(main,faces,(facing.getOpposite().getHorizontalIndex()+2)&3,
                    saved.hasKey("SideTexture",3)?saved.getInteger("SideTexture"):-1,true,layout<0?(saved.getBoolean("SlabTileSides")?1:0):layout);
        return new SourceMaterial(main,faces,((int)(180-facing.getHorizontalAngle())/90)&3,-1,false,layout);
    }

    /** A per-draw snapshot, never retained across edits to the saved source tags. */
    public static final class SourceMaterial {
        private static final SourceMaterial NONE=new SourceMaterial(-1,FaceTextures.DEFAULT,0);
        private final int main,rotation;
        private final int side,layout;private final boolean stairs;
        private final FaceTextures faces;
        private SourceMaterial(int main,FaceTextures faces,int rotation) {
            this(main,faces,rotation,-1,false,-1);
        }
        private SourceMaterial(int main,FaceTextures faces,int rotation,int side,boolean stairs,int layout){
            this.main=main;this.faces=faces;this.rotation=rotation;this.side=side;this.stairs=stairs;this.layout=layout;
        }
        public boolean stretchSides(){return stairs&&layout==0;}
        public int textureLayout(){return layout;}
        public int texture(EnumFacing face) {
            EnumFacing local=face;
            if (local.getAxis()!=EnumFacing.Axis.Y) for (int i=0;i<rotation;i++) local=stairs?local.rotateYCCW():local.rotateY();
            return faces.texture(local.getIndex(),stairs&&local.getAxis()!=EnumFacing.Axis.Y&&side>=0?ScreenHousingTextures.clamp(side):main);
        }
    }

    private BlockPos originFor(RampGeometry.Box box,double partial) {
        BlockPos material=beltMaterials.get(box);
        if(material!=null)return material;
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
                world.setBlockState(origin,sourceState(origin),3);
                restoreSourceTile(world,origin,sourceTileTags.get(origin));
            }
        }
        if (world.getTileEntity(pos)==this) world.setBlockToAir(pos);
    }
    public void recoverOnEvent() {
        if(world==null||isInvalid()||world.getTileEntity(pos)!=this)return;
        if(vehicleFrozen || world instanceof com.vandorlabs.vehicle.VehicleWorld || com.vandorlabs.vehicle.VehicleTransfer.isMutating(world))return;
        if (world.isRemote || !world.isBlockLoaded(controller)) return;
        TileEntity owner=world.getTileEntity(controller);
        if (!(owner instanceof TileEntityRampController) || !((TileEntityRampController)owner).owns(this)) restore();
        else ((TileEntityRampController)owner).resumeAfterLoad();
    }
    @Override public void onLoad() {
        if (!world.isRemote && !vehicleFrozen && !(world instanceof com.vandorlabs.vehicle.VehicleWorld)) {
            ControllerRecovery.register(this);
            world.scheduleUpdate(pos,world.getBlockState(pos).getBlock(),1);
        }
    }
    @Override public void invalidate() { ControllerRecovery.unregister(this); super.invalidate(); }
    @Override public void onChunkUnload() { ControllerRecovery.unregister(this); super.onChunkUnload(); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setLong(com.vandorlabs.ramp.RampCopyData.ORIGIN,pos.toLong());
        if(vehicleFrozen)tag.setBoolean("VehicleRampFrozen",true);
        super.writeToNBT(tag);tag.setInteger("MoatGeometryRevision",moatGeometryRevision);tag.setBoolean("MoatMode",moat);tag.setBoolean("MoatVerticalSources",verticalMoatSources);tag.setInteger("RampInterpolation",interpolation);
        tag.setInteger("EscalatorMode",escalator);tag.setBoolean("EscalatorRunning",beltRunning);
        tag.setDouble("EscalatorDistance",beltDistance);tag.setLong("EscalatorTick",beltTick);
        new RampCellData(sourceY,row,length,drop,segments,duration,low,high,top,
                elevator,open,moving,startPose,startTick,startOffset,endOffset(),treadPixels).write(new NbtPrimitiveData(tag));
        // A rotating outer corner reaches one cell beyond the original deck.
        if(moat)tag.setInteger(SaveSchema.Ramp.ROW,row);
        tag.setInteger(SaveSchema.Ramp.START_OFFSET_HALF_STEPS,startHalfSteps);
        tag.setInteger(SaveSchema.Ramp.END_OFFSET_HALF_STEPS,endHalfSteps);
        tag.setInteger(SaveSchema.Ramp.TRAVEL_AXIS,travelAxis);
        tag.setBoolean(SaveSchema.Ramp.EXTEND_SEGMENTS,extendSegments);
        tag.setInteger(SaveSchema.Ramp.SPEED_MODE,speed);
        NBTTagList originList=new NBTTagList();
        // Match the reader/recovery fallback without changing live unformed geometry.
        List<BlockPos> savedOrigins=origins.isEmpty()?java.util.Collections.singletonList(new BlockPos(pos.getX(),sourceY,pos.getZ())):origins;
        for (BlockPos origin:savedOrigins) {
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
        List<RampGeometry.Box> mapGeometry=java.util.Collections.emptyList();
        if(world!=null){
            if(world.getBlockState(pos).getBlock() instanceof BlockControlledRamp)mapGeometry=geometry(0);
            else if(world instanceof com.vandorlabs.vehicle.VehicleWorld){
                // Endpoint parts are serialized before their new states enter the staging view.
                // Derive their saved map geometry from the same configured controller facing.
                TileEntity owner=world.getTileEntity(controller);
                if(owner instanceof TileEntityRampController)mapGeometry=geometryUncached(((TileEntityRampController)owner).rampDirection(),pose(0));
            }
        }
        for (RampGeometry.Box box:mapGeometry) {
            if (mapBoxes.length()>0) mapBoxes.append(';');
            double[] coords={box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ};
            for (int i=0;i<coords.length;i++) {
                if (i>0) mapBoxes.append(',');
                // Map metadata uses block-local micro-units. Absolute-world subtraction
                // otherwise changes the last double digits when a snapshot is rebased.
                mapBoxes.append(Math.round(Math.max(0,Math.min(1,coords[i]))*1000000D)/1000000D);
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
        tag=com.vandorlabs.ramp.RampCopyData.relocated(tag);
        moatGeometryRevision=Math.max(0,Math.min(2,tag.getInteger("MoatGeometryRevision")));vehicleFrozen=tag.getBoolean("VehicleRampFrozen");moat=tag.getBoolean("MoatMode");verticalMoatSources=tag.getBoolean("MoatVerticalSources");
        super.readFromNBT(tag);cachedBeltDistance=Double.NaN;beltMaterials.clear();
        escalator=Math.max(0,Math.min(2,tag.getInteger("EscalatorMode")));
        beltRunning=tag.getBoolean("EscalatorRunning");beltDistance=tag.getDouble("EscalatorDistance");
        beltTick=tag.getLong("EscalatorTick");
        if(!Double.isFinite(beltDistance))beltDistance=0;
        interpolation=Math.max(0,Math.min(2,tag.getInteger("RampInterpolation")));
        controller=tag.hasKey(SaveSchema.Ramp.CONTROLLER_X)
                ?new BlockPos(tag.getInteger(SaveSchema.Ramp.CONTROLLER_X),
                        tag.getInteger(SaveSchema.Ramp.CONTROLLER_Y),
                        tag.getInteger(SaveSchema.Ramp.CONTROLLER_Z))
                :BlockPos.fromLong(tag.getLong(SaveSchema.Ramp.CONTROLLER));
        source=tag.hasKey(SaveSchema.Ramp.SOURCE_STATE)
                ?LegacyBlockStates.decode(tag.getString(SaveSchema.Ramp.SOURCE_STATE)):null;
        if (source==null) source=NBTUtil.readBlockState(tag.getCompoundTag(SaveSchema.Ramp.SOURCE));
        RampCellData data=RampCellData.read(new NbtPrimitiveData(tag));
        sourceY=data.sourceY; row=moat&&tag.hasKey(SaveSchema.Ramp.ROW,3)?Math.max(-64,Math.min(64,tag.getInteger(SaveSchema.Ramp.ROW))):data.row; length=data.length; drop=data.drop;
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
