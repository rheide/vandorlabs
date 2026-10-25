package com.vandorlabs.blocks;

/** Native hexagonal engines retain their existing solo mesh and join complete 2x2 or 3x3 groups. */
public final class BlockConnectedHexagonalPropulsionLight extends BlockConnectedPropulsionLight {
    private static final PropertyConnectedPart HEX_PART=PropertyConnectedPart.create("part",3);
    public BlockConnectedHexagonalPropulsionLight(String id,float depth){super(id,false,depth,3);}
    @Override public PropertyConnectedPart partProperty(){return HEX_PART;}
}
