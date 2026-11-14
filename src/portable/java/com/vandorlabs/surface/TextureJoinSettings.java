package com.vandorlabs.surface;

import com.vandorlabs.persistence.HousingTags;

/** Saved texture connectivity, independent of platform worlds and render APIs. */
public final class TextureJoinSettings {
    public static final int OFF=0,ALL=1,VERTICAL=2,HORIZONTAL=3,NORTH_SOUTH=4,EAST_WEST=5,COUNT=6;
    public static final int EVERY=0,EVEN=1,ODD=2;
    public static final String DIRECTION_TAG="TextureJoinDirection",PARITY_TAG="TextureJoinParity";
    public static final TextureJoinSettings DEFAULT=new TextureJoinSettings(ALL,EVERY);
    private static final TextureJoinSettings[] DISABLED={new TextureJoinSettings(OFF,EVERY),new TextureJoinSettings(OFF,EVEN),new TextureJoinSettings(OFF,ODD)};
    private static final String[] NAMES={"Off","On","Vertical","Horizontal","North/South","East/West"};
    public final int direction,parity;
    public TextureJoinSettings(int direction,int parity){
        if(!valid(direction,parity))throw new IllegalArgumentException("Invalid texture join settings");
        this.direction=direction;this.parity=parity;
    }
    public static boolean valid(int direction,int parity){return direction>=0&&direction<COUNT&&parity>=0&&parity<3;}
    public String directionLabel(){return "Join: "+NAMES[direction];}
    public String parityLabel(){return "Neighbours: "+(parity==EVEN?"Even":parity==ODD?"Odd":"All");}
    public boolean directional(){return direction>=VERTICAL;}
    public TextureJoinSettings disabled(){return DISABLED[parity];}
    public TextureJoinSettings cycleDirection(int step){return new TextureJoinSettings(Math.floorMod(direction+step,COUNT),parity);}
    public TextureJoinSettings cycleParity(int step){return new TextureJoinSettings(direction,Math.floorMod(parity+step,3));}
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
    /** The owner filters candidate coordinates; the candidate's own parity does not veto a join. */
    public boolean connects(int ax,int ay,int az,int bx,int by,int bz){
        long dx=(long)bx-ax,dy=(long)by-ay,dz=(long)bz-az;
        if(direction==OFF||Math.abs(dx)>1||Math.abs(dy)>1||Math.abs(dz)>1||dx==0&&dy==0&&dz==0)return false;
        // Sloping panels may step in depth as well as their primary joining axis.
        // Geometry adapters still require actual coplanar/meeting surfaces.
        if(direction==VERTICAL&&dy==0||direction==HORIZONTAL&&dy!=0
                ||direction==NORTH_SOUTH&&(dz==0||dx!=0)||direction==EAST_WEST&&(dx==0||dz!=0))return false;
        if(!directional()||parity==EVERY)return true;
        int wanted=parity==EVEN?0:1;
        if(direction==VERTICAL)return Math.floorMod(by,2)==wanted;
        if(direction==NORTH_SOUTH)return Math.floorMod(bz,2)==wanted;
        if(direction==EAST_WEST)return Math.floorMod(bx,2)==wanted;
        return (dx==0||Math.floorMod(bx,2)==wanted)&&(dz==0||Math.floorMod(bz,2)==wanted);
    }
    @Override public boolean equals(Object other){return other instanceof TextureJoinSettings&&((TextureJoinSettings)other).direction==direction&&((TextureJoinSettings)other).parity==parity;}
    @Override public int hashCode(){return direction*3+parity;}
}
