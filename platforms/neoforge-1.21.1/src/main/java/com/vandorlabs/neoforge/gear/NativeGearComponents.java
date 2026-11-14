package com.vandorlabs.neoforge.gear;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Native component cells have an independent local plane; assembly ownership never changes that plane. */
public final class NativeGearComponents {
    private static final ThreadLocal<Direction> PLACEMENT_FACING=new ThreadLocal<>();
    static Direction placementFacing(){return PLACEMENT_FACING.get();}
    static <T> T placing(Direction frame,Direction facing,java.util.function.Supplier<T> operation){var prior=PLACEMENT_FACING.get();PLACEMENT_FACING.set(facing);try{return placing(frame,operation);}finally{if(prior==null)PLACEMENT_FACING.remove();else PLACEMENT_FACING.set(prior);}}
    private static final ThreadLocal<Direction> PLACEMENT=new ThreadLocal<>();
    static Direction placementFrame(){var frame=PLACEMENT.get();return frame==null?Direction.DOWN:frame;}
    static <T> T placing(Direction frame,java.util.function.Supplier<T> operation){var prior=PLACEMENT.get();PLACEMENT.set(frame);try{return operation.get();}finally{if(prior==null)PLACEMENT.remove();else PLACEMENT.set(prior);}}
    private static final ThreadLocal<Boolean> REMOVING=new ThreadLocal<>();
    public static BlockPos offset(Direction frame,Direction facing,int x,int z){var local=switch(facing){case EAST->new BlockPos(-z,0,x);case SOUTH->new BlockPos(-x,0,-z);case WEST->new BlockPos(z,0,-x);default->new BlockPos(x,0,z);};return NativeGearOrientation.vector(frame,local);}
    public static BlockPos root(Level world,BlockPos pos){var tile=NativeGearFormation.at(world,pos);return tile==null?pos:root(pos,tile.getBlockState(),tile.componentFrame());}
    public static BlockPos root(BlockPos pos,BlockState state,Direction frame){if(!(state.getBlock() instanceof NativeGearBlock block)||block.definition()!=NativeGearDefinition.LARGE_WHEEL)return pos;int part=state.getValue(NativeGearBlock.PART);return pos.subtract(offset(frame,state.getValue(NativeGearBlock.FACING),part&1,part>>1));}
    public static List<BlockPos> cells(BlockPos root,Direction frame,Direction facing){var cells=new ArrayList<BlockPos>(4);for(int part=0;part<4;part++)cells.add(root.offset(offset(frame,facing,part&1,part>>1)));return List.copyOf(cells);}
    public static boolean intact(Level world,BlockPos member){var tile=NativeGearFormation.at(world,member);if(tile==null)return false;if(tile.definition()!=NativeGearDefinition.LARGE_WHEEL)return true;var origin=root(world,member);var frame=tile.componentFrame();var facing=tile.getBlockState().getValue(NativeGearBlock.FACING);var cells=cells(origin,frame,facing);for(int i=0;i<4;i++){var part=NativeGearFormation.at(world,cells.get(i));if(part==null||part.definition()!=NativeGearDefinition.LARGE_WHEEL||part.componentFrame()!=frame||part.getBlockState().getValue(NativeGearBlock.FACING)!=facing||part.getBlockState().getValue(NativeGearBlock.PART)!=i)return false;}return true;}
    static boolean removing(){return Boolean.TRUE.equals(REMOVING.get());}
    public static void withoutBreak(Runnable operation){var prior=REMOVING.get();REMOVING.set(true);try{operation.run();}finally{if(prior==null)REMOVING.remove();else REMOVING.set(prior);}}
    private NativeGearComponents(){}
}
