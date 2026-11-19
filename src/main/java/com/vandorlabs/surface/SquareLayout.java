package com.vandorlabs.surface;

import com.vandorlabs.turntable.TurntableGeometry;

import com.vandorlabs.blocks.BlockTurntableSurface;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.*;

/** Complete loaded component; discovery never binds or changes a surface cell. */
public class SquareLayout {
    public static final int MAX_SIZE=32,MAX_CELLS=MAX_SIZE*MAX_SIZE;
    public final EnumFacing normal;
    public final TurntableGeometry.Face basis;
    public final List<BlockPos> cells;
    public final BlockPos root;
    public final int size,width,height,minU,minV,plane;
    public final Vec3d pivot;
    protected SquareLayout(Collection<BlockPos> members,EnumFacing normal){this(members,normal,false);}
    private SquareLayout(Collection<BlockPos> members,EnumFacing normal,boolean rectangle){
        this.normal=normal;basis=TurntableGeometry.Face.valueOf(normal.name());
        List<BlockPos> ordered=new ArrayList<>(members);Collections.sort(ordered);cells=Collections.unmodifiableList(ordered);root=ordered.isEmpty()?BlockPos.ORIGIN:ordered.get(0);
        List<com.vandorlabs.surface.core.SurfaceLayout.Cell> points=new ArrayList<>();for(BlockPos pos:ordered)points.add(new com.vandorlabs.surface.core.SurfaceLayout.Cell(pos.getX(),pos.getY(),pos.getZ()));
        com.vandorlabs.surface.core.SurfaceLayout geometry=new com.vandorlabs.surface.core.SurfaceLayout(points,basis,rectangle);
        width=geometry.width;height=geometry.height;size=width;minU=geometry.minU;minV=geometry.minV;plane=geometry.plane;
        pivot=new Vec3d(geometry.pivotX,geometry.pivotY,geometry.pivotZ);
    }
    public SquareLayout reversed(){return new SquareLayout(cells,normal.getOpposite(),width!=height);}
    public BlockPos cell(int u,int v,int depth){
        double[] p=basis.point(minU+u,minV+v,plane);
        return new BlockPos(p[0],p[1],p[2]).offset(normal,depth);
    }
    public static SquareLayout scan(World world,BlockPos seed){return scan(world,seed,p->true);}
    public static SquareLayout scan(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){
        return new SquareLayout(connected(world,seed,allowed),readState(world,seed).getValue(BlockTurntableSurface.FACING));
    }
    public static SquareLayout rectangleAnyArtwork(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){return new SquareLayout(connectedAnyArtwork(world,seed,allowed),readState(world,seed).getValue(BlockTurntableSurface.FACING),true);}
    public static SquareLayout rectangle(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){return new SquareLayout(connected(world,seed,allowed),readState(world,seed).getValue(BlockTurntableSurface.FACING),true);}
    public static SquareLayout rectangle(Collection<BlockPos> cells,EnumFacing normal){return new SquareLayout(cells,normal,true);}
    public static SquareLayout of(Collection<BlockPos> members,EnumFacing normal){return new SquareLayout(members,normal);}
    public static SquareLayout single(BlockPos pos,EnumFacing normal){return new SquareLayout(Collections.singletonList(pos),normal);}
    private static final boolean TRACK_DISCOVERY=Boolean.getBoolean("vandorlabs.copySurfaceChecksOnly");
    private static long discoveryScans,discoveryReads;
    public static long discoveryScans(){return discoveryScans;}
    public static long discoveryReads(){return discoveryReads;}
    private static IBlockState readState(World world,BlockPos pos){if(TRACK_DISCOVERY)discoveryReads++;return world.getBlockState(pos);}
    private static net.minecraft.tileentity.TileEntity readTile(World world,BlockPos pos){if(TRACK_DISCOVERY)discoveryReads++;return world.getTileEntity(pos);}
    private static boolean readLoaded(World world,BlockPos pos){if(TRACK_DISCOVERY)discoveryReads++;return world.isBlockLoaded(pos);}
    /** Copy ownership topology ignores artwork; drawing and Turntable discovery do not. */
    public static java.util.Set<BlockPos> connectedAnyArtwork(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){return connected(world,seed,allowed,false);}
    public static java.util.Set<BlockPos> connected(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){return connected(world,seed,allowed,true);}
    private static java.util.Set<BlockPos> connected(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed,boolean matchingArtwork){
        if(TRACK_DISCOVERY)discoveryScans++;
        final com.vandorlabs.surface.core.SurfaceDiscovery.Access<BlockPos> access=new com.vandorlabs.surface.core.SurfaceDiscovery.Access<BlockPos>(){
            private com.vandorlabs.tiles.TileEntityTurntableSurface seedAppearance;
            private BlockPos current;private IBlockState state;private net.minecraft.tileentity.TileEntity tile;private boolean tileRead;
            private IBlockState state(BlockPos cell){if(!cell.equals(current)){current=cell;state=readState(world,cell);tile=null;tileRead=false;}return state;}
            private net.minecraft.tileentity.TileEntity tile(BlockPos cell){state(cell);if(!tileRead){tile=readTile(world,cell);tileRead=true;}return tile;}
            public boolean loaded(BlockPos cell){return readLoaded(world,cell);}
            public boolean surface(BlockPos cell){return state(cell).getBlock() instanceof BlockTurntableSurface;}
            public boolean hasTile(BlockPos cell){return tile(cell) instanceof com.vandorlabs.tiles.TileEntityTurntableSurface;}
            public TurntableGeometry.Face face(BlockPos cell){return TurntableGeometry.Face.valueOf(state(cell).getValue(BlockTurntableSurface.FACING).name());}
            public boolean sameArtwork(BlockPos seedCell,BlockPos cell){if(seedAppearance==null)seedAppearance=(com.vandorlabs.tiles.TileEntityTurntableSurface)tile(seedCell);return seedAppearance.sameArtwork(tile(cell));}
            public BlockPos offset(BlockPos cell,int x,int y,int z){return cell.add(x,y,z);}
        };
        return com.vandorlabs.surface.core.SurfaceDiscovery.connected(access,seed.toImmutable(),allowed,matchingArtwork);
    }

