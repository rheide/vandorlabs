package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.util.EnumFacing;

/** Direct reads retain the original property objects and canonical transitions. */
final class TrapdoorStateChecks {
    static void run() {
        int cases=0;
        for(BlockProgrammableTrapdoor block:new BlockProgrammableTrapdoor[]{(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_TRAPDOOR,(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR}) {
            BlockStateContainer reference=new BlockStateContainer(block,BlockTrapDoor.FACING,BlockTrapDoor.OPEN,BlockTrapDoor.HALF);
            PropertyDirection equivalentFacing=PropertyDirection.create("facing",EnumFacing.Plane.HORIZONTAL);
            for(IBlockState state:block.getBlockState().getValidStates()) {
                IBlockState expected=reference.getBaseState().withProperty(BlockTrapDoor.FACING,state.getValue(BlockTrapDoor.FACING))
                        .withProperty(BlockTrapDoor.OPEN,state.getValue(BlockTrapDoor.OPEN)).withProperty(BlockTrapDoor.HALF,state.getValue(BlockTrapDoor.HALF));
                require(state.getProperties().equals(expected.getProperties()),"direct property map differs");
                require(block.getMetaFromState(state)==block.getMetaFromState(expected),"trapdoor metadata changed");
                require(block.getStateFromMeta(block.getMetaFromState(state))==state,"metadata lost canonical state");
                require(state.getValue(equivalentFacing)==state.getValue(BlockTrapDoor.FACING),"equivalent external property read failed");
                for(EnumFacing facing:EnumFacing.HORIZONTALS)
                    require(state.withProperty(equivalentFacing,facing)==state.withProperty(BlockTrapDoor.FACING,facing),"equivalent external transition changed");
                for(IProperty<?> property:state.getPropertyKeys())transitions(property,state,expected);
                try {state.getValue(PropertyBool.create("absent"));throw new AssertionError("missing property accepted");}
                catch(IllegalArgumentException expectedFailure){}
                cases++;
            }
        }
        System.out.println("PASS: "+cases+" direct trapdoor states preserve vanilla property identity, metadata and transitions");
    }
    private static <T extends Comparable<T>> void transitions(IProperty<T> property,IBlockState state,IBlockState expected) {
        for(T value:property.getAllowedValues())require(state.withProperty(property,value).getProperties()
                .equals(expected.withProperty(property,value).getProperties()),"property transition differs");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
