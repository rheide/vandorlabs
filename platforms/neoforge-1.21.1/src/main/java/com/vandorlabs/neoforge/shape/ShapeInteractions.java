package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.VandorLabsModern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

final class ShapeInteractions {
    static InteractionResult open(Level level,BlockPos pos,Player player){
        boolean wants=player.isCreative()&&player.isShiftKeyDown()||player.getMainHandItem().is(VandorLabsModern.CONFIGURIZER.get());
        if(!wants||!NativeConfigurationAccess.canEdit(player,level,pos))return InteractionResult.PASS;
        if(!level.isClientSide&&player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ProgrammableShapeEntity tile)ShapeMenu.open(server,tile);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private ShapeInteractions(){}
}
