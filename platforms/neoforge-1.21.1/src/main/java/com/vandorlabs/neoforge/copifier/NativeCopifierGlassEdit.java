package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.glass.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.*;
import com.vandorlabs.tiles.GlassTextures;
import net.minecraft.nbt.*;

/** Ordinary-Glass semantic projection and copied edits; physical fluids/platform data stay with the host. */
public final class NativeCopifierGlassEdit {
 public record Plan(NativeGlassSettings settings,boolean applicable){}
 public static CompoundTag capture(NativeGlassSettings settings){var g=settings.glass();var out=new CompoundTag();out.putBoolean("join",g.join);out.putInt("glass_shade",g.shade);out.putInt("glass_size",g.size);out.putInt("glass_tint",g.tint);out.putBoolean("framed",g.framed);out.putInt("glass_material",g.material);var layout=new CompoundTag();layout.putBoolean("Stretch",g.stretch);layout.putInt("Mode",g.textureLayout());settings.playback().writeSettings(layout);out.put("glass_layout",layout);var frame=new CompoundTag();settings.frames().write(new ModernHousingTags(frame));out.put("FrameAppearance",frame);return out;}
 public static Plan prepare(NativeGlassSettings current,CompoundTag values){
  if(!NativeCopiedSettingsValidation.valid(values))throw new IllegalArgumentException("Malformed copied settings");
  var g=current.glass();int size=g.size,shade=g.shade,tint=g.tint,material=g.material,layout=g.layout;boolean framed=g.framed,join=g.join,stretch=g.stretch,applicable=false;var frames=current.frames();var playback=current.playback();
  if(values.contains("glass_size",Tag.TAG_INT)){int value=values.getInt("glass_size");if(value>=0&&value<=2)size=Math.min(value,1);applicable=true;}
  if(values.contains("glass_shade",Tag.TAG_INT)){int value=values.getInt("glass_shade");if(GlassTextures.valid(value))shade=value;applicable=true;}
  if(values.contains("glass_tint",Tag.TAG_INT)||values.contains("framed",Tag.TAG_BYTE)){int value=values.contains("glass_tint",Tag.TAG_INT)?values.getInt("glass_tint"):tint;if(GlassTextures.validTint(value)){tint=value;if(values.contains("framed",Tag.TAG_BYTE))framed=values.getBoolean("framed");}applicable=true;}
  if(values.contains("glass_material",Tag.TAG_INT)){int value=values.getInt("glass_material");if(value==-1||ModernFinishCatalog.validChoice(value))material=value;applicable=true;}
  if(values.contains("join",Tag.TAG_BYTE)){join=values.getBoolean("join");applicable=true;}
  if(values.contains("glass_layout",Tag.TAG_COMPOUND)&&values.getCompound("glass_layout").contains("Stretch",Tag.TAG_BYTE)){
   var copied=values.getCompound("glass_layout");int mode=copied.contains("Mode",Tag.TAG_INT)?copied.getInt("Mode"):copied.getBoolean("Stretch")?0:1;
   if(mode!=g.textureLayout()){layout=mode;stretch=mode!=1;}
   if(copied.contains(GlassPlaybackData.MODE,Tag.TAG_BYTE))playback=new PanelPlayback(copied.getByte(GlassPlaybackData.MODE),copied.contains(GlassPlaybackData.SPEED,Tag.TAG_BYTE)?copied.getByte(GlassPlaybackData.SPEED):1);applicable=true;
  }
  if(values.contains("FrameAppearance",Tag.TAG_COMPOUND)){frames=FrameSettings.read(new ModernHousingTags(values.getCompound("FrameAppearance")),NativeWallEntity::validFrame,NativeWallEntity::validSurface,ModernFinishCatalog::validChoice);applicable=true;}
  var next=new NativeGlassSettings(new ProgrammableGlassSettings(shade,material,layout,stretch,tint,framed,size,join,g.panelFacing,g.legacySize),frames,playback);
  return new Plan(next,applicable);
 }
 private NativeCopifierGlassEdit(){}
}
