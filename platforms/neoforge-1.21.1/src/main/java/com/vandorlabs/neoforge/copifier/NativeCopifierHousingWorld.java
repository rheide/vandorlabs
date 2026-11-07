package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.NativeConfigurationAccess;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;

/** Inactive server adapter. A future item route must additionally authenticate the held Copifier. */
public final class NativeCopifierHousingWorld {
    public static CompoundTag capture(ServerLevel level,BlockPos pos,ServerPlayer actor,Direction face){
        if(!level.getServer().isSameThread()||actor==null||face==null||!NativeConfigurationAccess.canEditDialog(actor,level,pos,face))return null;
        if(!(level.getBlockEntity(pos) instanceof ProgrammableBlockEntity tile)||tile.isRemoved()||tile.getLevel()!=level||tile.getBlockState()!=level.getBlockState(pos))return null;
        var id=BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock());if(!id.getNamespace().equals("vandorlabs"))return null;
        final NativeCopifierHousingCapture.Family family;
        try{family=NativeCopifierHousingCapture.Family.of(id.getPath());}catch(IllegalArgumentException unsupported){return null;}
        try{return NativeCopifierHousingCapture.capture(family,tile.saveWithoutMetadata(level.registryAccess()));}
        catch(IllegalArgumentException unsupported){return null;}
    }
    private NativeCopifierHousingWorld(){}
}
