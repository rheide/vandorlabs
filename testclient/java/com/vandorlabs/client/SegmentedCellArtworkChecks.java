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
  for(boolean wrongType:new boolean[]{false,true}){NBTTagCompound malformed=saved.copy();if(wrongType)malformed.setString(SegmentedCellArtwork.FIELD,"future-artwork");else malformed.setInteger(SegmentedCellArtwork.FIELD,-2);SegmentedDoorTile retained=new SegmentedDoorTile();retained.readFromNBT(malformed);NBTTagCompound again=retained.writeToNBT(new NBTTagCompound());if(retained.cellArtwork.overridden||!again.getTag(SegmentedCellArtwork.FIELD).equals(malformed.getTag(SegmentedCellArtwork.FIELD)))throw new AssertionError("unsupported saved artwork lost or installed");checks++;}
  for(boolean inherit:new boolean[]{false,true}){io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{new SegmentedDoorMessage(1,0,10,true,true,ChannelList.EMPTY).cellTexture(-1,inherit).toBytes(bytes);if(bytes.readableBytes()!=(inherit?8:12))throw new AssertionError("bounded cell command bytes");new SegmentedDoorMessage().fromBytes(bytes.duplicate());checks++;bytes.writeByte(0);try{new SegmentedDoorMessage().fromBytes(bytes.duplicate());throw new AssertionError("trailing cell command byte accepted");}catch(IllegalArgumentException expected){checks++;}}finally{bytes.release();}}
  int supplied=0;for(int choice=0;choice<com.vandorlabs.tiles.ScreenHousingTextures.IDS.length;choice++){
   String id=com.vandorlabs.tiles.ScreenHousingTextures.IDS[choice];
   if(com.vandorlabs.segmented.SegmentedArtworkPolicy.legacyGeneral(id)){if(!com.vandorlabs.tiles.ScreenHousingTextures.generalTexture(choice)||!com.vandorlabs.navigation.NavigationScreenSettings.validTexture(choice)||!com.vandorlabs.items.ItemProgrammableArmor.listedMaterial(choice))throw new AssertionError("recategorized saved general material narrowed "+id);checks++;}
   if(!com.vandorlabs.segmented.SegmentedArtworkPolicy.segmentedOnly(id))continue;supplied++;if(!SegmentedDoorTile.validTexture(choice)||com.vandorlabs.tiles.ScreenHousingTextures.generalTexture(choice))throw new AssertionError("supplied Segmented eligibility "+id);checks++;
   SegmentedDoorTile selected=new SegmentedDoorTile();selected.cellArtwork=SegmentedCellArtwork.explicit(choice,SegmentedDoorTile::validTexture);selected.settings=new SegmentedDoorSettings(0,false,1,0,true,2,true,SegmentedDoorSettings.DEFAULT.glass);NBTTagCompound configured=selected.itemSettings();SegmentedDoorTile installed=new SegmentedDoorTile();installed.applyItemConfiguration(configured,ChannelList.EMPTY);
   if(installed.effectiveTexture()!=choice||!installed.settings.window||installed.settings.shape!=2||!installed.settings.join)throw new AssertionError("supplied local item settings lost "+id);checks++;
   NBTTagCompound selectedSaved=selected.writeToNBT(new NBTTagCompound());installed.readFromNBT(selectedSaved);if(installed.effectiveTexture()!=choice||!installed.settings.window)throw new AssertionError("supplied saved local artwork lost "+id);checks++;
  }if(supplied!=6)throw new AssertionError("six supplied Segmented choices missing");checks++;
  System.out.println("Actual Segmented cell artwork persistence PASS "+checks+" checks");
 }
}
