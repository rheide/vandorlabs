package com.vandorlabs.neoforge.wall;

import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.render.FlatPanelPlacement;
import com.vandorlabs.render.FlatWallTopology;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native coordinates around the shared flat-panel topology and collision rectangles. */
public final class WallGeometry {
    public record Pose(Direction facing,int depth,boolean full,FlatWallTopology.Corner corner){}
    private record Key(Direction facing,int depth,boolean full,Double front,Double back,Boolean frontRight,Boolean backRight,boolean left,boolean right){}
    private static final java.util.Map<Key,VoxelShape> SHAPES=new java.util.LinkedHashMap<>(128,.75F,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Key,VoxelShape> entry){return size()>128;}
    };
    private WallGeometry(){}
    private static Key key(Pose pose){var c=pose.corner;return new Key(pose.facing,pose.depth,pose.full,c==null?null:c.frontArm,c==null?null:c.backArm,c==null?null:c.frontRight,c==null?null:c.backRight,c!=null&&c.straightLeft,c!=null&&c.straightRight);}
    private static FlatWallTopology.Panel panel(BlockState state,com.vandorlabs.ui.WallDialog.State settings){
        if(!(state.getBlock() instanceof WallBlock block))return null;
        boolean join=!block.profile().porthole()||settings==null||settings.appearance.join;
        int facing=state.getValue(WallBlock.FACING).get3DDataValue();double near=FlatPanelPlacement.near(state.getValue(WallBlock.DEPTH));
        return new FlatWallTopology.Panel(true,block.profile()!=com.vandorlabs.ui.WallDialog.Profile.FULL_PORTHOLE,true,join,facing,facing,near,near);
    }
    public static String signature(Pose pose){return key(pose).toString();}
    /** Collision queries run on the level thread and can read installed tiles. */
    public static Pose live(BlockState state,BlockGetter level,BlockPos pos){
        if(!(level instanceof Level))return pose(state,pos,p->panel(level.getBlockState(p),level.getModelData(p).get(WallEntity.WALL_SETTINGS)));
        return pose(state,pos,p->{if(level instanceof Level world&&!world.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4))return null;
            BlockState other=level.getBlockState(p);if(!(other.getBlock() instanceof WallBlock))return null;return panel(other,level.getBlockEntity(p) instanceof WallEntity tile?tile.wallSettings():null);});
    }
    /** Section workers consume only captured states and the region's immutable native ModelData. */
    public static Pose captured(BlockState state,net.minecraft.world.level.BlockAndTintGetter region,BlockPos pos,net.neoforged.neoforge.client.model.data.ModelData own){
        var owner=panel(state,own.get(WallEntity.WALL_SETTINGS));
        return pose(state,owner,pos,p->panel(region.getBlockState(p),region.getModelData(p).get(WallEntity.WALL_SETTINGS)));
    }
    private static Pose pose(BlockState state,BlockPos pos,java.util.function.Function<BlockPos,FlatWallTopology.Panel> sampler){return pose(state,sampler.apply(pos),pos,sampler);}
    private static Pose pose(BlockState state,FlatWallTopology.Panel owner,BlockPos pos,java.util.function.Function<BlockPos,FlatWallTopology.Panel> sampler){
        var block=(WallBlock)state.getBlock();var corner=owner==null?null:FlatWallTopology.corner(owner,direction->sampler.apply(pos.relative(Direction.from3DDataValue(direction))));
        return new Pose(state.getValue(WallBlock.FACING),state.getValue(WallBlock.DEPTH),block.profile()==com.vandorlabs.ui.WallDialog.Profile.FULL_PORTHOLE,corner);
    }
    public static List<AABB> boxes(Pose pose){
        if(pose.full)return List.of(new AABB(0,0,0,1,1,1));List<AABB> result=new ArrayList<>();
        for(double[] box:FlatWallTopology.boxes(FlatPanelPlacement.near(pose.depth),pose.corner)){
            Point a=FlatPanelPlacement.rotate(new Point(box[0]/16,0,box[1]/16),pose.facing.get3DDataValue());
            Point b=FlatPanelPlacement.rotate(new Point(box[2]/16,1,box[3]/16),pose.facing.get3DDataValue());
            result.add(new AABB(Math.min(a.x,b.x),0,Math.min(a.z,b.z),Math.max(a.x,b.x),1,Math.max(a.z,b.z)));
        }return List.copyOf(result);
    }
    public static VoxelShape collision(Pose pose){if(pose.full)return Shapes.block();Key key=key(pose);synchronized(SHAPES){var found=SHAPES.get(key);if(found!=null)return found;
        var shape=com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(boxes(pose));SHAPES.put(key,shape);return shape;}}
}
