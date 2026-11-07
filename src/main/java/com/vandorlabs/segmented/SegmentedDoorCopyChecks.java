package com.vandorlabs.segmented;

import com.vandorlabs.items.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DoorGlassAppearance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;

/** Native configurable-segment copies must retain appearance, not assembly ownership. */
final class SegmentedDoorCopyChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("segmented copies: "+why);}
    static void run(EntityPlayerMP actor,SegmentedDoorTile source,SegmentedDoorTile target){
        SegmentedDoorSettings desired=new SegmentedDoorSettings(2,true,2,0,true,3,true,new DoorGlassAppearance(-1,2,3,2),2);
        source.configurePart(actor,ChannelList.of(64887,64888),20,false,false,-1,desired);
        source.configureCellTexture(actor,-1,false);
        ItemStack tool=new ItemStack(ModItems.DUPLIFIER);ItemDuplifier.copyFrom(source.getWorld(),source.getPos(),tool);
        NBTTagCompound copied=tool.getSubCompound(ItemDuplifier.SETTINGS_TAG);
        ItemStack picked=source.getBlockType().getPickBlock(source.getWorld().getBlockState(source.getPos()),new RayTraceResult(new Vec3d(source.getPos()),net.minecraft.util.EnumFacing.NORTH,source.getPos()),source.getWorld(),source.getPos(),actor);
        boolean duplifier=copied!=null&&copied.hasKey("segmented_door",10),pick=picked.hasTagCompound()&&picked.getSubCompound("BlockEntityTag")!=null&&picked.getSubCompound("BlockEntityTag").hasKey("SegmentedSettings",10);
        System.out.println("[vandorlabs][reprolab] segmented-copy-preflight duplifier="+duplifier+" middle-pick="+pick);
        require(duplifier&&pick,"both Duplifier and middle-click must capture complete segment settings");
        require(ItemDuplifier.applyTo(target.getWorld(),target.getPos(),tool,actor),"native Duplifier apply");
        require(target.cellArtwork.equals(source.cellArtwork)&&target.cellArtwork.overridden&&target.settings.equals(desired)&&target.ticksPerSection==20&&!target.localRedstone&&!target.signalOpens&&target.getRedstoneChannels().equals(source.getRedstoneChannels()),"all segment fields copied");
        NBTTagCompound data=picked.getSubCompound("BlockEntityTag");SegmentedDoorTile placed=new SegmentedDoorTile();placed.readFromNBT(data);
        require(placed.cellArtwork.equals(source.cellArtwork)&&placed.cellArtwork.overridden&&placed.settings.equals(desired)&&placed.ticksPerSection==20&&placed.getRedstoneChannels().equals(source.getRedstoneChannels()),"picked item retains all settings");
        for(String key:new String[]{"SegmentedIdentity","SegmentedSequence","SegmentedOpen","SegmentedFrom","SegmentedStarted","SegmentedTopOffset","SegmentedHeight","ContainedFluids"})require(!data.hasKey(key),"pick must not clone live ownership or fluid: "+key);
        System.out.println("[vandorlabs][reprolab] segmented-copies PASS Duplifier full local opening/glass and shared configuration pick item all properties ownership excluded");
    }
}
