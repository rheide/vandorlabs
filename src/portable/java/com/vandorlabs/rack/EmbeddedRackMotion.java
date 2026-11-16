package com.vandorlabs.rack;

/** Four bounded cosmetic poses using the placed Rack's exact pause-clock motion envelope. */
public final class EmbeddedRackMotion {
    private boolean initialized;
    private int present;
    private final long[] sequences=new long[4];
    private final int[] levels=new int[4];
    private final double[] starts=new double[4],from=new double[4],to=new double[4];
    public void observe(int mask,long s0,long s1,long s2,long s3,int l0,int l1,int l2,int l3,double now){
        int next=present;
        if(observeSlot(0,mask,s0,l0,now))next=(next&~1)|(mask&1);
        if(observeSlot(1,mask,s1,l1,now))next=(next&~2)|(mask&2);
        if(observeSlot(2,mask,s2,l2,now))next=(next&~4)|(mask&4);
        if(observeSlot(3,mask,s3,l3,now))next=(next&~8)|(mask&8);
        present=next;initialized=true;
    }
    private boolean observeSlot(int bay,int mask,long sequence,int level,double now){
        int bit=1<<bay;boolean occupied=(mask&bit)!=0;
        if(!initialized){sequences[bay]=sequence;levels[bay]=level;starts[bay]=now;from[bay]=to[bay]=occupied?0:ModuleRackMotion.TRAVEL_BLOCKS;return true;}
        if(sequence<sequences[bay])return false;
        boolean changed=ModuleRackMotion.starts(false,present,mask,bay,sequences[bay],sequence);
        if(changed){
            double current=offset(bay,now);
            if(occupied&&(present&bit)!=0&&sequence!=sequences[bay])current=ModuleRackMotion.TRAVEL_BLOCKS;
            if(occupied&&!visible(bay,now))current=ModuleRackMotion.TRAVEL_BLOCKS;
            from[bay]=current;to[bay]=occupied?0:ModuleRackMotion.TRAVEL_BLOCKS;starts[bay]=now;sequences[bay]=sequence;
        }
        if(occupied)levels[bay]=Math.max(0,Math.min(4,level));return true;
    }
    public double offset(int bay,double now){return ModuleRackMotion.between(Math.max(0,now-starts[bay]),from[bay],to[bay]);}
    public boolean occupied(int bay){return (present&1<<bay)!=0;}
    public boolean visible(int bay,double now){return occupied(bay)||now-starts[bay]<ModuleRackMotion.DURATION_TICKS&&from[bay]!=to[bay];}
    public boolean moving(int bay,double now){return now-starts[bay]<ModuleRackMotion.DURATION_TICKS&&from[bay]!=to[bay];}
    public int level(int bay){return levels[bay];}
}
