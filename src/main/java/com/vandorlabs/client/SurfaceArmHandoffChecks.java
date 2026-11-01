package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.TileEntityTurntableSurface;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Exercise delayed endpoint tile publication without changing server ownership. */
public final class SurfaceArmHandoffChecks {
    public static void run(GearTileEntity root){
        java.util.List<BlockPos> cells=new java.util.ArrayList<>(root.payload().surfaceEndpointCells(root));
        require(cells.size()==9,"all endpoint Surfaces indexed");BlockPos missing=cells.get(0);TileEntity original=root.getWorld().getTileEntity(missing);
        net.minecraft.nbt.NBTTagCompound saved=original.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        net.minecraft.nbt.NBTTagCompound rootTag=root.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        try{
            net.minecraft.nbt.NBTTagCompound delayed=rootTag.copy();delayed.setDouble("GearProgress",.8);delayed.getCompoundTag("ArmPayload").setBoolean("Materialized",false);root.readFromNBT(delayed);ArmSurfaceActivity.refresh(root);
            require(SurfaceArmHandoff.hideNative((TileEntityTurntableSurface)original),"native blocks arriving before the endpoint packet remain hidden");
            net.minecraft.nbt.NBTTagList journal=new net.minecraft.nbt.NBTTagList();net.minecraft.nbt.NBTTagCompound entry=new net.minecraft.nbt.NBTTagCompound();entry.setInteger("Cell",0);entry.setLong("Pos",missing.toLong());journal.appendTag(entry);delayed.getCompoundTag("ArmPayload").setTag("OwnershipJournal",journal);root.readFromNBT(delayed);ArmSurfaceActivity.refresh(root);
            require(!SurfaceArmHandoff.hideNative((TileEntityTurntableSurface)original),"pending ownership journal retains its native renderer");
        }finally{root.readFromNBT(rootTag);ArmSurfaceActivity.refresh(root);}
        root.surfaceVisualHandoff=null;
        try{
            root.getWorld().removeTileEntity(missing);
            require(SurfaceArmHandoff.fallback(root),"carried decal retained before native tile packet");
            require(SurfaceArmHandoff.hideNative((TileEntityTurntableSurface)root.getWorld().getTileEntity(cells.get(1))),"partial native pad hidden");
        }finally{root.getWorld().setTileEntity(missing,TileEntity.create(root.getWorld(),saved));}
        SurfaceArmHandoff.changed((TileEntityTurntableSurface)root.getWorld().getTileEntity(missing));
        require(!SurfaceArmHandoff.fallback(root),"native renderer takes over after all tile packets");
        require(!SurfaceArmHandoff.hideNative((TileEntityTurntableSurface)root.getWorld().getTileEntity(cells.get(1))),"native pad visible once complete");
        try{root.getWorld().removeTileEntity(missing);require(!SurfaceArmHandoff.fallback(root),"mining after handoff must not resurrect a carried decal");}
        finally{root.getWorld().setTileEntity(missing,TileEntity.create(root.getWorld(),saved));}
        System.out.println("[vandorlabs] surface-arm-handoff PASS delayed native tile packet one renderer complete publication no mined ghost");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Surface handoff: "+message);}
    private SurfaceArmHandoffChecks(){}
}
