package com.vandorlabs.neoforge.glass;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.ProgrammableGlassSettings;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Pure preflight of the dedicated ordinary-glass item callback after generic tile-NBT merge. */
public final class NativeGlassPlacementPolicy {
 public record Result(NativeGlassSettings settings,int stateSize){}
 public static Result place(NativeGlassSettings base,CompoundTag outer,int stateSize,int depth,int placerOppositeHorizontal){
  if(stateSize<0||stateSize>2||depth<0||depth>2||placerOppositeHorizontal<0||placerOppositeHorizontal>3)throw new IllegalArgumentException("Glass placement pose");
  int layout=outer!=null&&outer.contains("LayoutMode",Tag.TAG_INT)?outer.getInt("LayoutMode"):outer!=null&&outer.getBoolean("Stretch")?0:1;
  if(!TextureLayoutMode.valid(layout))throw new IllegalArgumentException("Invalid glass texture layout");
  var old=base.glass();int size=outer==null?stateSize:outer.getInt("Size"),shade=outer==null?0:outer.getInt("Shade"),material=outer!=null&&outer.contains("Material",Tag.TAG_INT)?outer.getInt("Material"):-1,tint=outer!=null&&outer.contains("Tint",Tag.TAG_INT)?outer.getInt("Tint"):1;
  boolean framed=outer==null||!outer.contains("Framed")||outer.getBoolean("Framed"),join=outer==null||!outer.contains("Join")||outer.getBoolean("Join"),sameLayout=old.textureLayout()==layout;
  var glass=new ProgrammableGlassSettings(GlassTextures.valid(shade)?shade:old.shade,material==-1||ModernFinishCatalog.validChoice(material)?material:old.material,sameLayout?old.layout:layout,sameLayout?old.stretch:layout!=1,GlassTextures.validTint(tint)?tint:old.tint,GlassTextures.validTint(tint)?framed:old.framed,size>=0&&size<=2?Math.min(size,1):old.size,join,placerOppositeHorizontal,false);
  var auxiliary=outer==null?base:NativeGlassSettings.read(outer);
  return new Result(new NativeGlassSettings(glass,auxiliary.frames(),auxiliary.playback()),depth==0&&size>=0&&size<=2?size:stateSize);
 }
 private NativeGlassPlacementPolicy(){}
}
