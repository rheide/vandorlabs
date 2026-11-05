package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.DoorGlassData;
import net.minecraft.nbt.*;

/** Native adapter for the exact door-only glazing contract. */
public final class NativeDoorGlass extends DoorGlassData {
    private static final java.util.function.IntPredicate MATERIAL=value->value==-1||ModernFinishCatalog.validChoice(value);
    public static final NativeDoorGlass DEFAULT=new NativeDoorGlass(ORIGINAL,0,1,1);
    private static final Tag<CompoundTag> TAG=new Tag<>(){
        public boolean compound(CompoundTag root,String key){return root.contains(key,net.minecraft.nbt.Tag.TAG_COMPOUND);}public CompoundTag child(CompoundTag root,String key){return root.getCompound(key);}public CompoundTag create(){return new CompoundTag();}
        public boolean integer(CompoundTag tag,String key){return tag.contains(key,net.minecraft.nbt.Tag.TAG_INT);}public int getInt(CompoundTag tag,String key){return tag.getInt(key);}public void putInt(CompoundTag tag,String key,int value){tag.putInt(key,value);}
        public void put(CompoundTag root,String key,CompoundTag child){root.put(key,child);}public void remove(CompoundTag root,String key){root.remove(key);}
    };
    private static final Factory<NativeDoorGlass> FACTORY=new Factory<>(){public NativeDoorGlass original(){return DEFAULT;}public NativeDoorGlass create(int m,int s,int t,int l){return new NativeDoorGlass(m,s,t,l);}};
    public NativeDoorGlass(int material,int shade,int tint,int layout){super(material,shade,tint,layout,MATERIAL);}
    public static NativeDoorGlass read(CompoundTag tag){return DoorGlassData.read(TAG,tag,MATERIAL,FACTORY);}
    public void write(CompoundTag tag){DoorGlassData.write(TAG,tag,material,shade,tint,layout);}
    public boolean same(NativeDoorGlass other){return other!=null&&material==other.material&&shade==other.shade&&tint==other.tint&&layout==other.layout;}
}
