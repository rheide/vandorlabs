package com.vandorlabs.client;
import com.vandorlabs.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.nbt.NBTTagCompound;
/** Actual cell ownership and configured projection; no renderer or synthetic grid. */
public final class SegmentedCellArtworkChecks {
 public static void main(String[] args){net.minecraft.init.Bootstrap.register();net.minecraft.tileentity.TileEntity.register("segmented_cell_reference",SegmentedDoorTile.class);com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();int checks=0;
  SegmentedDoorTile old=new SegmentedDoorTile();NBTTagCompound plain=old.configuration();if(plain.hasKey(SegmentedCellArtwork.FIELD))throw new AssertionError("old default gained override");checks++;
  SegmentedDoorTile fresh=new SegmentedDoorTile();fresh.applyItemConfiguration(plain,ChannelList.EMPTY);if(fresh.cellArtwork.overridden||fresh.effectiveTexture()!=fresh.texture)throw new AssertionError("old configured default changed");checks++;
  old.cellArtwork=SegmentedCellArtwork.explicit(-1,SegmentedDoorTile::validTexture);NBTTagCompound config=old.configuration(),item=old.itemSettings();if(!config.hasKey(SegmentedCellArtwork.FIELD,3)||!item.hasKey(SegmentedCellArtwork.FIELD,3))throw new AssertionError("local default omitted");checks++;
  fresh.applyItemConfiguration(item,ChannelList.EMPTY);if(!fresh.cellArtwork.equals(old.cellArtwork)||fresh.effectiveTexture()!=-1)throw new AssertionError("configured local default lost");checks++;
  NBTTagCompound saved=old.writeToNBT(new NBTTagCompound());SegmentedDoorTile reload=new SegmentedDoorTile();reload.readFromNBT(saved);if(!reload.cellArtwork.equals(old.cellArtwork))throw new AssertionError("saved override lost");checks++;
  reload.applyItemConfiguration(plain,ChannelList.EMPTY);if(reload.cellArtwork.overridden||reload.configuration().hasKey(SegmentedCellArtwork.FIELD))throw new AssertionError("inherited reset lost");checks++;
  NBTTagCompound wrong=item.copy();wrong.setString(SegmentedCellArtwork.FIELD,"-1");try{fresh.applyItemConfiguration(wrong,ChannelList.EMPTY);throw new AssertionError("wrong type accepted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("Actual Segmented cell artwork persistence PASS "+checks+" checks");
 }
}
