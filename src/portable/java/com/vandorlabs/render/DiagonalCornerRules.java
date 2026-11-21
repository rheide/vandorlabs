package com.vandorlabs.render;

/**
 * Loader-free decisions for Diagonal Corner Glass, used by both Forge 1.12.2 and NeoForge so the
 * two versions place, join and edit corners identically. Sides and facings are numbered north,
 * east, south, west (0..3); sizes are {@link CornerGlassGeometry#TALL}, FULL and WIDE.
 */
public final class DiagonalCornerRules {
    private DiagonalCornerRules(){}

    /** Metadata for a corner saved before 2.12, when diagonal corners had only a depth 0..2. */
    public static int legacyMetadata(int turn,int depth){return (turn&3)|(depth==1?4:0)|(depth==2?8:0);}

    /**
     * Size for a newly placed corner: the clicked diagonal block's size (diagonal glass, wall,
     * porthole or another diagonal corner), else the item's configured size, else full width.
     * Pass -1 for "none".
     */
    public static int placementSize(int clickedDiagonalSize,int configuredSize){
        if(valid(clickedDiagonalSize))return clickedDiagonalSize;
        if(valid(configuredSize))return configuredSize;
        return CornerGlassGeometry.FULL;
    }
    public static boolean valid(int size){return size>=CornerGlassGeometry.TALL&&size<=CornerGlassGeometry.WIDE;}

    /**
     * Whether a diagonal wall, porthole or glass lets corners join it. Only full-width panels have
     * a diagonal join setting; a porthole also needs Join Portholes.
     */
    public static boolean wallAcceptsCorner(int wallSize,int diagonalJoin,boolean porthole,boolean joinPortholes){
        return wallSize!=CornerGlassGeometry.FULL||diagonalJoin!=1&&(!porthole||joinPortholes);
    }

    /**
     * Whether a diagonal wall across the corner's side {@code side} continues it. Both blocks must
     * allow joining, and the slope must cross the shared face exactly where the bevel does.
     */
    public static boolean continues(CornerGlassGeometry corner,boolean cornerJoins,int side,int wallFacing,int wallSize,boolean wallInverted,boolean wallAccepts){
        return cornerJoins&&wallAccepts&&corner.sloped()&&corner.joinsDiagonal(side,wallFacing,wallSize,wallInverted);
    }

    /** The side of the corner that faces a neighbour at {@code side} of it. */
    public static int opposite(int side){return (side+2)&3;}

    // Dialog: Size cycles like diagonal glass; Slope flips the lean, or for wide corners which half.

    public static String sizeLabel(int size){
        return com.vandorlabs.ui.WallAppearanceDialog.sizeLabel(size==CornerGlassGeometry.WIDE,size==CornerGlassGeometry.FULL);
    }
    public static int nextSize(int size,int direction){return com.vandorlabs.ui.WallAppearanceDialog.nextSize(size,direction);}
    /** The bevel rises away from the corner (outward) or towards it, like diagonal glass leans. */
    public static String slopeLabel(int size,boolean inverted,boolean far){
        return (size==CornerGlassGeometry.WIDE?"Slope rises: ":"Slope leans: ")+(far==inverted?"Outward":"Inward");
    }
    /** New metadata after the Slope button: wide corners flip their position, others their lean. */
    public static int toggleSlope(int metadata,int size){
        return size==CornerGlassGeometry.WIDE?metadata^8:metadata^4;
    }
    /** Metadata for a dialog edit: the bevel keeps its turn. */
    public static int edited(int metadata,boolean inverted,boolean far){
        return CornerGlassGeometry.diagonal(metadata&3,CornerGlassGeometry.FULL,inverted,far).metadata();
    }
}
