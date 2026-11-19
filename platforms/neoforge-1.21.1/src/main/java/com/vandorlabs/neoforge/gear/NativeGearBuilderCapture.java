package com.vandorlabs.neoforge.gear;

import com.vandorlabs.neoforge.builders.*;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.state.BlockState;

/** Complete idle production builder frames may translate through an Extender's exact grid endpoints. */
final class NativeGearBuilderCapture {
    static boolean builder(BlockState state){return NativeBuilderTransfer.builder(state);}

    static Set<BlockPos> complete(NativeGearEntity arm,ServerLevel world,Set<BlockPos> selected){
        var roots=new LinkedHashSet<NativeBuilderEntity>();
        for(var pos:selected){
            if(!NativeBuilderFormation.loaded(world,pos)||!builder(world.getBlockState(pos)))continue;
            var root=NativeBuilderBindings.ownerAt(world,pos);
            if(root==null||!root.formed()||root.design().drive()!=null||!root.available())
                throw new IllegalArgumentException("Capture a complete idle production builder");
            NativeBuilderTransfer.requireIdle(world,root.getBlockPos());
            roots.add(root);
        }
        if(roots.isEmpty())return selected;
        requireExtender(arm);
        var result=new TreeSet<BlockPos>(selected);
        for(var root:roots){
            var d=root.design();
            for(int x=0;x<d.width();x++)for(int y=0;y<d.height();y++)for(int z=0;z<d.depth();z++){
                var pos=root.cell(x,y,z);
                if(NativeBuilderBindings.ownerAt(world,pos)!=root)
                    throw new IllegalArgumentException("Load every owned builder frame and interior part");
                result.add(pos);
                if(result.size()>8192)throw new IllegalArgumentException("Builder attachment exceeds the arm cell budget");
            }
        }
        return Set.copyOf(result);
    }

    static boolean breakBlocked(ServerPlayer actor,ServerLevel world,BlockPos pos,BlockState state){
        return builder(state)?NativeBuilderTransactions.transferBreakBlocked(actor,world,pos,state)
            :NativeObserverCapture.breakBlocked(actor,world,pos,state);
    }

    static void validate(List<NativeGearSnapshot.Cell> cells){
        var builders=new ArrayList<NativeRawCell>();
        for(var cell:cells)if(builder(cell.state))builders.add(new NativeRawCell(cell.pos,cell.state,cell.tile(),NativeGearSnapshot.MAX_BYTES));
        if(!builders.isEmpty())NativeBuilderTransfer.validate(builders);
    }

    static boolean nativeOrientation(NativeGearEntity arm,NativeGearPayload payload,NativeGearPayload.Transform transform){
        if(payload.cells().stream().noneMatch(cell->builder(cell.state)))return true;
        return arm.definition().kind==NativeGearDefinition.Kind.EXTENDER
            &&transform.vector(1,0,0).distanceToSqr(1,0,0)<1e-12
            &&transform.vector(0,1,0).distanceToSqr(0,1,0)<1e-12
            &&transform.vector(0,0,1).distanceToSqr(0,0,1)<1e-12;
    }

    static void checkStroke(NativeGearEntity arm,NativeGearPayload payload){
        if(payload.cells().stream().noneMatch(cell->builder(cell.state)))return;
        requireExtender(arm);
        if(payload.endpoint(arm,0).isEmpty()||payload.endpoint(arm,1).isEmpty())
            throw new IllegalArgumentException("A builder requires two grid-aligned Extender endpoints");
    }

    private static void requireExtender(NativeGearEntity arm){
        if(arm.definition().kind!=NativeGearDefinition.Kind.EXTENDER||arm.settings().pushBlocks())
            throw new IllegalArgumentException("Use a translating Extender with block pushing disabled");
    }

    private NativeGearBuilderCapture(){}
}
