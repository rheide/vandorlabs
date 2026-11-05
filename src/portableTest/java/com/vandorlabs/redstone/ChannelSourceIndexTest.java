package com.vandorlabs.redstone;

import java.lang.reflect.Field;
import java.util.*;

/** Differential query order/counters and adversarial lifetime tests against the frozen old index. */
public final class ChannelSourceIndexTest {
    static List<Integer> trace;
    static final class Member {
        final int id;boolean enabled=true;Runnable action;
        Member(int id){this.id=id;}
        boolean active(){if(trace!=null)trace.add(id);Runnable next=action;action=null;if(next!=null)next.run();return enabled;}
        @Override public boolean equals(Object other){return other instanceof Member;}
        @Override public int hashCode(){return 1;}
    }
    static class Snapshot implements ChannelSourceIndex.SourceSnapshot {
        final int x,y,z,range,level;
        Snapshot(int x,int y,int z,int range,int level){this.x=x;this.y=y;this.z=z;this.range=range;this.level=level;}
        public int x(){return x;}public int y(){return y;}public int z(){return z;}public int range(){return range;}public int level(){return level;}
    }
    static final class AlternateSnapshot extends Snapshot {AlternateSnapshot(){super(0,0,0,0,7);}}
    static final class Context extends ChannelSourceIndex.QueryContext<Member> {protected boolean active(Member member){return member.active();}}
    private static final class Failure extends RuntimeException {private static final long serialVersionUID=1L;}
    private static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void query(FrozenChannelSourceIndex old,ChannelSourceIndex<Member,Snapshot> current,Context context,int x,int y,int z){
        trace=new ArrayList<>();int expected=old.at(x,y,z);List<Integer> visits=trace;
        trace=new ArrayList<>();int actual=current.at(x,y,z);
        check(actual==expected,"query level");check(visits.equals(trace),"exact active visitation order");
        check(old.stats.sourceBuckets==context.sourceBuckets&&old.stats.sourceCandidates==context.sourceCandidates,"exact per-query counters");trace=null;
    }
    static void buffersCleared(Context context)throws Exception{
        Field pool=ChannelSourceIndex.QueryContext.class.getDeclaredField("queryBuffers");pool.setAccessible(true);Deque<?> buffers=(Deque<?>)pool.get(context);
        check(buffers.size()<=8,"network pool remains bounded by eight arrays");
        for(Object buffer:buffers){Object[] array=(Object[])buffer;check(array.length==82,"exact candidate capacity");for(Object item:array)check(item==null,"returned buffer retained a bucket");}
    }
    private static int bufferCount(Context context)throws Exception{Field pool=ChannelSourceIndex.QueryContext.class.getDeclaredField("queryBuffers");pool.setAccessible(true);return ((Deque<?>)pool.get(context)).size();}
    private static void randomized()throws Exception{
        Context context=new Context();FrozenChannelSourceIndex old=new FrozenChannelSourceIndex(new FrozenChannelSourceIndex.Stats());ChannelSourceIndex<Member,Snapshot> current=new ChannelSourceIndex<>(context);
        Member[] members=new Member[64];for(int i=0;i<members.length;i++)members[i]=new Member(i);Random random=new Random(0x29c74a85L);int[] ranges={0,4,8,16,32,64,128,256};
        for(int event=0;event<3000;event++){
            Member member=members[random.nextInt(members.length)];int operation=random.nextInt(4);
            if(operation<2){Snapshot snapshot=new Snapshot(random.nextInt(1201)-600,random.nextInt(129)-64,random.nextInt(1201)-600,ranges[random.nextInt(ranges.length)],random.nextInt(15)+1);old.put(member,snapshot);current.put(member,snapshot);check(current.source(member)==snapshot,"publication must retain exact original snapshot identity");}
            else if(operation==2){old.remove(member);current.remove(member);}else member.enabled=!member.enabled;
            check(old.maximum()==current.maximum()&&old.all.isEmpty()==current.isEmpty()&&old.all.get(member)==current.source(member),"mutation state and identity membership");
            for(int n=0;n<3;n++){Snapshot anchor=old.all.get(members[random.nextInt(members.length)]);int x=anchor==null?random.nextInt(1201)-600:anchor.x+(n==1?anchor.range:0),y=anchor==null?0:anchor.y,z=anchor==null?0:anchor.z;query(old,current,context,x,y,z);}
        }
        buffersCleared(context);check(context.sourceBuckets==9000L*81,"empty/populated queries still visit every spatial bucket");
    }
    private static void identityAndCallbacks()throws Exception{
        Context context=new Context();ChannelSourceIndex<Member,Snapshot> current=new ChannelSourceIndex<>(context);FrozenChannelSourceIndex old=new FrozenChannelSourceIndex(new FrozenChannelSourceIndex.Stats());Member a=new Member(1),b=new Member(2);
        Snapshot left=new Snapshot(0,0,0,4,3),right=new Snapshot(100,0,0,4,12);old.put(a,left);current.put(a,left);old.put(b,right);current.put(b,right);
        check(a.equals(b)&&a!=b&&current.source(a)==left&&current.source(b)==right,"equal members remain distinct identity keys");query(old,current,context,0,0,0);query(old,current,context,100,0,0);old.remove(a);current.remove(a);check(current.source(b)==right,"removal must not remove an equal member");
        Member mutating=new Member(3);Snapshot captured=new Snapshot(0,0,0,4,11),replacement=new Snapshot(1000,0,0,4,11);old.put(mutating,captured);current.put(mutating,captured);
        mutating.action=()->old.put(mutating,replacement);check(old.at(0,0,0)==11,"frozen snapshot survives replacement during activity callback");
        mutating.action=()->current.put(mutating,replacement);check(current.at(0,0,0)==11,"query re-fetched replacement after activity callback");check(current.source(mutating)==replacement,"replacement snapshot identity");query(old,current,context,0,0,0);query(old,current,context,1000,0,0);
        old.put(mutating,captured);current.put(mutating,captured);mutating.action=()->old.remove(mutating);check(old.at(0,0,0)==11,"frozen removal callback result");mutating.action=()->current.remove(mutating);check(current.at(0,0,0)==11,"captured snapshot must survive removal callback");query(old,current,context,0,0,0);
        Context staleContext=new Context();ChannelSourceIndex<Member,Snapshot> stale=new ChannelSourceIndex<>(staleContext);Member missing=new Member(4);stale.put(missing,captured);
        Field all=ChannelSourceIndex.class.getDeclaredField("all");all.setAccessible(true);((Map<?,?>)all.get(stale)).clear();missing.action=()->{throw new Failure();};
        check(stale.at(0,0,0)==0&&missing.action!=null&&staleContext.sourceCandidates==1,"missing snapshot must skip liveness callback after counting candidate");buffersCleared(staleContext);
    }
    private static void reentrant()throws Exception{
        final int depth=10;Context context=new Context();FrozenChannelSourceIndex.Stats oldStats=new FrozenChannelSourceIndex.Stats();List<ChannelSourceIndex<Member,? extends Snapshot>> current=new ArrayList<>();List<FrozenChannelSourceIndex> old=new ArrayList<>();Member[] members=new Member[depth];
        for(int i=0;i<depth;i++){
            members[i]=new Member(100+i);FrozenChannelSourceIndex reference=new FrozenChannelSourceIndex(oldStats);old.add(reference);
            if(i%2==0){ChannelSourceIndex<Member,Snapshot> index=new ChannelSourceIndex<>(context);Snapshot value=new Snapshot(0,0,0,0,7);index.put(members[i],value);reference.put(members[i],value);current.add(index);}
            else {ChannelSourceIndex<Member,AlternateSnapshot> index=new ChannelSourceIndex<>(context);AlternateSnapshot value=new AlternateSnapshot();index.put(members[i],value);reference.put(members[i],value);current.add(index);}
        }
        for(int i=0;i<depth-1;i++){final int next=i+1;members[i].action=()->check(old.get(next).at(0,0,0)==7,"old nested result");}
        trace=new ArrayList<>();check(old.get(0).at(0,0,0)==7,"old outer result");List<Integer> visits=trace;
        for(int i=0;i<depth-1;i++){final int next=i+1;members[i].action=()->check(current.get(next).at(0,0,0)==7,"new nested result");}
        trace=new ArrayList<>();check(current.get(0).at(0,0,0)==7,"new outer result");check(visits.equals(trace),"cross-index reentrant active order");trace=null;
        check(context.sourceBuckets==oldStats.sourceBuckets&&context.sourceCandidates==oldStats.sourceCandidates,"reentrant counter parity");check(bufferCount(context)==8&&oldStats.queryBuffers.size()==8,"shared network pool cap after ten concurrent queries");buffersCleared(context);
        for(int i=0;i<depth-1;i++){final int next=i+1;members[i].action=()->old.get(next).at(0,0,0);}members[depth-1].action=()->{throw new Failure();};
        boolean oldThrown=false;try{old.get(0).at(0,0,0);}catch(Failure expected){oldThrown=true;}
        for(int i=0;i<depth-1;i++){final int next=i+1;members[i].action=()->current.get(next).at(0,0,0);}members[depth-1].action=()->{throw new Failure();};
        boolean nestedThrown=false;try{current.get(0).at(0,0,0);}catch(Failure expected){nestedThrown=true;}
        check(oldThrown&&nestedThrown&&context.sourceBuckets==oldStats.sourceBuckets&&context.sourceCandidates==oldStats.sourceCandidates,"nested exception propagation/counter parity");buffersCleared(context);
        members[0].action=()->{throw new Failure();};boolean thrown=false;try{current.get(0).at(0,0,0);}catch(Failure expected){thrown=true;}check(thrown,"activity failure must propagate");buffersCleared(context);check(current.get(0).at(0,0,0)==7,"pool unusable after exception");
        members[0].action=()->check(current.get(0).at(0,0,0)==7,"same-index reentrant query");check(current.get(0).at(0,0,0)==7,"same-index outer query");buffersCleared(context);
        Context other=new Context();ChannelSourceIndex<Member,Snapshot> isolated=new ChannelSourceIndex<>(other);check(isolated.at(0,0,0)==0&&bufferCount(other)==1&&bufferCount(context)==8,"networks must not share buffers");
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("allocation")){ChannelSourceIndexAllocationCheck.run();return;}
        randomized();identityAndCallbacks();reentrant();
        System.out.println("ChannelSourceIndex differential PASS checks="+checks+" identity snapshots query-order counters mutation reentrant shared-pool-cap exception-cleanup");
    }
}
