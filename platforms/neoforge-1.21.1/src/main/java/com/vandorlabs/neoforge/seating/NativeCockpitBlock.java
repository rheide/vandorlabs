package com.vandorlabs.neoforge.seating;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One stationary owner with the eight cells reserved by the authored Cockpit. */
final class NativeCockpitBlock extends NativeAdvancedChairBlock {
    private static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 3);

    NativeCockpitBlock(Properties properties) {
        super(NativeSeatKind.COCKPIT, properties);
    }

    @Override protected MapCodec<? extends NativeSeatBlock> codec() { return simpleCodec(NativeCockpitBlock::new); }
    @Override protected IntegerProperty lateralProperty() { return SECTION; }
    @Override Direction placementFacing(BlockPlaceContext context) { return context.getHorizontalDirection(); }
    @Override int partIndex(BlockState state) { return 2*state.getValue(SECTION)+(state.getValue(UPPER)?1:0); }
    @Override BlockState stateFor(BlockState root, int part) {
        return root.setValue(UPPER,(part&1)!=0).setValue(SECTION,part/2);
    }
    @Override int cells() { return NativeCockpitGeometry.CELLS; }
    @Override BlockPos offset(int part, Direction facing) { return NativeCockpitGeometry.offset(part,facing); }

    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        var owner=owner(world,pos);
        if(proxy(state)&&owner==null)return Shapes.empty();
        return NativeCockpitGeometry.shape(partIndex(state),state.getValue(FACING),owner==null?0:owner.liftPosition());
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state; }
}
