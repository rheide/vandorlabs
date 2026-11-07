package com.vandorlabs.neoforge.segmented;
import com.vandorlabs.segmented.SegmentedArtworkPolicy;
/** Metadata lookup only; model parsing and rendering remain separate. */
public final class NativeSegmentedArtwork {
    private static String id(int choice){if(choice<0)return null;var entries=com.vandorlabs.neoforge.ModernFinishCatalog.ENTRIES;return choice>=0&&choice<entries.size()?entries.get(choice).id():null;}
    public static int gate(int choice){return SegmentedArtworkPolicy.gate(id(choice));}
    public static boolean translucent(int choice){return SegmentedArtworkPolicy.translucent(id(choice));}
    public static boolean complex(int choice){return SegmentedArtworkPolicy.complex(id(choice));}
    private NativeSegmentedArtwork(){}
}
