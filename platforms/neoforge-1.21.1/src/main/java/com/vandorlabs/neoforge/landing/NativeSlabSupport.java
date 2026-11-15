package com.vandorlabs.neoforge.landing;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Source-compatible slab families used by standalone wheels and track mounting artwork. */
public final class NativeSlabSupport {
    public static boolean slab(BlockState state){return state.getBlock() instanceof SlabBlock||state.getBlock() instanceof com.vandorlabs.neoforge.shape.NativeProgrammableSlab||state.getBlock() instanceof com.vandorlabs.neoforge.light.NativeLightBlock&&state.hasProperty(BlockStateProperties.HALF);}
    public static boolean top(BlockState state){return slab(state)&&(state.hasProperty(BlockStateProperties.SLAB_TYPE)?state.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.TOP:state.hasProperty(BlockStateProperties.HALF)&&state.getValue(BlockStateProperties.HALF)==Half.TOP);}
    public static boolean bottom(BlockState state){return slab(state)&&(state.hasProperty(BlockStateProperties.SLAB_TYPE)?state.getValue(BlockStateProperties.SLAB_TYPE)==SlabType.BOTTOM:state.hasProperty(BlockStateProperties.HALF)&&state.getValue(BlockStateProperties.HALF)==Half.BOTTOM);}
    private NativeSlabSupport(){}
}
