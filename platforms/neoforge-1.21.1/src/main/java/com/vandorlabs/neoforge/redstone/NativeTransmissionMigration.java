package com.vandorlabs.neoforge.redstone;
import com.vandorlabs.redstone.*;
import net.minecraft.nbt.*;
/** Source development-module upgrade. Unknown item/Vehicle ownership remains inert and opaque. */
public final class NativeTransmissionMigration {
 private static final java.util.Set<String> MODULE_FIELDS=java.util.Set.of("id","Count","Damage","tag");
 private NativeTransmissionMigration(){}
 public static final class Result {
  private final CompoundTag original,normalized;private final boolean unsupported;
  private Result(CompoundTag original,CompoundTag normalized,boolean unsupported){this.original=original;this.normalized=normalized;this.unsupported=unsupported;}
  public boolean installable(){return !unsupported;}
  public CompoundTag install(){if(unsupported)throw new IllegalStateException("Unsupported installed screen module");return normalized.copy();}
  public CompoundTag unchanged(){return original.copy();}
  public CompoundTag referenceProjection(){return normalized.copy();}
 }
 public static Result read(CompoundTag source){CompoundTag original=source.copy();if(!source.getCompound("ForgeData").contains("VandorTransmitters",Tag.TAG_COMPOUND))return new Result(original,original.copy(),false);CompoundTag result=source.copy(),forge=result.getCompound("ForgeData"),modules=forge.getCompound("VandorTransmitters"),settings=forge.getCompound(TransmissionSettings.SETTINGS);boolean installed=false,unsupported=false;ChannelList main=ChannelList.EMPTY;
  for(String slot:new java.util.TreeSet<>(modules.getAllKeys())){if(!(modules.get(slot) instanceof CompoundTag item)){unsupported=true;continue;}for(String key:item.getAllKeys())if(!MODULE_FIELDS.contains(key))unsupported=true;if(item.contains("tag")&&!item.contains("tag",Tag.TAG_COMPOUND))unsupported=true;for(String key:item.getCompound("tag").getAllKeys())if(!key.equals("RedstoneChannels"))unsupported=true;if(item.getByte("Count")<=0)continue;int damage=Math.max(0,item.getShort("Damage"));if(!item.getString("id").equals("vandorlabs:redstone_transmitter")||damage>=4){unsupported=true;continue;}installed=true;ChannelList channels=channels(item.getCompound("tag"));if(slot.equals("main"))main=channels;if(slot.startsWith("cockpit_")){unsupported=true;continue;}if(slot.startsWith("row_")){String[] coordinates=slot.split("_");if(coordinates.length==3)try{int side=Integer.parseInt(coordinates[1]),row=Integer.parseInt(coordinates[2]);if(side>=0&&side<2){ListTag rows=result.getCompound(side==0?"RedstonePrimary":"RedstoneSecondary").getList("Rows",Tag.TAG_COMPOUND);if(row>=0&&row<rows.size()&&channels(rows.getCompound(row)).isEmpty())rows.getCompound(row).putIntArray("RedstoneChannels",channels.toArray());}}catch(NumberFormatException malformed){/* Unknown development coordinate has no target, matching source. */}}
  }
  if(installed){CompoundTag configured=settings.getCompound("main");configured.putInt("Range",64);if(!configured.contains("Channels",Tag.TAG_INT_ARRAY)&&!main.isEmpty())configured.putIntArray("Channels",main.toArray());settings.put("main",configured);forge.put(TransmissionSettings.SETTINGS,settings);}forge.remove("VandorTransmitters");forge.remove("VandorSharedScreenTransmitter");result.remove("TransmitterSchema");if(forge.isEmpty())result.remove("ForgeData");else result.put("ForgeData",forge);return new Result(original,result,unsupported);
 }
 private static ChannelList channels(CompoundTag tag){try{return ChannelList.of(tag.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException malformed){return ChannelList.EMPTY;}}
}
