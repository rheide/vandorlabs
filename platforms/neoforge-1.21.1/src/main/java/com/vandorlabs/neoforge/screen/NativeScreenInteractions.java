package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import com.vandorlabs.screen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Standalone controls use authoritative current player angles and the same pixels as the renderer. */
public final class NativeScreenInteractions {
    private NativeScreenInteractions(){}
    public static boolean activate(Level level,BlockPos pos,Player player,InteractionHand hand){
        if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Screen interaction requires server thread");
        if(hand!=InteractionHand.MAIN_HAND||player.isShiftKeyDown()||player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get())||!player.isAlive()||player.isSpectator()||player.level()!=level||player.distanceToSqr(pos.getCenter())>64||!level.hasChunkAt(pos)||!level.mayInteract(player,pos))return false;
        if(level instanceof ServerLevel server&&!NativeRedstoneChannels.open(server))return false;
        if(!(level.getBlockEntity(pos) instanceof NativeScreenEntity owner)||owner.inert()||owner.isRemoved()||owner.getLevel()!=level||!(owner.getBlockState().getBlock() instanceof NativeScreenBlock block)||block.family()!=owner.family())return false;
        Vec3 start=player.getEyePosition().subtract(Vec3.atLowerCornerOf(pos));double reach=Math.min(8,player.blockInteractionRange());Vec3 end=start.add(Vec3.directionFromRotation(player.getXRot(),player.getYRot()).scale(reach));
        if(owner.yoke())return activateYoke(owner,player,start,end);
        if(NativeScreenNavigation.activate(owner,player,hand,start,end))return true;
        if(NativeScreenChipInteractions.activate(owner,player,hand,start,end))return true;
        for(int slot=0;slot<2;slot++)if(owner.family().supportsSlot(slot)){
            var visual=NativeScreenPowerViews.prepare(owner,slot==0?ScreenPowerQueries.SURFACE_0:ScreenPowerQueries.SURFACE_1);
            if(visual.inert()||visual.appearance().surface(slot)!=-2)continue;var settings=visual.settings();var data=settings.scalar();
            boolean chipDeck=owner.family()==ScreenFamily.HALF_CONSOLE&&settings.secondarySurface()==-4&&!visual.controls().get(1).enabled();
            var projection=ScreenControlLayout.of(owner.family(),block.screenState(owner.getBlockState()),slot,data.smallInput,data.wallPosition,settings.ceilingMounted(),settings.ceilingPosition(),chipDeck);
            var sample=projection.sample(start.x,start.y,start.z,end.x,end.y,end.z);var rows=visual.controls().get(slot).rows();int row=sample.row(rows.size());if(row<0)continue;
            if(rows.get(row).slider()){int step=sample.sliderStep();if(step<0)return false;if(!level.isClientSide)owner.controlService().select(slot,row,step);}
            else if(!level.isClientSide)owner.controlService().toggle(slot,row);
            // The released block consumes a visible row click even when an empty channel list has no effect.
            return true;
        }
        return false;
    }

    private static boolean activateYoke(NativeScreenEntity owner,Player player,Vec3 start,Vec3 end){
        Level level=owner.getLevel();
        if(!player.mayUseItemAt(owner.getBlockPos(),net.minecraft.core.Direction.UP,player.getMainHandItem()))return false;
        Vec3 offset=Vec3.atLowerCornerOf(owner.getBlockPos());
        var worldHit=level.clip(new net.minecraft.world.level.ClipContext(start.add(offset),end.add(offset),
            net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        if(worldHit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK||!worldHit.getBlockPos().equals(owner.getBlockPos()))
            return false;
        int selected=-1;
        NativeYokeGeometry.Face.Sample closest=null;
        for(int slot=0;slot<2;slot++){
            var controls=owner.controls(slot);
            if(!controls.enabled())continue;
            var sample=NativeYokeGeometry.face(owner.getBlockState(),slot).sample(start,end,controls.rows().size());
            if(sample!=null&&(closest==null||sample.time()<closest.time())){closest=sample;selected=slot;}
        }
        if(closest==null)return false;
        if(!level.isClientSide){
            var row=owner.controls(selected).rows().get(closest.row());
            if(row.slider())owner.controlService().select(selected,closest.row(),closest.sliderStep());
            else owner.controlService().toggle(selected,closest.row());
        }
        return true;
    }
}
