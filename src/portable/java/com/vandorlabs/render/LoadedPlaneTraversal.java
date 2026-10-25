package com.vandorlabs.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Bounded four-neighbour traversal; eligibility never probes an unloaded cell. */
public final class LoadedPlaneTraversal {
    public interface Neighbors<P> {P at(P origin,int direction);}
    public static final class Scan<P> {
        public final Set<P> members;
        public final boolean complete;
        private Scan(Set<P> members,boolean complete){this.members=members;this.complete=complete;}
    }
    private LoadedPlaneTraversal(){}
    public static <P> Scan<P> scan(P start,Neighbors<P> neighbors,Predicate<P> loaded,Predicate<P> eligible,int limit){
        Set<P> members=new LinkedHashSet<P>();Deque<P> queue=new ArrayDeque<P>();boolean complete=true;
        members.add(start);queue.add(start);
        while(!queue.isEmpty()){
            P current=queue.removeFirst();
            for(int direction=0;direction<4;direction++){
                P next=neighbors.at(current,direction);
                if(members.contains(next))continue;
                if(!loaded.test(next)){complete=false;continue;}
                if(!eligible.test(next))continue;
                members.add(next);
                if(members.size()>=limit)return new Scan<P>(members,false);
                queue.addLast(next);
            }
        }
        return new Scan<P>(members,complete);
    }
}
