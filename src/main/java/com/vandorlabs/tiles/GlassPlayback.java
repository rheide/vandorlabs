package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;
import com.vandorlabs.persistence.GlassPlaybackData;
import net.minecraft.tileentity.TileEntity;

/** Independent playback for a glazing insert; old saves remain static at normal speed. */
public final class GlassPlayback {
    private static final GlassPlaybackData.ByteTag<NBTTagCompound> TAG=new GlassPlaybackData.ByteTag<NBTTagCompound>(){
        @Override public void putByte(NBTTagCompound tag,String key,byte value){tag.setByte(key,value);}
        @Override public void remove(NBTTagCompound tag,String key){tag.removeTag(key);}
    };
    private final TileEntity owner;
    private int mode=1,speed=1;
    public GlassPlayback(TileEntity owner){this.owner=owner;}
    public int mode(){return mode;}
    public int speed(){return speed;}
    public static boolean valid(int value){return GlassPlaybackData.valid(value);}
    public void set(int mode,int speed){
        if(!valid(mode)||!valid(speed))throw new IllegalArgumentException("Invalid glass playback");
        if(this.mode==mode&&this.speed==speed)return;
        this.mode=mode;this.speed=speed;owner.markDirty();GlassMaterials.changed();
        if(owner.getWorld()!=null){net.minecraft.block.state.IBlockState state=owner.getWorld().getBlockState(owner.getPos());owner.getWorld().notifyBlockUpdate(owner.getPos(),state,state,3);}
    }
    public void write(NBTTagCompound tag){
        GlassPlaybackData.write(TAG,tag,mode,speed);
    }
    public void writeSettings(NBTTagCompound tag){GlassPlaybackData.writeSettings(TAG,tag,mode,speed);}
    public void read(NBTTagCompound tag){int oldMode=mode,oldSpeed=speed;mode=GlassPlaybackData.read(tag.hasKey("GlassPlaybackMode",1),tag.getByte("GlassPlaybackMode"));speed=GlassPlaybackData.read(tag.hasKey("GlassPlaybackSpeed",1),tag.getByte("GlassPlaybackSpeed"));if(oldMode!=mode||oldSpeed!=speed)GlassMaterials.changed();}
    /** Old Duplifier settings do not clear a newly configured playback state. */
    public void apply(NBTTagCompound tag){if(tag.hasKey("GlassPlaybackMode",1))set(tag.getByte("GlassPlaybackMode"),tag.hasKey("GlassPlaybackSpeed",1)?tag.getByte("GlassPlaybackSpeed"):1);}
    public boolean same(GlassPlayback other){return mode==other.mode&&speed==other.speed;}
    public static GlassPlayback of(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).glassPlayback:((TileEntityAnimatedScreenSelector)tile).glassPlayback;}
}
