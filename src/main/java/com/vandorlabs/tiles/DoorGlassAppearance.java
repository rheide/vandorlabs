package com.vandorlabs.tiles;
import net.minecraft.nbt.NBTTagCompound;
/** Immutable optional glazing finish; original doors share one default value. */
public final class DoorGlassAppearance {
 public static final int ORIGINAL=-2;
 public static final DoorGlassAppearance DEFAULT=new DoorGlassAppearance(ORIGINAL,0,1,1);
 public final int material,shade,tint,layout;
 public DoorGlassAppearance(int material,int shade,int tint,int layout){if(!valid(material,shade,tint,layout))throw new IllegalArgumentException("Invalid door glass finish");this.material=material;this.shade=shade;this.tint=tint;this.layout=layout;}
 public static boolean valid(int material,int shade,int tint,int layout){return (material==ORIGINAL||GlassMaterials.valid(material))&&GlassTextures.valid(shade)&&GlassTextures.validTint(tint)&&com.vandorlabs.render.TextureLayoutMode.valid(layout);}
 public void write(NBTTagCompound root){if(material==ORIGINAL){root.removeTag("DoorGlass");return;}NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Material",material);tag.setInteger("Shade",shade);tag.setInteger("Tint",tint);tag.setInteger("Layout",layout);root.setTag("DoorGlass",tag);}
 public static DoorGlassAppearance read(NBTTagCompound root){if(!root.hasKey("DoorGlass",10))return DEFAULT;NBTTagCompound t=root.getCompoundTag("DoorGlass");int m=t.hasKey("Material",3)?t.getInteger("Material"):ORIGINAL,s=t.getInteger("Shade"),tint=t.hasKey("Tint",3)?t.getInteger("Tint"):1,l=t.hasKey("Layout",3)?t.getInteger("Layout"):1;return valid(m,s,tint,l)?new DoorGlassAppearance(m,s,tint,l):DEFAULT;}
 public boolean equals(Object o){if(!(o instanceof DoorGlassAppearance))return false;DoorGlassAppearance a=(DoorGlassAppearance)o;return material==a.material&&shade==a.shade&&tint==a.tint&&layout==a.layout;}
 public int hashCode(){return ((material*31+shade)*31+tint)*31+layout;}
}
