package com.vandorlabs.neoforge.screen;
import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.neoforge.ModernHousingTags;
import net.minecraft.nbt.*;
/** Immutable scalar screen choices. Geometry, control rows and owned items have separate authorities. */
public record NativeScreenSettings(ScreenData scalar,HousingSettings housing,NativeScreenActivation activation,ChannelList channels,int channelLevel,boolean inverted,int primarySurface,int secondarySurface,boolean ceilingMounted,int ceilingPosition,int surfaceLayout,boolean surfaceTileSides) {
 public interface Catalog {String defaultInput();boolean validInput(String value);boolean validMaterial(int value);int material(int value);}
 public NativeScreenSettings{java.util.Objects.requireNonNull(scalar);java.util.Objects.requireNonNull(housing);java.util.Objects.requireNonNull(activation);java.util.Objects.requireNonNull(channels);if(channelLevel<0||channelLevel>15||ceilingPosition < -1||ceilingPosition>2||!TextureLayoutMode.valid(surfaceLayout))throw new IllegalArgumentException("Invalid screen scalar");}
 public static NativeScreenSettings fresh(Catalog catalog){var tags=new ModernHousingTags(new CompoundTag());HousingSettings housing=HousingSettings.read(tags,0,false,catalog::material,catalog::validMaterial);return new NativeScreenSettings(new ScreenData("engineering_screen",false,2,true,1,catalog.defaultInput(),catalog.defaultInput(),-1,false,0,false,0),housing,NativeScreenActivation.LEGACY,ChannelList.EMPTY,0,false,-1,-1,false,-1,1,true);}
 public static NativeScreenSettings read(CompoundTag root,Catalog catalog){
  var tags=new ModernHousingTags(root);ScreenData decoded=ScreenData.read(tags,"engineering_screen",catalog.defaultInput(),catalog::validInput);var activation=NativeScreenActivation.read(root);HousingSettings housing=HousingSettings.read(tags,0,false,catalog::material,catalog::validMaterial);
  ChannelList channels=ChannelList.of(decoded.redstoneChannel);if(root.contains("RedstoneChannels",Tag.TAG_INT_ARRAY))try{channels=ChannelList.of(root.getIntArray("RedstoneChannels"));}catch(IllegalArgumentException invalid){/* Legacy scalar fallback. */}
  ScreenData scalar=new ScreenData(decoded.selectedScreen,activation.data().mode<0?decoded.redstoneEnabled:activation.data().mode>0,decoded.displayMode,decoded.framed,decoded.animationSpeedIndex,decoded.inputPanel,decoded.secondaryInputPanel,decoded.wallPosition,decoded.smallInput,channels.first(),decoded.channelSignal,housing.mainTexture);
  boolean tile=!root.contains("SurfaceTileSides",Tag.TAG_BYTE)||root.getBoolean("SurfaceTileSides");int layout=root.getInt("SurfaceTextureLayout");if(!root.contains("SurfaceTextureLayout",Tag.TAG_INT)||!TextureLayoutMode.valid(layout))layout=tile?1:0;
  return new NativeScreenSettings(scalar,housing,activation,channels,root.contains("ChannelLevel",Tag.TAG_INT)?Math.max(0,Math.min(15,root.getInt("ChannelLevel"))):scalar.channelSignal?15:0,root.getBoolean("RedstoneInverted"),surface(root,"PrimarySurfaceTexture",catalog),surface(root,"SecondarySurfaceTexture",catalog),root.getBoolean("CeilingMounted"),root.contains("CeilingPosition",Tag.TAG_INT)?Math.max(0,Math.min(2,root.getInt("CeilingPosition"))):-1,layout,tile);
 }
 private static int surface(CompoundTag root,String key,Catalog catalog){int value=root.getInt(key);return value==-4||value==-3?value:root.contains(key,Tag.TAG_INT)&&value>=0?catalog.material(value):-1;}
 public void write(CompoundTag root){var tags=new ModernHousingTags(root);scalar.write(tags);housing.write(tags);activation.write(root);root.putIntArray("RedstoneChannels",channels.toArray());root.putBoolean("RedstoneInverted",inverted);root.putInt("ChannelLevel",channelLevel);root.putInt("PrimarySurfaceTexture",primarySurface);root.putInt("SecondarySurfaceTexture",secondarySurface);root.putBoolean("CeilingMounted",ceilingMounted);if(ceilingPosition>=0)root.putInt("CeilingPosition",ceilingPosition);else root.remove("CeilingPosition");if(scalar.wallPosition<0)root.remove("wallPosition");root.putInt("SurfaceTextureLayout",surfaceLayout);root.putBoolean("SurfaceTileSides",surfaceTileSides);}
 /** Keep unknown/specialized data opaque until its own adapter can install it. */
 public static final class Saved {
  private final CompoundTag original;public final NativeScreenSettings settings;
  public Saved(CompoundTag root,Catalog catalog){original=root.copy();settings=read(original,catalog);}
  public CompoundTag unchanged(){return original.copy();}
  public CompoundTag edited(NativeScreenSettings next){CompoundTag copy=original.copy();next.write(copy);if(next.activation.equals(settings.activation)){if(original.contains("DisplayActivation"))copy.put("DisplayActivation",original.get("DisplayActivation").copy());else copy.remove("DisplayActivation");}return copy;}
 }
}
