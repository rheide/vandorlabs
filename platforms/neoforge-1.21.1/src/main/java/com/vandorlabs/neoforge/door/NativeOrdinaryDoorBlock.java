package com.vandorlabs.neoforge.door;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.SpaceDoorControlPanel;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Concrete ordinary Door behavior. Registration still awaits its functional renderer and live client acceptance. */
public final class NativeOrdinaryDoorBlock extends NativeProgrammableDoorBlock {
    public NativeOrdinaryDoorBlock(Properties properties,Supplier<BlockEntityType<NativeProgrammableDoorEntity>> entities,Supplier<MenuType<NativeDoorMenu>> menus,Supplier<Item> canonicalItem,boolean slidingModel){super(properties,entities,menus,canonicalItem,slidingModel);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
    @Override protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state){return net.minecraft.world.level.block.RenderShape.MODEL;}
    private static boolean left(BlockState state){return state.getValue(HINGE)==DoorHingeSide.LEFT;}
    @Override protected VoxelShape orphanShape(BlockState state,boolean collision){return NativeDoorGeometry.orphanShape(slidingModel(),state.getValue(FACING),state.getValue(OPEN),left(state),state.getValue(PAIRED),collision);}
    @Override protected VoxelShape doorShape(NativeDoorSettings settings,BlockState state,boolean collision){return NativeDoorGeometry.shape(settings,state.getValue(FACING),state.getValue(OPEN),left(state),state.getValue(PAIRED),collision);}
    @Override protected boolean controlPanelHit(NativeDoorSettings settings,BlockState state,SpaceDoorControlPanel.Side side,BlockHitResult hit,BlockPos lower){
        if(side==SpaceDoorControlPanel.Side.NONE)return false;var front=state.getValue(FACING);var panelFace=side==SpaceDoorControlPanel.Side.RIGHT?front.getClockWise():front.getCounterClockWise();if(hit.getDirection()!=panelFace)return false;
        return NativeDoorGeometry.panelContains(settings,normal(front,hit.getLocation().x-lower.getX(),hit.getLocation().z-lower.getZ()),hit.getLocation().y-lower.getY(),1);
    }
    private static double normal(Direction front,double x,double z){return switch(front){case SOUTH->z;case NORTH->1-z;case EAST->x;case WEST->1-x;default->throw new IllegalArgumentException("Horizontal Door facing required");};}
    @Override protected boolean securityOpen(NativeProgrammableDoorEntity host){return host.securityOpen();}
    @Override protected InteractionResult credentialInteraction(NativeProgrammableDoorEntity host,Player player,InteractionHand hand,BlockHitResult hit,boolean panelHit){
        if(player.level().isClientSide)return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server))return InteractionResult.FAIL;
        if(player.getItemInHand(hand).is(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()))return InteractionResult.CONSUME;
        if(!panelHit&&!host.holdingCredential(server,hand))return InteractionResult.CONSUME;
        var settings=host.settings();int side=0;
        if(settings.panelMode()==2){var front=host.getBlockState().getValue(FACING);var lower=host.getBlockPos();double n=normal(front,hit.getLocation().x-lower.getX(),hit.getLocation().z-lower.getZ());var door=settings.door();double first=(SpaceDoorControlPanel.z0(door.sliding,settings.depth()==2)+1.5)/16+SpaceDoorData.positionOffset(door.sliding,door.framed,door.hinges,settings.depth());side=Math.abs(n-first)<=Math.abs(n-(1-first))?0:1;}
        return host.interactCredential(side,server,hand)?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
    @Override protected boolean isPathfindable(BlockState state,PathComputationType path){return path!=PathComputationType.WATER&&state.getValue(OPEN);}
}
