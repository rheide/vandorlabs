package com.vandorlabs.gear.track;
import com.vandorlabs.gear.track.core.TrackTopology;
public final class TrackRegistry {
    public static final TrackBlock COMPACT=new TrackBlock("compact_track",TrackTopology.Style.COMPACT);
    public static final TrackBlock ARMOURED=hidden(new TrackBlock("armoured_track",TrackTopology.Style.ARMOURED));
    public static final TrackBlock BOGIE=hidden(new TrackBlock("bogie_track",TrackTopology.Style.BOGIE));
    public static final TrackBlock[] BLOCKS={COMPACT,ARMOURED,BOGIE};
    private static TrackBlock hidden(TrackBlock block){block.setCreativeTab(null);return block;}
    private TrackRegistry(){}
}
