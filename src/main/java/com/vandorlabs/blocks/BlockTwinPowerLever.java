package com.vandorlabs.blocks;

/** Twin-arm Power Lever; legacy IDs retain their original default size. */
public final class BlockTwinPowerLever extends BlockIndustrialLever {
    private final boolean large;
    public BlockTwinPowerLever(String name,boolean large){super(name);this.large=large;}
    public boolean isLegacyLarge(){return large;}
    @Override public int defaultSize(){return large?1:0;}
}
