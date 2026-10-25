package com.vandorlabs.neoforge.shape;

import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.ControlMount.Box;
import com.vandorlabs.render.StairGeometry;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.*;

/** Native shape conversion over the shared authored stair and half-height geometry. */
public final class ShapeGeometry {
    private ShapeGeometry(){}
    public static Direction facing(BlockState state){return state.getBlock() instanceof ProgrammableStairsBlock?state.getValue(StairBlock.FACING):state.getValue(ProgrammableSlabBlock.FACING);}
    public static boolean upper(BlockState state){return state.getValue(ProgrammableSlabBlock.HALF)==Half.TOP;}
    /** Derive joins from the supplied immutable render region or loaded native world; never read a tile. */
    public static BlockState connected(BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,StairState settings){
        if(!(state.getBlock() instanceof ProgrammableStairsBlock))return state;
        if(settings.roll!=0)return state.setValue(StairBlock.SHAPE,net.minecraft.world.level.block.state.properties.StairsShape.STRAIGHT);
        var shape=com.vandorlabs.render.StairJoinPolicy.shape(facing(state).get3DDataValue(),upper(state),face->{
            var other=pos.relative(Direction.from3DDataValue(face));
            if(level instanceof net.minecraft.world.level.Level world&&!world.getChunkSource().hasChunk(other.getX()>>4,other.getZ()>>4))return new com.vandorlabs.render.StairJoinPolicy.State(false,2,false);
            BlockState neighbor=level.getBlockState(other);boolean stair=neighbor.getBlock() instanceof StairBlock;
            return new com.vandorlabs.render.StairJoinPolicy.State(stair,stair?neighbor.getValue(StairBlock.FACING).get3DDataValue():2,stair&&neighbor.getValue(StairBlock.HALF)==Half.TOP);
        });
        return state.setValue(StairBlock.SHAPE,net.minecraft.world.level.block.state.properties.StairsShape.valueOf(shape.name()));
    }
    public static List<Box> boxes(BlockState state,StairState settings){
        if(state.getBlock() instanceof ProgrammableStairsBlock)return StairGeometry.boxes(facing(state).get3DDataValue(),upper(state),settings.roll==0?StairGeometry.Shape.valueOf(state.getValue(StairBlock.SHAPE).name()):StairGeometry.Shape.STRAIGHT,settings.packed());
        return List.of(upper(state)?new Box(0,.5,0,1,1,1):new Box(0,0,0,1,.5,1));
    }
    public static VoxelShape shape(BlockState state,StairState settings){
        VoxelShape result=Shapes.empty();for(Box box:boxes(state,settings))result=Shapes.joinUnoptimized(result,Shapes.box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ),BooleanOp.OR);return result.optimize();
    }
}
