package com.vandorlabs.drives;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/** Cosmetic drive controls; these machines never supply vehicle propulsion. */
public final class DriveSettings {
    public static final DriveSettings DEFAULT=new DriveSettings(false,false,1,8);
    public final boolean levels,inverted;public final int particles,threshold;
    public DriveSettings(boolean levels,boolean inverted,int particles,int threshold){if(particles<0||particles>3||threshold<0||threshold>15)throw new IllegalArgumentException("Invalid drive settings");this.levels=levels;this.inverted=inverted;this.particles=particles;this.threshold=threshold;}
    public int activation(boolean redstone,int raw){return !redstone?15:inverted?15-clamp(raw):clamp(raw);}
    public boolean plume(boolean active,int level){return active&&particles>0&&(!levels||level>=threshold);}
    public int count(int level){return 8*(particles==2?3:particles==3?6:1);}
    public static int clamp(int level){return Math.max(0,Math.min(15,level));}
    public void write(NBTTagCompound tag){tag.setBoolean("DriveSignalLevels",levels);tag.setBoolean("DriveInverted",inverted);tag.setInteger("DriveParticles",particles);tag.setInteger("DriveParticleThreshold",threshold);}
    public static DriveSettings read(NBTTagCompound tag){return new DriveSettings(tag.getBoolean("DriveSignalLevels"),tag.getBoolean("DriveInverted"),tag.hasKey("DriveParticles",3)?Math.max(0,Math.min(3,tag.getInteger("DriveParticles"))):1,tag.hasKey("DriveParticleThreshold",3)?Math.max(0,Math.min(15,tag.getInteger("DriveParticleThreshold"))):8);}
    public void write(ByteBuf buffer){buffer.writeBoolean(levels);buffer.writeBoolean(inverted);buffer.writeByte(particles);buffer.writeByte(threshold);}
    public static DriveSettings read(ByteBuf buffer){if(buffer.readableBytes()!=4)throw new IllegalArgumentException("Invalid drive settings length");return new DriveSettings(buffer.readBoolean(),buffer.readBoolean(),buffer.readUnsignedByte(),buffer.readUnsignedByte());}
}
