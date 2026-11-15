package com.vandorlabs.neoforge.legacylanding;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.redstone.ChannelList;
import java.util.*;
import net.minecraft.nbt.*;
/** Own-field codec only. The adapter retains original opaque/platform data separately. */
public final class NativeLegacyLandingData {
 public record Configuration(int mode,ChannelList channels,int pixels,int size){public Configuration{Objects.requireNonNull(channels);if(!LegacyLandingSettings.valid(mode,pixels,size,false))throw new IllegalArgumentException("Invalid telescopic settings");}}
 public record State(Configuration configuration,float progress,boolean lastSignal,boolean owned,int ownerX,int ownerY,int ownerZ,boolean legacy,UUID identity,long revision){public State{Objects.requireNonNull(configuration);if(!Float.isFinite(progress)||progress<0||progress>4||revision<0||!legacy&&identity==null||owned&&!LegacyLandingLayout.ownerOffset(ownerX,ownerY,ownerZ)||!owned&&(ownerX!=0||ownerY!=0||ownerZ!=0))throw new IllegalArgumentException("Invalid telescopic state");}}
 public static Configuration defaults(){return new Configuration(1,ChannelList.EMPTY,16,0);}
 public static boolean configurable(NativeLegacyLandingKind kind,Configuration settings){return LegacyLandingSettings.valid(settings.mode,settings.pixels,settings.size,kind.nonWheeled);}
 public static State read(CompoundTag tag){
  for(String key:List.of("ExtensionPixels","GearSize","RedstoneMode","RedstoneChannel","GearOwnerDistance","GearOwnerX","GearOwnerZ"))if(tag.contains(key)&&!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Malformed legacy gear integer "+key);
  for(String key:List.of("LastSignal","GearHasOwner","NativeLegacyProxyUpgrade"))if(tag.contains(key)&&(!tag.contains(key,Tag.TAG_BYTE)||tag.getByte(key)<0||tag.getByte(key)>1))throw new IllegalArgumentException("Malformed legacy gear flag "+key);
  if(tag.contains("Progress")&&!tag.contains("Progress",Tag.TAG_FLOAT))throw new IllegalArgumentException("Malformed legacy progress");
  float progress=tag.getFloat("Progress");if(!Float.isFinite(progress)||progress<0||progress>4)throw new IllegalArgumentException("Unsupported saved legacy progress");
  boolean legacy=!tag.contains("NativeLegacyLandingSchema");UUID identity=null;long revision=0;
  if(!legacy){if(!tag.contains("NativeLegacyLandingSchema",Tag.TAG_INT)||tag.getInt("NativeLegacyLandingSchema")!=1||!tag.hasUUID("NativeLegacyLandingIdentity")||!tag.contains("NativeLegacyLandingRevision",Tag.TAG_LONG)||tag.getLong("NativeLegacyLandingRevision")<0)throw new IllegalArgumentException("Unsupported legacy landing schema");identity=tag.getUUID("NativeLegacyLandingIdentity");revision=tag.getLong("NativeLegacyLandingRevision");}
  int fallback=tag.getInt("RedstoneChannel");if(fallback<0)throw new IllegalArgumentException("Negative legacy channel");ChannelList channels=ChannelList.of(fallback);
  if(tag.contains("RedstoneChannels")){if(!tag.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))throw new IllegalArgumentException("Malformed legacy channel list");int[] values=tag.getIntArray("RedstoneChannels");if(values.length>ChannelList.MAX_CHANNELS)throw new IllegalArgumentException("Legacy channel limit");channels=ChannelList.of(values);}
  int mode=LegacyLandingSettings.mode(tag.contains("RedstoneMode"),tag.getInt("RedstoneMode")),pixels=LegacyLandingSettings.pixels(tag.contains("ExtensionPixels"),tag.getInt("ExtensionPixels")),size=LegacyLandingSettings.size(tag.getInt("GearSize"));
  if(!legacy&&(tag.contains("RedstoneMode")&&tag.getInt("RedstoneMode")!=mode||tag.contains("ExtensionPixels")&&tag.getInt("ExtensionPixels")!=pixels||tag.getInt("GearSize")!=size))throw new IllegalArgumentException("Out-of-range native telescopic fields");
  int x=tag.getInt("GearOwnerX"),y=tag.getInt("GearOwnerDistance"),z=tag.getInt("GearOwnerZ");boolean owned=tag.getBoolean("GearHasOwner")||y>0;if(owned&&!LegacyLandingLayout.ownerOffset(x,y,z)||!owned&&(x!=0||y!=0||z!=0))throw new IllegalArgumentException("Legacy owner outside bounded footprint");
  return new State(new Configuration(mode,channels,pixels,size),progress,tag.getBoolean("LastSignal"),owned,x,y,z,legacy,identity,revision);
 }
 public static void configuration(CompoundTag tag,Configuration settings){tag.putInt("GearSize",settings.size);tag.putInt("ExtensionPixels",settings.pixels);tag.putInt("RedstoneMode",settings.mode);tag.putInt("RedstoneChannel",settings.channels.first());tag.putIntArray("RedstoneChannels",settings.channels.toArray());}
 public static void write(CompoundTag tag,State state){configuration(tag,state.configuration);tag.putFloat("Progress",state.progress);tag.putBoolean("LastSignal",state.lastSignal);for(String key:List.of("GearHasOwner","GearOwnerX","GearOwnerDistance","GearOwnerZ"))tag.remove(key);if(state.owned){tag.putBoolean("GearHasOwner",true);tag.putInt("GearOwnerX",state.ownerX);tag.putInt("GearOwnerDistance",state.ownerY);tag.putInt("GearOwnerZ",state.ownerZ);}if(!state.legacy){tag.putInt("NativeLegacyLandingSchema",1);tag.putUUID("NativeLegacyLandingIdentity",Objects.requireNonNull(state.identity));tag.putLong("NativeLegacyLandingRevision",state.revision);}}
 private NativeLegacyLandingData(){}
}
