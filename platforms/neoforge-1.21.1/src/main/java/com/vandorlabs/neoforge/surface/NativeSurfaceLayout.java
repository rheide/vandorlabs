package com.vandorlabs.neoforge.surface;

import com.vandorlabs.surface.core.*;
import com.vandorlabs.turntable.TurntableGeometry;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Native positions are adapters over the same exact filled-area policy as Source. */
public final class NativeSurfaceLayout {
    public final SurfaceLayout geometry;
    public final List<BlockPos> cells;
    public final BlockPos root;
    public final Direction normal;
    public NativeSurfaceLayout(Collection<BlockPos> cells,Direction normal,boolean rectangle){
        this.normal=normal;var points=new ArrayList<SurfaceLayout.Cell>();for(var cell:cells)points.add(point(cell));geometry=new SurfaceLayout(points,face(normal),rectangle);
        var ordered=new ArrayList<BlockPos>();for(var cell:geometry.cells)ordered.add(position(cell));this.cells=List.copyOf(ordered);root=this.cells.getFirst();
    }
    public BlockPos cell(int u,int v,int depth){return position(geometry.cell(u,v,depth));}
    public static NativeSurfaceLayout single(BlockPos pos,Direction normal){return new NativeSurfaceLayout(List.of(pos),normal,true);}
    public static Set<BlockPos> connected(Level level,BlockPos seed,Predicate<BlockPos> allowed,boolean artwork){
        var access=new SurfaceDiscovery.Access<BlockPos>(){
            private NativeSurfaceEntity appearance;
            private BlockPos current;private net.minecraft.world.level.block.state.BlockState state;private net.minecraft.world.level.block.entity.BlockEntity tile;private boolean tileRead;
            private net.minecraft.world.level.block.state.BlockState state(BlockPos cell){if(!cell.equals(current)){current=cell;state=level.getBlockState(cell);tile=null;tileRead=false;}return state;}
            private net.minecraft.world.level.block.entity.BlockEntity tile(BlockPos cell){state(cell);if(!tileRead){tile=level.getBlockEntity(cell);tileRead=true;}return tile;}
            public boolean loaded(BlockPos cell){return level.hasChunkAt(cell);}
            public boolean surface(BlockPos cell){return state(cell).getBlock() instanceof NativeSurfaceBlock;}
            public boolean hasTile(BlockPos cell){return tile(cell) instanceof NativeSurfaceEntity value&&!value.inert();}
            public TurntableGeometry.Face face(BlockPos cell){return NativeSurfaceLayout.face(state(cell).getValue(NativeSurfaceBlock.FACING));}
            public boolean sameArtwork(BlockPos seedCell,BlockPos cell){if(appearance==null)appearance=(NativeSurfaceEntity)tile(seedCell);return appearance.sameArtwork((NativeSurfaceEntity)tile(cell));}
            public BlockPos offset(BlockPos cell,int x,int y,int z){return cell.offset(x,y,z);}
        };
        return SurfaceDiscovery.connected(access,seed.immutable(),allowed,artwork);
    }

    public static SurfaceLayout.Cell point(BlockPos pos){return new SurfaceLayout.Cell(pos.getX(),pos.getY(),pos.getZ());}
    public static BlockPos position(SurfaceLayout.Cell pos){return new BlockPos(pos.x,pos.y,pos.z);}
    public static TurntableGeometry.Face face(Direction normal){return TurntableGeometry.Face.values()[normal.get3DDataValue()];}
}
