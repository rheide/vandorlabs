package com.vandorlabs.tiles;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/** Independent playback for a glazing insert; old saves remain static at normal speed. */
public final class GlassPlayback {
    private final TileEntity owner;
    private int mode=1,speed=1;
    public GlassPlayback(TileEntity owner){this.owner=owner;}
    public int mode(){return mode;}
    public int speed(){return speed;}
    public static boolean valid(int value){return value>=0&&value<=2;}
    public void set(int mode,int speed){
        if(!valid(mode)||!valid(speed))throw new IllegalArgumentException("Invalid glass playback");
        if(this.mode==mode&&this.speed==speed)return;
        this.mode=mode;this.speed=speed;owner.markDirty();GlassMaterials.changed();
        if(owner.getWorld()!=null){net.minecraft.block.state.IBlockState state=owner.getWorld().getBlockState(owner.getPos());owner.getWorld().notifyBlockUpdate(owner.getPos(),state,state,3);}
    }
    public void write(NBTTagCompound tag){
        if(mode!=1||speed!=1){tag.setByte("GlassPlaybackMode",(byte)mode);tag.setByte("GlassPlaybackSpeed",(byte)speed);}
        else{tag.removeTag("GlassPlaybackMode");tag.removeTag("GlassPlaybackSpeed");}
    }
    public void writeSettings(NBTTagCompound tag){tag.setByte("GlassPlaybackMode",(byte)mode);tag.setByte("GlassPlaybackSpeed",(byte)speed);}
    public void read(NBTTagCompound tag){int oldMode=mode,oldSpeed=speed;mode=tag.hasKey("GlassPlaybackMode",1)&&valid(tag.getByte("GlassPlaybackMode"))?tag.getByte("GlassPlaybackMode"):1;speed=tag.hasKey("GlassPlaybackSpeed",1)&&valid(tag.getByte("GlassPlaybackSpeed"))?tag.getByte("GlassPlaybackSpeed"):1;if(oldMode!=mode||oldSpeed!=speed)GlassMaterials.changed();}
    /** Old Duplifier settings do not clear a newly configured playback state. */
    public void apply(NBTTagCompound tag){if(tag.hasKey("GlassPlaybackMode",1))set(tag.getByte("GlassPlaybackMode"),tag.hasKey("GlassPlaybackSpeed",1)?tag.getByte("GlassPlaybackSpeed"):1);}
    public boolean same(GlassPlayback other){return mode==other.mode&&speed==other.speed;}
    public static GlassPlayback of(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).glassPlayback:((TileEntityAnimatedScreenSelector)tile).glassPlayback;}
}
