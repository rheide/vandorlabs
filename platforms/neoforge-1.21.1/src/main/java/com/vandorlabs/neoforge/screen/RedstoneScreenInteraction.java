package com.vandorlabs.neoforge.screen;

import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.ui.RedstoneScreenLayout;
import net.minecraft.world.entity.player.Player;

/** Vanilla block use supplies authority; the same authored plane supplies render and hit mapping. */
public final class RedstoneScreenInteraction {
    private RedstoneScreenInteraction(){}
    public static RedstoneScreenLayout.Hit hit(ProgrammableScreenEntity screen,Player player){
        var eye=player.getEyePosition().subtract(screen.getBlockPos().getX(),screen.getBlockPos().getY(),screen.getBlockPos().getZ());
        var end=eye.add(player.calculateViewVector(player.getXRot(),player.getYRot()).scale(Math.min(8,player.blockInteractionRange())));
        return new RedstoneScreenLayout(false).hit(ScreenSurface.quad(ScreenSurface.Kind.FLAT,false),screen.getBlockState().getValue(ProgrammableScreenBlock.FACING).get3DDataValue(),new double[]{eye.x,eye.y,eye.z},new double[]{end.x,end.y,end.z},screen.redstone().rows().size());
    }
    public static boolean activate(ProgrammableScreenEntity screen,Player player){
        if(player.isShiftKeyDown()||!screen.redstone().enabled())return false;
        var hit=hit(screen,player);if(hit.row<0)return false;
        var row=screen.redstone().rows().get(hit.row);
        if(row.slider){int segment=RedstoneScreenLayout.sliderSegment(hit.column,row.segments());if(segment<0)return false;if(!player.level().isClientSide)screen.redstone().selectSegment(hit.row,segment);}
        else if(!player.level().isClientSide)screen.redstone().toggleRow(hit.row);
        return true;
    }
}
