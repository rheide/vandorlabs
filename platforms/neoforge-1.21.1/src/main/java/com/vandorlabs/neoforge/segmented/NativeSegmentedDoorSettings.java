package com.vandorlabs.neoforge.segmented;
import com.vandorlabs.persistence.SegmentedDoorData;
import com.vandorlabs.neoforge.door.NativeDoorGlass;
import net.minecraft.nbt.CompoundTag;
import io.netty.buffer.ByteBuf;
/** Immutable renderer/settings adapter. Saved owns a defensive opaque snapshot separately. */
public final class NativeSegmentedDoorSettings extends SegmentedDoorData {
 public static final NativeSegmentedDoorSettings DEFAULT=new NativeSegmentedDoorSettings(0,false,1,0,false,0,false,new NativeDoorGlass(-1,0,1,1),0);
 public static final NativeSegmentedDoorSettings LEGACY=DEFAULT.activation(-1);
 public final NativeDoorGlass glass;
 public NativeSegmentedDoorSettings(int d,boolean s,int l,int a,boolean w,int shape,boolean j,NativeDoorGlass g,int p){this(d,s,l,a,w,shape,j,g,p,16);}
 public NativeSegmentedDoorSettings(int d,boolean s,int l,int a,boolean w,int shape,boolean j,NativeDoorGlass g,int p,int tread){super(d,s,l,a,w,shape,j,g,p,tread);glass=g;}
 public NativeSegmentedDoorSettings tread(int value){return new NativeSegmentedDoorSettings(direction,straight,layout,activation,window,shape,join,glass,position,value);}
 public NativeSegmentedDoorSettings activation(int value){return new NativeSegmentedDoorSettings(direction,straight,layout,value,window,shape,join,glass,position,treadPixels);}
 public NativeSegmentedDoorSettings shared(NativeSegmentedDoorSettings s){return direction==s.direction&&straight==s.straight&&layout==s.layout&&activation==s.activation&&position==s.position&&treadPixels==s.treadPixels?this:new NativeSegmentedDoorSettings(s.direction,s.straight,s.layout,s.activation,window,shape,join,glass,s.position,s.treadPixels);}
 public static NativeSegmentedDoorSettings read(CompoundTag root){
  if(!root.contains("SegmentedSettings",10))return LEGACY;CompoundTag n=root.getCompound("SegmentedSettings");NativeDoorGlass g=NativeDoorGlass.read(n);if(g.material==NativeDoorGlass.ORIGINAL)g=DEFAULT.glass;
  try{return new NativeSegmentedDoorSettings(n.getInt("Direction"),n.getBoolean("Straight"),n.getInt("Layout"),n.getInt("Activation"),n.getBoolean("Window"),n.getInt("Shape"),n.getBoolean("Join"),g,n.getInt("Position"),n.contains("TreadPixels",3)?n.getInt("TreadPixels"):16);}catch(IllegalArgumentException invalid){return LEGACY;}
 }
 private static final String[] ROOT_FIELDS={"SegmentedTread","SegmentedPosition","SegmentedSlide","SegmentedStraight","SegmentedLayout","SegmentedWindow","SegmentedShape","SegmentedJoin","SegmentedGlassMaterial","SegmentedGlassShade","SegmentedGlassTint","SegmentedGlassLayout"};
 public void write(CompoundTag root){
  if(same(LEGACY)){root.remove("SegmentedSettings");for(String field:ROOT_FIELDS)root.remove(field);return;}
  if(treadPixels!=16)root.putInt("SegmentedTread",treadPixels);else root.remove("SegmentedTread");if(position!=0)root.putInt("SegmentedPosition",position);else root.remove("SegmentedPosition");root.putInt("SegmentedSlide",direction);root.putBoolean("SegmentedStraight",straight);root.putInt("SegmentedLayout",layout);root.putBoolean("SegmentedWindow",window);root.putInt("SegmentedShape",shape);root.putBoolean("SegmentedJoin",join);root.putInt("SegmentedGlassMaterial",glass.material);root.putInt("SegmentedGlassShade",glass.shade);root.putInt("SegmentedGlassTint",glass.tint);root.putInt("SegmentedGlassLayout",glass.layout);
  CompoundTag n=new CompoundTag();if(position!=0)n.putInt("Position",position);if(treadPixels!=16)n.putInt("TreadPixels",treadPixels);n.putInt("Direction",direction);n.putBoolean("Straight",straight);n.putInt("Layout",layout);n.putInt("Activation",activation);n.putBoolean("Window",window);n.putInt("Shape",shape);n.putBoolean("Join",join);glass.write(n);root.put("SegmentedSettings",n);
 }
 public void encode(ByteBuf b){b.writeByte(direction);b.writeBoolean(straight);b.writeByte(layout);b.writeByte(activation+1);b.writeBoolean(window);b.writeByte(shape);b.writeBoolean(join);b.writeInt(glass.material);b.writeByte(glass.shade);b.writeByte(glass.tint);b.writeByte(glass.layout);b.writeByte(position);b.writeByte(treadPixels);}
 public static NativeSegmentedDoorSettings decode(ByteBuf b){if(b.readableBytes()!=16&&b.readableBytes()!=15&&b.readableBytes()!=14)throw new IllegalArgumentException("Invalid Segmented Door settings length");int d=b.readUnsignedByte();boolean s=flag(b);int l=b.readUnsignedByte(),a=b.readUnsignedByte()-1;boolean w=flag(b);int sh=b.readUnsignedByte();boolean j=flag(b);NativeDoorGlass g=new NativeDoorGlass(b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte());return new NativeSegmentedDoorSettings(d,s,l,a,w,sh,j,g,b.isReadable()?b.readUnsignedByte():0,b.isReadable()?b.readUnsignedByte():16);}
 private static boolean flag(ByteBuf b){int v=b.readUnsignedByte();if(v>1)throw new IllegalArgumentException("Invalid Segmented Door flag");return v==1;}
 /** An unchanged save preserves every original field/type, including invalid and unknown data. */
 public static final class Saved {
  private final CompoundTag retained;public final NativeSegmentedDoorSettings settings;
  public Saved(CompoundTag root){retained=root.copy();settings=read(root);}
  public CompoundTag unchanged(){return retained.copy();}
  public CompoundTag edited(NativeSegmentedDoorSettings value){
   CompoundTag result=retained.copy(),old=result.contains("SegmentedSettings",10)?result.getCompound("SegmentedSettings").copy():null;
   value.write(result);if(old==null)return result;
   java.util.Set<String> known=java.util.Set.of("TreadPixels","Position","Direction","Straight","Layout","Activation","Window","Shape","Join","DoorGlass");
   boolean unknown=old.getAllKeys().stream().anyMatch(key->!known.contains(key));
   if(old.contains("DoorGlass",10))unknown|=old.getCompound("DoorGlass").getAllKeys().stream().anyMatch(key->!java.util.Set.of("Material","Shade","Tint","Layout").contains(key));
   if(!result.contains("SegmentedSettings",10)&&unknown){DEFAULT.write(result);result.getCompound("SegmentedSettings").putInt("Activation",-1);}
   if(result.contains("SegmentedSettings",10)){CompoundTag canonical=result.getCompound("SegmentedSettings");for(String key:old.getAllKeys())if(!known.contains(key))canonical.put(key,old.get(key).copy());
    if(old.contains("DoorGlass",10)&&canonical.contains("DoorGlass",10)){CompoundTag previous=old.getCompound("DoorGlass"),glass=canonical.getCompound("DoorGlass");for(String key:previous.getAllKeys())if(!java.util.Set.of("Material","Shade","Tint","Layout").contains(key))glass.put(key,previous.get(key).copy());}
   }return result;
  }
 }
 @Override public boolean equals(Object other){return other instanceof NativeSegmentedDoorSettings&&same((NativeSegmentedDoorSettings)other);}
 @Override public int hashCode(){int h=direction;h=h*31+(straight?1:0);h=h*31+layout;h=h*31+activation;h=h*31+(window?1:0);h=h*31+shape;h=h*31+(join?1:0);int g=((glass.material*31+glass.shade)*31+glass.tint)*31+glass.layout;h=(h*31+g)*31+position;return treadPixels==16?h:h*31+treadPixels;}
}
