package com.vandorlabs.neoforge.screen;

import com.vandorlabs.render.BoxRayTrace;
import com.vandorlabs.render.DiagonalScreenHull;
import com.vandorlabs.render.InputSurfaceLayout;
import com.vandorlabs.render.ScreenFamilyPolicy;
import com.vandorlabs.render.ScreenFamilyPolicy.Kind;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;

/** Native voxel collision with exact authored diagonal picking and outlines. */
final class ScreenShapes {
    private static final AtomicReferenceArray<VoxelShape> SELECTION=new AtomicReferenceArray<>(32768),COLLISION=new AtomicReferenceArray<>(32768);
    private static final DoubleList COORDS;
    static {double[] coords=new double[17];for(int i=0;i<coords.length;i++)coords[i]=i/16D;COORDS=DoubleArrayList.wrap(coords);}
    private ScreenShapes(){}
    static VoxelShape shape(BlockState state,BlockGetter level,BlockPos pos,boolean collision){
        ProgrammableScreenBlock block=(ProgrammableScreenBlock)state.getBlock();Kind kind=block.family();if(kind==Kind.FLAT||kind==Kind.CONSOLE)return Shapes.block();
        ProgrammableScreenEntity tile=level.getBlockEntity(pos) instanceof ProgrammableScreenEntity screen?screen:null;
        int face=block.facing(state).get3DDataValue(),wall=tile==null?(block.upper(state)?2:0):tile.wallPosition(),ceiling=tile==null?(kind==Kind.FULL_INPUT||kind==Kind.HALF_CONSOLE?1:2):tile.ceilingPosition();
        boolean mounted=tile!=null&&tile.ceilingMounted(),small=tile!=null&&tile.data().smallInput;
        int key=kind.ordinal()|face<<3|(block.keyboard(state)?1:0)<<6|(block.upper(state)?1:0)<<7|(block.inverted(state)?1:0)<<8|(mounted?1:0)<<9|wall<<10|(small?1:0)<<12|ceiling<<13;
        var cache=collision?COLLISION:SELECTION;VoxelShape found=cache.get(key);if(found!=null)return found;
        synchronized(cache){found=cache.get(key);if(found==null){found=build(kind,face,block.keyboard(state),block.upper(state),block.inverted(state),mounted,wall,small,ceiling,collision);cache.set(key,found);}}return found;
    }
    private static VoxelShape build(Kind kind,int face,boolean keyboard,boolean upper,boolean inverted,boolean ceiling,int wall,boolean small,int ceilingPosition,boolean collision){
        if(kind==Kind.DIAGONAL)return new DiagonalShape(DiagonalScreenHull.of(face,inverted));
        if(ScreenFamilyPolicy.input(kind)){InputSurfaceLayout.Box b=ScreenFamilyPolicy.input(kind,keyboard,upper,ceiling,wall,small,ceilingPosition).housing;return box(rotate(new DiagonalScreenHull.Bounds(b.x0/16,b.y0/16,b.z0/16,b.x1/16,b.y1/16,b.z1/16),face));}
        if(kind==Kind.HALF_CONSOLE){double offset=ceiling?InputSurfaceLayout.ceilingStart(16,ceilingPosition)/16:0;return box(rotate(new DiagonalScreenHull.Bounds(0,0,offset,1,.5,1+offset),face));}
        DiagonalScreenHull.Bounds[] slices=new DiagonalScreenHull.Bounds[8];for(int i=0;i<8;i++){double h=(i+1)/16D;slices[i]=rotate(new DiagonalScreenHull.Bounds(0,upper?1-h:0,(8+i)/16D,1,upper?1:h,(9+i)/16D),face);}
        VoxelShape shape=Shapes.empty();for(var b:slices)shape=Shapes.or(shape,box(b));
        return collision?shape:new HalfSelection(rotate(new DiagonalScreenHull.Bounds(0,upper?.5:0,.5,1,upper?1:.5,1),face),slices);
    }
    private static VoxelShape box(DiagonalScreenHull.Bounds b){return Shapes.box(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static DiagonalScreenHull.Bounds rotate(DiagonalScreenHull.Bounds b,int face){if(face==5)return new DiagonalScreenHull.Bounds(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX);if(face==3)return new DiagonalScreenHull.Bounds(1-b.maxX,b.minY,1-b.maxZ,1-b.minX,b.maxY,1-b.minZ);if(face==4)return new DiagonalScreenHull.Bounds(b.minZ,b.minY,1-b.maxX,b.maxZ,b.maxY,1-b.minX);return b;}
    private static DiscreteVoxelShape discrete(DiagonalScreenHull.Bounds[] boxes){BitSetDiscreteVoxelShape shape=new BitSetDiscreteVoxelShape(16,16,16);for(var b:boxes)for(int x=(int)Math.round(b.minX*16);x<(int)Math.round(b.maxX*16);x++)for(int y=(int)Math.round(b.minY*16);y<(int)Math.round(b.maxY*16);y++)for(int z=(int)Math.round(b.minZ*16);z<(int)Math.round(b.maxZ*16);z++)shape.fill(x,y,z);return shape;}
    private static final class DiagonalShape extends VoxelShape {
        private final DiagonalScreenHull hull;
        DiagonalShape(DiagonalScreenHull hull){super(discrete(hull.collision));this.hull=hull;}
        @Override public DoubleList getCoords(Direction.Axis axis){return COORDS;}
        @Override public void forAllEdges(Shapes.DoubleLineConsumer consumer){for(double[] edge:hull.outline)consumer.consume(edge[0],edge[1],edge[2],edge[3],edge[4],edge[5]);}
        @Override public BlockHitResult clip(Vec3 start,Vec3 end,BlockPos pos){var hit=hull.trace(start.x-pos.getX(),start.y-pos.getY(),start.z-pos.getZ(),end.x-pos.getX(),end.y-pos.getY(),end.z-pos.getZ());return hit==null?null:new BlockHitResult(new Vec3(pos.getX()+hit.x,pos.getY()+hit.y,pos.getZ()+hit.z),Direction.getNearest((float)hit.nx,(float)hit.ny,(float)hit.nz),pos,false);}
    }
    private static final class HalfSelection extends VoxelShape {
        private final DiagonalScreenHull.Bounds[] slices;
        HalfSelection(DiagonalScreenHull.Bounds bounds,DiagonalScreenHull.Bounds[] slices){super(discrete(new DiagonalScreenHull.Bounds[]{bounds}));this.slices=slices;}
        @Override public DoubleList getCoords(Direction.Axis axis){return COORDS;}
        @Override public BlockHitResult clip(Vec3 start,Vec3 end,BlockPos pos){var hit=BoxRayTrace.trace(slices,start.x-pos.getX(),start.y-pos.getY(),start.z-pos.getZ(),end.x-pos.getX(),end.y-pos.getY(),end.z-pos.getZ());return hit==null?null:new BlockHitResult(new Vec3(pos.getX()+hit.x,pos.getY()+hit.y,pos.getZ()+hit.z),Direction.from3DDataValue(hit.facing),pos,false);}
    }
}
