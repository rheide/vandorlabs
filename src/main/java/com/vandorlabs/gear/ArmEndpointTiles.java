package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import java.util.IdentityHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Bidirectional endpoint state policy; NBT is only translated by the caller, never interpreted here. */
final class ArmEndpointTiles {
    private final int[] forward,inverse;
    private final boolean turns;
    private final IdentityHashMap<IBlockState,IBlockState> tileFree=new IdentityHashMap<>();
    ArmEndpointTiles(Vec3d[] axes){
        ArmGridMapping mapping=ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,axes);if(mapping==null)throw new IllegalArgumentException("Attachment endpoint is not a proper grid rotation");
        forward=mapping.basis();inverse=new int[9];for(int row=0;row<3;row++)for(int col=0;col<3;col++)inverse[row*3+col]=forward[col*3+row];turns=mapping.rotates();
    }
    int[] basis(){return forward.clone();}
    IBlockState constructionState(VehicleStructure.Cell nativeCell){
        if(!turns)return nativeCell.state;
        if(!nativeCell.hasTileData()&&tileFree.containsKey(nativeCell.state))return tileFree.get(nativeCell.state);
        IBlockState rest=ArmGridTiles.state(nativeCell,inverse);
        if(rest!=null&&ArmGridTiles.state(nativeCell,rest,forward)!=nativeCell.state)rest=null;
        if(!nativeCell.hasTileData())tileFree.put(nativeCell.state,rest);
        return rest;
    }
}