    public static final double DISC_RADIUS=TurntableGeometry.DISC_RADIUS;
    public boolean onDisc(double x,double y,double z){return TurntableGeometry.onDisc(x-pivot.x,y-pivot.y,z-pivot.z,basis,size);}
    public boolean loaded(World world){for(BlockPos cell:cells)if(!world.isBlockLoaded(cell))return false;return true;}
    public NBTTagCompound write(){
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Normal",normal.getIndex());
        NBTTagList positions=new NBTTagList();for(BlockPos p:cells)positions.appendTag(new NBTTagLong(p.toLong()));
        tag.setTag("Cells",positions);return tag;
    }
    public static SquareLayout read(NBTTagCompound tag){return read(tag,false);}
    public static SquareLayout readRectangle(NBTTagCompound tag){return read(tag,true);}
    private static SquareLayout read(NBTTagCompound tag,boolean rectangle){
        int face=tag.getInteger("Normal");NBTTagList positions=tag.getTagList("Cells",4);
        if(face<0 || face>5 || positions.tagCount()<1 || positions.tagCount()>MAX_CELLS)throw new IllegalArgumentException("Invalid turntable surface data");
        Set<BlockPos> members=new HashSet<>();for(int i=0;i<positions.tagCount();i++)if(!members.add(BlockPos.fromLong(((NBTTagLong)positions.get(i)).getLong())))throw new IllegalArgumentException("Duplicate surface cell");
        return new SquareLayout(members,EnumFacing.getFront(face),rectangle);
    }
}
