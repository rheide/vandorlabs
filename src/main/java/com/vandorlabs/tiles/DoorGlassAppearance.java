package com.vandorlabs.tiles;
import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.persistence.DoorGlassData;
/** Immutable optional glazing finish; original doors share one default value. */
public final class DoorGlassAppearance extends DoorGlassData {
 private static final java.util.function.IntPredicate MATERIAL_VALID=GlassMaterials::valid;
 public static final DoorGlassAppearance DEFAULT=new DoorGlassAppearance(ORIGINAL,0,1,1);
 private static final Tag<NBTTagCompound> TAG=new Tag<NBTTagCompound>(){
  public boolean compound(NBTTagCompound root,String key){return root.hasKey(key,10);}public NBTTagCompound child(NBTTagCompound root,String key){return root.getCompoundTag(key);}public NBTTagCompound create(){return new NBTTagCompound();}
  public boolean integer(NBTTagCompound tag,String key){return tag.hasKey(key,3);}public int getInt(NBTTagCompound tag,String key){return tag.getInteger(key);}public void putInt(NBTTagCompound tag,String key,int value){tag.setInteger(key,value);}
  public void put(NBTTagCompound root,String key,NBTTagCompound child){root.setTag(key,child);}public void remove(NBTTagCompound root,String key){root.removeTag(key);}
 };
 private static final Factory<DoorGlassAppearance> FACTORY=new Factory<DoorGlassAppearance>(){public DoorGlassAppearance original(){return DEFAULT;}public DoorGlassAppearance create(int material,int shade,int tint,int layout){return new DoorGlassAppearance(material,shade,tint,layout);}};
 public DoorGlassAppearance(int material,int shade,int tint,int layout){super(material,shade,tint,layout,MATERIAL_VALID);}
 public static boolean valid(int material,int shade,int tint,int layout){return DoorGlassData.valid(material,shade,tint,layout,MATERIAL_VALID);}
 public void write(NBTTagCompound root){DoorGlassData.write(TAG,root,material,shade,tint,layout);}
 public static DoorGlassAppearance read(NBTTagCompound root){return DoorGlassData.read(TAG,root,MATERIAL_VALID,FACTORY);}
 public boolean equals(Object o){if(!(o instanceof DoorGlassAppearance))return false;DoorGlassAppearance a=(DoorGlassAppearance)o;return material==a.material&&shade==a.shade&&tint==a.tint&&layout==a.layout;}
 public int hashCode(){return ((material*31+shade)*31+tint)*31+layout;}
}
