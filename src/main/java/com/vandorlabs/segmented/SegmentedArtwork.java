package com.vandorlabs.segmented;
/** Metadata lookup only; does not initialize model JSON or client rendering classes. */
public final class SegmentedArtwork {
    private static String id(int choice){String[] ids=com.vandorlabs.tiles.ScreenHousingTextures.IDS;return choice>=0&&choice<ids.length?ids[choice]:null;}
    public static int gate(int choice){return SegmentedArtworkPolicy.gate(id(choice));}
    public static boolean translucent(int choice){return SegmentedArtworkPolicy.translucent(id(choice));}
    public static boolean complex(int choice){return SegmentedArtworkPolicy.complex(id(choice));}
    private SegmentedArtwork(){}
}
