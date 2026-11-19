package com.vandorlabs.neoforge.copier;

import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import com.vandorlabs.neoforge.builders.NativeBuilderPartBlock;
import com.vandorlabs.neoforge.builders.NativeBuilderTransfer;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

final class NativeCopyBuilders {
    static Set<BlockPos> validate(List<NativeRawCell> cells,boolean constructionChip){
        if(constructionChip&&cells.stream().anyMatch(cell->NativeBuilderTransfer.builder(cell.state)))
            throw new IllegalArgumentException("Copy builders from a complete stationary input area");
        return NativeBuilderTransfer.validate(cells,true);
    }

    static void requireIdle(ServerLevel world,BlockPos position){
        NativeBuilderTransfer.requireIdle(world,position);
        var builder=(NativeBuilderEntity)world.getBlockEntity(position);
        if(builder.drive()!=null&&(!builder.synthesisReady()||builder.driveLevel()!=0))
            throw new IllegalArgumentException("Turn off the standalone drive or computer with its Redstone gate before copying");
    }

    static void translateIdentity(NativeRawCell cell,BlockPos destination,UUID copyOwner,CompoundTag raw){
        if(!NativeBuilderTransfer.builder(cell.state))return;
        boolean part=cell.state.getBlock() instanceof NativeBuilderPartBlock;
        String field=part?"BuilderOwner":"BuilderKey";
        var root=part?destination.offset(BlockPos.of(raw.getLong("ConstructorOwner"))):destination;
        var label="vandorlabs:copy-builder/"+copyOwner+"/"+root.asLong()+"/"+raw.getUUID(field);
        raw.putUUID(field,UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)));
    }

    private NativeCopyBuilders(){}
}
