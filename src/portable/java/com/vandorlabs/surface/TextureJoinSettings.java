package com.vandorlabs.surface;

import com.vandorlabs.persistence.HousingTags;

/** Saved texture connectivity, independent of platform worlds and render APIs. */
public final class TextureJoinSettings {
    public static final int OFF=0,ALL=1,VERTICAL=2,HORIZONTAL=3,NORTH_SOUTH=4,EAST_WEST=5,COUNT=6;
    public static final int EVERY=0;
    /** Reserved old persisted values; both now mean All. */
    @Deprecated public static final int EVEN=1,ODD=2;
    public static final String DIRECTION_TAG="TextureJoinDirection",PARITY_TAG="TextureJoinParity";
    public static final TextureJoinSettings DEFAULT=new TextureJoinSettings(ALL,EVERY);
    private static final TextureJoinSettings DISABLED=new TextureJoinSettings(OFF,EVERY);
    private static final String[] NAMES={"Off","On","Vertical","Horizontal","North/South","East/West"};
    public final int direction,parity;
    public TextureJoinSettings(int direction,int parity){
        if(!valid(direction,parity))throw new IllegalArgumentException("Invalid texture join settings");
        this.direction=direction;this.parity=EVERY;
    }
    public static boolean valid(int direction,int parity){return direction>=0&&direction<COUNT&&parity>=0&&parity<3;}
    public String directionLabel(){return "Join: "+NAMES[direction];}
    public String parityLabel(){return "Neighbours: All";}
    public boolean directional(){return direction>=VERTICAL;}
    public TextureJoinSettings disabled(){return DISABLED;}
    public TextureJoinSettings cycleDirection(int step){return new TextureJoinSettings(Math.floorMod(direction+step,COUNT),parity);}
    public TextureJoinSettings cycleParity(int step){return new TextureJoinSettings(direction,EVERY);}
    public void write(HousingTags tag){write(tag,false);}
    public void write(HousingTags tag,boolean retainDefaults){
        // Old snapshots must round-trip without introducing new own-tile fields.
        // Explicit nondefault policies are saved; retained fields are overwritten
        // when resetting a host to its default policy.
        if(equals(DEFAULT)&&!retainDefaults&&!tag.contains(DIRECTION_TAG)&&!tag.contains(PARITY_TAG))return;
        tag.putInt(DIRECTION_TAG,direction);tag.putInt(PARITY_TAG,parity);
    }
    public static TextureJoinSettings read(HousingTags tag){
        int direction=tag.containsInt(DIRECTION_TAG)?tag.getInt(DIRECTION_TAG):ALL;
        int parity=tag.containsInt(PARITY_TAG)?tag.getInt(PARITY_TAG):EVERY;
        return valid(direction,parity)?new TextureJoinSettings(direction,parity):DEFAULT;
    }
    /** Canonicalize only the retired own field, never nested capabilities or foreign tile data. */
    public static void normalizeLegacy(HousingTags tag){if(needsLegacyNormalization(tag))tag.putInt(PARITY_TAG,EVERY);}
    public static boolean needsLegacyNormalization(HousingTags tag){
        String id=tag.getString("id");
        return ("vandorlabs:programmable_viewscreen".equals(id)||"vandorlabs:programmable_glass".equals(id))
                &&tag.containsInt(PARITY_TAG)&&(tag.getInt(PARITY_TAG)==EVEN||tag.getInt(PARITY_TAG)==ODD)
                &&(!tag.contains(DIRECTION_TAG)||tag.containsInt(DIRECTION_TAG)&&tag.getInt(DIRECTION_TAG)>=OFF&&tag.getInt(DIRECTION_TAG)<COUNT);
    }
    /** Full-depth Portholes retain panel-depth metadata that does not move their physical plane. */
    public static boolean samePanelDepth(boolean fullDepth,int first,int second){return fullDepth||first==second;}
    /** Directional edges still require actual geometry admission in platform adapters. */
    public boolean connects(int ax,int ay,int az,int bx,int by,int bz){
        long dx=(long)bx-ax,dy=(long)by-ay,dz=(long)bz-az;
        if(direction==OFF||Math.abs(dx)>1||Math.abs(dy)>1||Math.abs(dz)>1||dx==0&&dy==0&&dz==0)return false;
        // Sloping panels may step in depth as well as their primary joining axis.
        // Geometry adapters still require actual coplanar/meeting surfaces.
        if(direction==VERTICAL&&dy==0||direction==HORIZONTAL&&dy!=0
                ||direction==NORTH_SOUTH&&(dz==0||dx!=0)||direction==EAST_WEST&&(dx==0||dz!=0))return false;
        return true;
    }
    /** A seam belongs to both panels; neither endpoint may opt the other into joining. */
    public boolean connectsWith(TextureJoinSettings other,int ax,int ay,int az,int bx,int by,int bz){
        return other!=null&&connects(ax,ay,az,bx,by,bz)&&other.connects(bx,by,bz,ax,ay,az);
    }
    @Override public boolean equals(Object other){return other instanceof TextureJoinSettings&&((TextureJoinSettings)other).direction==direction&&((TextureJoinSettings)other).parity==parity;}
    @Override public int hashCode(){return direction*3+parity;}
}
