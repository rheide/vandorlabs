package com.vandorlabs.light;

import java.util.*;

/** Loaded coplanar Light rules. Graph callbacks belong to the owning-thread platform adapter. */
public final class ProgrammableLightJoining {
    public static final int LIMIT=4096;
    private ProgrammableLightJoining(){}
    public static final class Node {
        public final String block;
        public final int facing,half,face;
        public final boolean slab,available,join,small,on;
        public Node(String block,int facing,int half,int face,boolean slab,boolean available,boolean join,boolean small,boolean on){this.block=Objects.requireNonNull(block);this.facing=facing;this.half=half;this.face=face;this.slab=slab;this.available=available;this.join=join;this.small=small;this.on=on;}
    }
    public interface Graph<P> {
        P neighbor(P position,int ordinal);
        boolean loaded(P position);
        Node node(P position);
    }
    public static boolean eligible(Node first,Node next,boolean matchOn){return next!=null&&first.block.equals(next.block)&&first.facing==next.facing&&(!first.slab||first.half==next.half)&&next.available&&next.join&&!next.small&&first.face==next.face&&(!matchOn||first.on==next.on);}
    public static <P> List<P> members(P start,Node first,Graph<P> graph,boolean matchOn){
        if(!first.join||first.small)return Collections.singletonList(start);
        LinkedHashSet<P> members=new LinkedHashSet<>();ArrayDeque<P> queue=new ArrayDeque<>();members.add(start);queue.add(start);
        while(!queue.isEmpty()){P current=queue.removeFirst();for(int ordinal=0;ordinal<4;ordinal++){P next=graph.neighbor(current,ordinal);if(members.contains(next)||!graph.loaded(next)||!eligible(first,graph.node(next),matchOn))continue;members.add(next);if(members.size()>=LIMIT)return Collections.singletonList(start);queue.addLast(next);}}
        return Collections.unmodifiableList(new ArrayList<>(members));
    }
    public static final class Input {
        public final boolean numeric,powered;public final int level;
        public Input(boolean numeric,int level,boolean powered){this.numeric=numeric;this.level=level;this.powered=powered;}
    }
    public static final class Settlement {
        /** A negative level means the saved joined numeric level is untouched. */
        public final int level;public final boolean powered;
        private Settlement(int level,boolean powered){this.level=level;this.powered=powered;}
    }
    public static Settlement settle(Iterable<Input> inputs){boolean numeric=false,power=false;int level=0;for(Input input:inputs){numeric|=input.numeric;power|=input.powered;level=Math.max(level,input.level);}return new Settlement(numeric?level:-1,numeric?level>0:power);}
}
