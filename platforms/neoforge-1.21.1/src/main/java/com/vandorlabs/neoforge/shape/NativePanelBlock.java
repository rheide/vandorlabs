package com.vandorlabs.neoforge.shape;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.CubeConfiguration;
import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.PanelDepthPolicy;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Shared loaded-world interaction, configured items and contained-fluid hooks for panel bodies. */
public abstract class NativePanelBlock extends HorizontalDirectionalBlock implements EntityBlock,SimpleWaterloggedBlock {
    protected NativePanelBlock(Properties properties){super(properties);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.isShiftKeyDown()&&CubeConfiguration.open(player,level,pos)?InteractionResult.sidedSuccess(level.isClientSide):InteractionResult.PASS;}
    @Override public ItemStack getCloneItemStack(BlockState state,HitResult hit,LevelReader level,BlockPos pos,Player player){ItemStack item=new ItemStack(this);if(level.getBlockEntity(pos) instanceof NativeWallEntity tile)tile.saveToItem(item,level.registryAccess());return item;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){NativeWallLayouts.invalidateAt(level,pos);NativeFluidWorld.queueAt(level,pos);}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){if(level instanceof Level actual)NativeWallLayouts.invalidateAt(actual,pos);NativeFluidWorld.queueAt(level,pos);return state;}
    @Override public boolean canPlaceLiquid(Player player,BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return NativeFluidWorld.canPlaceLiquid(level,pos,fluid);}
    @Override public boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){return NativeFluidWorld.placeLiquid(level,pos,fluid);}
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return NativeFluidWorld.pickupBlock(level,pos);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
}
