package com.vandorlabs.gear.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.property.IUnlistedProperty;

/** One immutable unlisted render value keeps the BlockStateContainer at four states. */
public final class TrackAppearance {
    public enum Mount { NONE, SOCKET, TOP, TOP_HIGH }
    public static final TrackAppearance SINGLE = new TrackAppearance(0, Mount.SOCKET, 0, 0);
    public static final IUnlistedProperty<TrackAppearance> PROPERTY = new IUnlistedProperty<TrackAppearance>() {
        @Override public String getName() { return "appearance"; }
        @Override public boolean isValid(TrackAppearance value) { return value != null; }
        @Override public Class<TrackAppearance> getType() { return TrackAppearance.class; }
        @Override public String valueToString(TrackAppearance value) { return Integer.toHexString(value.key()); }
    };

    public static final IUnlistedProperty<Integer> STYLE=new IUnlistedProperty<Integer>(){
        @Override public String getName(){return "track_style";}@Override public boolean isValid(Integer value){return value!=null&&value>=0&&value<3;}@Override public Class<Integer> getType(){return Integer.class;}@Override public String valueToString(Integer value){return value.toString();}
    };
    public final int beltMask;
    public final Mount mount;
    /** World N/E/S/W bits 1/2/4/8; independent of travel facing. */
    public final int sideMask;
    public final int lowerSideMask;
    /** Local right=2 and left=8 bridges between unequal longitudinal runs. */
    public final int bridgeMask;

    public TrackAppearance(int beltMask, Mount mount, int sideMask, int bridgeMask) {this(beltMask,mount,sideMask,bridgeMask,0);}
    public TrackAppearance(int beltMask, Mount mount, int sideMask, int bridgeMask,int lowerSideMask) {
        int key=com.vandorlabs.gear.track.core.TrackVisualState.validatedKey(beltMask,mount==null?-1:mount.ordinal(),sideMask,bridgeMask,lowerSideMask);
        this.beltMask=key&15;this.mount=mount;this.sideMask=key>>6&15;this.bridgeMask=key>>10&10;this.lowerSideMask=key>>14&15;
    }

    public int key() { return com.vandorlabs.gear.track.core.TrackVisualState.key(beltMask,mount.ordinal(),sideMask,bridgeMask,lowerSideMask); }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Key", key());
        return tag;
    }

    public static TrackAppearance read(NBTTagCompound tag) {
        int key = tag.getInteger("Key");
        return new TrackAppearance(key & 15, Mount.values()[key >> 4 & 3], key >> 6 & 15, key >> 10 & 10,key >> 14 & 15);
    }

    @Override public boolean equals(Object other) { return other instanceof TrackAppearance && ((TrackAppearance) other).key() == key(); }
    @Override public int hashCode() { return key(); }
}
