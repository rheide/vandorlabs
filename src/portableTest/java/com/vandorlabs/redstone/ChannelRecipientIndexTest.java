package com.vandorlabs.redstone;

import java.lang.reflect.Field;
import java.util.*;

/** Independent old/new recipient lookup, identity membership and immutable snapshot ownership. */
public final class ChannelRecipientIndexTest {
    static List<String> trace;
    static final class Member {
        final int id;Member(int id){this.id=id;}
        @Override public boolean equals(Object other){return other instanceof Member;}
        @Override public int hashCode(){return 1;}
    }
    static final class Position {
        int x,y,z;final int id;final boolean mutable;
        Position(int x,int y,int z,int id,boolean mutable){this.x=x;this.y=y;this.z=z;this.id=id;this.mutable=mutable;}
        int getX(){if(trace!=null)trace.add(id+"x");return x;}
        int getY(){if(trace!=null)trace.add(id+"y");return y;}
        int getZ(){if(trace!=null)trace.add(id+"z");return z;}
        Position toImmutable(){return mutable?new Position(x,y,z,id,false):this;}
    }
    static final class Stats implements ChannelRecipientIndex.Counters {
        long buckets,candidates;Runnable candidateAction;
        public void bucketVisited(){buckets++;if(trace!=null)trace.add("bucket");}
        public void candidateVisited(){candidates++;if(trace!=null)trace.add("candidate");Runnable next=candidateAction;candidateAction=null;if(next!=null)next.run();}
    }
    static final class Index extends ChannelRecipientIndex<Member,Position> {
        Index(Stats stats){super(stats);}
        protected Position snapshot(Position pos){return pos.toImmutable();}
        protected int x(Position pos){return pos.getX();}protected int y(Position pos){return pos.getY();}protected int z(Position pos){return pos.getZ();}
    }
    private static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void compare(FrozenChannelRecipientIndex old,Index current,Stats stats,Position query,int range){
        trace=new ArrayList<>();Set<Member> expected=old.find(query,range);List<String> reads=trace;
        trace=new ArrayList<>();Set<Member> actual=current.find(query,range);
        check(expected.size()==actual.size(),"result size");Iterator<Member> ordered=actual.iterator();for(Member member:expected)check(ordered.next()==member,"result identity iteration order");
        for(Member member:expected){boolean found=false;for(Member value:actual)if(member==value)found=true;check(found,"result identities must match, not equals");}
        check(reads.equals(trace),"exact coordinate, bucket and candidate visitation order");check(old.stats.buckets==stats.buckets&&old.stats.candidates==stats.candidates,"exact counters");trace=null;
    }
    private static void random()throws Exception{
        Stats stats=new Stats();Index current=new Index(stats);FrozenChannelRecipientIndex old=new FrozenChannelRecipientIndex(new Stats());Member[] members=new Member[80];for(int i=0;i<members.length;i++)members[i]=new Member(i);
        int[] coordinates={Integer.MIN_VALUE,-30000000,-65536,-257,-256,-65,-64,-17,-16,-1,0,1,15,16,17,63,64,65,255,256,257,65536,30000000,Integer.MAX_VALUE};int[] ranges={-1,0,4,8,16,32,64,128,256};Random random=new Random(0x29c8cd8bL);
        for(int event=0;event<2500;event++){
            Member member=members[random.nextInt(members.length)];
            if(random.nextInt(4)!=0){Position pos=new Position(coordinates[random.nextInt(coordinates.length)],coordinates[random.nextInt(coordinates.length)],coordinates[random.nextInt(coordinates.length)],member.id,false);old.put(member,pos);current.put(member,pos);check(current.position(member)==pos,"immutable position retained directly");}
            else {old.remove(member);current.remove(member);}
            check(old.positions.isEmpty()==current.isEmpty()&&old.positions.get(member)==current.position(member),"replacement/removal membership");
            Position anchor=old.positions.get(members[random.nextInt(members.length)]);for(int i=0;i<3;i++){int range=ranges[random.nextInt(ranges.length)];Position query=anchor==null?new Position(0,0,0,-1,false):new Position(anchor.x+(i==1?Math.max(0,range):0),anchor.y,anchor.z,-1,false);compare(old,current,stats,query,range);}
        }
        for(Member member:members){old.remove(member);current.remove(member);}check(current.isEmpty(),"all removals");
        Field grids=ChannelRecipientIndex.class.getDeclaredField("grids");grids.setAccessible(true);for(Object value:(Object[])grids.get(current))check(((Map<?,?>)value).isEmpty(),"empty grid buckets removed");
        compare(old,current,stats,null,0);compare(old,current,stats,null,-1);compare(old,current,stats,new Position(0,0,0,-1,false),16);
    }
    private static void ownershipAndCallbacks(){
        Stats stats=new Stats();Index current=new Index(stats);FrozenChannelRecipientIndex old=new FrozenChannelRecipientIndex(new Stats());Member a=new Member(1),b=new Member(2);Position mutable=new Position(-17,64,16,1,true);
        old.put(a,mutable);current.put(a,mutable);Position retained=current.position(a);check(retained!=mutable&&!retained.mutable,"mutable input captured once");mutable.x=1000;
        compare(old,current,stats,new Position(-1,64,16,-1,false),16);check(current.position(a)==retained&&retained.x==-17,"snapshot survives later input mutation");
        Position immutable=new Position(1000,64,16,2,false);old.put(b,immutable);current.put(b,immutable);check(a.equals(b)&&a!=b,"identity adversary");compare(old,current,stats,null,0);compare(old,current,stats,null,Integer.MIN_VALUE);
        old.put(a,immutable);current.put(a,immutable);compare(old,current,stats,new Position(1000,64,16,-1,false),4);old.remove(a);current.remove(a);check(current.position(b)==immutable,"equal distinct member unaffected by remove");
        Position query=new Position(1000,64,16,-1,true);old.stats.candidateAction=()->query.x=2000;check(old.find(query,4).isEmpty(),"old query reads after counter callback");query.x=1000;stats.candidateAction=()->query.x=2000;check(current.find(query,4).isEmpty(),"query must remain original P read after callback");
        compare(old,current,stats,null,0);compare(old,current,stats,null,-1);
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("allocation")){ChannelRecipientIndexAllocationCheck.run();return;}
        random();ownershipAndCallbacks();System.out.println("ChannelRecipientIndex differential PASS checks="+checks+" identity order counters mutable-snapshot replacement removal null-shortcircuits extremes aliases original-query");
    }
}
