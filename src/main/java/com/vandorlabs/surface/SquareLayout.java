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
    public final EnumFacing normal;
    public final TurntableGeometry.Face basis;
    public final List<BlockPos> cells;
    public final BlockPos root;
    public final int size,minU,minV,plane;
    public final Vec3d pivot;
    protected SquareLayout(Collection<BlockPos> members,EnumFacing normal){
        this.normal=normal;basis=TurntableGeometry.Face.valueOf(normal.name());
        List<BlockPos> ordered=new ArrayList<>(members);Collections.sort(ordered);cells=Collections.unmodifiableList(ordered);root=ordered.get(0);
        int lowU=Integer.MAX_VALUE,lowV=lowU,highU=Integer.MIN_VALUE,highV=highU;
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        plane=basis.plane(root.getX(),root.getY(),root.getZ());
        for(BlockPos p:ordered){
            int u=basis.u(p.getX(),p.getY(),p.getZ()),v=basis.v(p.getX(),p.getY(),p.getZ());
            if(basis.plane(p.getX(),p.getY(),p.getZ())!=plane)throw new IllegalArgumentException("Programmable Surfaces must lie in one plane");
            lowU=Math.min(lowU,u);highU=Math.max(highU,u);lowV=Math.min(lowV,v);highV=Math.max(highV,v);
            minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());
        }
        if(!TurntableGeometry.square(lowU,lowV,highU,highV,ordered.size()))throw new IllegalArgumentException(
                "Surface must be a filled square (found "+(highU-lowU+1)+" x "+(highV-lowV+1)+", "+ordered.size()+" cells; maximum 16 x 16)");
        size=highU-lowU+1;minU=lowU;minV=lowV;
        pivot=new Vec3d((minX+maxX+1D)/2+normal.getFrontOffsetX()*.5,
                (minY+maxY+1D)/2+normal.getFrontOffsetY()*.5,(minZ+maxZ+1D)/2+normal.getFrontOffsetZ()*.5);
    }
    public SquareLayout reversed(){return new SquareLayout(cells,normal.getOpposite());}
    public BlockPos cell(int u,int v,int depth){
        double[] p=basis.point(minU+u,minV+v,plane);
        return new BlockPos(p[0],p[1],p[2]).offset(normal,depth);
    }
    public static SquareLayout scan(World world,BlockPos seed){return scan(world,seed,p->true);}
    public static SquareLayout scan(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){
        return new SquareLayout(connected(world,seed,allowed),world.getBlockState(seed).getValue(BlockTurntableSurface.FACING));
    }
    public static SquareLayout of(Collection<BlockPos> members,EnumFacing normal){return new SquareLayout(members,normal);}
    public static SquareLayout single(BlockPos pos,EnumFacing normal){return new SquareLayout(Collections.singletonList(pos),normal);}
    public static java.util.Set<BlockPos> connected(World world,BlockPos seed,java.util.function.Predicate<BlockPos> allowed){
        if(!allowed.test(seed))throw new IllegalArgumentException("Surface is outside the selected pad");
        if(!world.isBlockLoaded(seed) || !(world.getBlockState(seed).getBlock() instanceof BlockTurntableSurface))
            throw new IllegalArgumentException("Place Programmable Surface blocks in front of the controller");
        EnumFacing normal=world.getBlockState(seed).getValue(BlockTurntableSurface.FACING);
        TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());
        EnumFacing u=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz),v=EnumFacing.getFacingFromVector(basis.vx,basis.vy,basis.vz);
        Set<BlockPos> visited=new HashSet<>(),members=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(seed.toImmutable());
        while(!queue.isEmpty()){
            BlockPos pos=queue.removeFirst();if(!allowed.test(pos)||!visited.add(pos))continue;
            if(!world.isBlockLoaded(pos))throw new IllegalArgumentException("Load every surface and its boundary before scanning");
            IBlockState state=world.getBlockState(pos);if(!(state.getBlock() instanceof BlockTurntableSurface))continue;
            if(state.getValue(BlockTurntableSurface.FACING)!=normal)throw new IllegalArgumentException("Connected surfaces must face the same direction");
            members.add(pos);if(members.size()>256)throw new IllegalArgumentException("Programmable Surfaces are limited to 16 x 16 blocks");
            for(EnumFacing side:new EnumFacing[]{u,u.getOpposite(),v,v.getOpposite()})queue.add(pos.offset(side));
        }
        return Collections.unmodifiableSet(members);
    }
    public static final double DISC_RADIUS=.43;
    public boolean onDisc(double x,double y,double z){double dx=x-pivot.x,dy=y-pivot.y,dz=z-pivot.z,u=dx*basis.ux+dy*basis.uy+dz*basis.uz,v=dx*basis.vx+dy*basis.vy+dz*basis.vz,r=size*DISC_RADIUS;return u*u+v*v<=r*r+1e-8;}
    public boolean loaded(World world){for(BlockPos cell:cells)if(!world.isBlockLoaded(cell))return false;return true;}
    public NBTTagCompound write(){
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Normal",normal.getIndex());
        NBTTagList positions=new NBTTagList();for(BlockPos p:cells)positions.appendTag(new NBTTagLong(p.toLong()));
        tag.setTag("Cells",positions);return tag;
    }
    public static SquareLayout read(NBTTagCompound tag){
        int face=tag.getInteger("Normal");NBTTagList positions=tag.getTagList("Cells",4);
        if(face<0 || face>5 || positions.tagCount()<1 || positions.tagCount()>256)throw new IllegalArgumentException("Invalid turntable surface data");
        Set<BlockPos> members=new HashSet<>();for(int i=0;i<positions.tagCount();i++)if(!members.add(BlockPos.fromLong(((NBTTagLong)positions.get(i)).getLong())))throw new IllegalArgumentException("Duplicate surface cell");
        return new SquareLayout(members,EnumFacing.getFront(face));
    }
}
