package com.vandorlabs.neoforge.screen;

import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.ui.RedstoneScreenLayout;
import net.minecraft.world.entity.player.Player;

/** Vanilla block use supplies authority; the same authored plane supplies render and hit mapping. */
public final class RedstoneScreenInteraction {
    private RedstoneScreenInteraction(){}
    public static RedstoneScreenLayout.Hit hit(ProgrammableScreenEntity screen,Player player){return hit(screen,player,0);}
    public static RedstoneScreenLayout.Hit hit(ProgrammableScreenEntity screen,Player player,int slot){
        var eye=player.getEyePosition().subtract(screen.getBlockPos().getX(),screen.getBlockPos().getY(),screen.getBlockPos().getZ());
        var end=eye.add(player.calculateViewVector(player.getXRot(),player.getYRot()).scale(Math.min(8,player.blockInteractionRange())));
        var family=screen.family();var surface=com.vandorlabs.render.ScreenFamilyPolicy.surface(family,slot,screen.keyboard(),screen.upper(),screen.geometryInverted(),screen.ceilingMounted(),screen.wallPosition(),screen.data().smallInput,screen.ceilingPosition());
        return RedstoneScreenLayout.of(com.vandorlabs.render.ScreenFamilyPolicy.half(family,slot)).hit(surface,screen.facing().get3DDataValue(),new double[]{eye.x,eye.y,eye.z},new double[]{end.x,end.y,end.z},screen.redstone(slot).rows().size());
    }
    public static boolean activate(ProgrammableScreenEntity screen,Player player){
        if(player.isShiftKeyDown())return false;
        for(int slot=0;slot<2;slot++)if(screen.supportsSlot(slot)&&screen.redstone(slot).enabled()){
            var hit=hit(screen,player,slot);if(hit.row<0)continue;
            var contents=screen.redstone(slot);var row=contents.rows().get(hit.row);
            if(row.slider){int segment=RedstoneScreenLayout.sliderSegment(hit.column,row.segments());if(segment<0)return false;if(!player.level().isClientSide)contents.selectSegment(hit.row,segment);}
            else if(!player.level().isClientSide)contents.toggleRow(hit.row);
            return true;
        }
        return false;
    }
}
