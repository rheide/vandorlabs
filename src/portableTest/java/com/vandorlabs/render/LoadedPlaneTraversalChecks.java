package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Loaded-boundary and traversal-budget contracts, independent of Minecraft coordinates. */
public final class LoadedPlaneTraversalChecks {
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static long cell(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
    private static int x(long cell){return (int)(cell>>32);}
    private static int y(long cell){return (int)cell;}
    private static Long next(Long cell,int direction){return cell(x(cell)+(direction==0?1:direction==1?-1:0),y(cell)+(direction==2?1:direction==3?-1:0));}
    public static void main(String[] args){
        closedRegions();
        java.util.Set<Long> probed=new java.util.HashSet<Long>();long missing=cell(2,1);
        LoadedPlaneTraversal.Scan<Long> partial=LoadedPlaneTraversal.scan(cell(0,0),LoadedPlaneTraversalChecks::next,
                p->p!=missing,p->{probed.add(p);return x(p)>=0&&x(p)<4&&y(p)>=0&&y(p)<3;},4096);
        need(!partial.complete&&partial.members.size()==11&&!partial.members.contains(missing),"unloaded hole must prevent complete rectangle certification");
        need(!probed.contains(missing),"eligibility queried unloaded cell");
        LoadedPlaneTraversal.Scan<Long> cap=LoadedPlaneTraversal.scan(cell(0,0),LoadedPlaneTraversalChecks::next,p->true,p->true,4096);
        need(cap.members.size()==4096&&!cap.complete,"open plane escaped traversal budget");
        List<Long> expected=Arrays.asList(cell(0,0),cell(1,0),cell(-1,0),cell(0,1),cell(0,-1));
        LoadedPlaneTraversal.Scan<Long> small=LoadedPlaneTraversal.scan(cell(0,0),LoadedPlaneTraversalChecks::next,p->true,p->true,5);
        need(new ArrayList<Long>(small.members).equals(expected),"deterministic owner/traversal ordering changed");
        System.out.println("Loaded plane traversal PASS rectangle12/unloaded11/open4096 and deterministic five-cell cap; no unloaded eligibility reads");
    }
    private static void closedRegions(){
        LoadedPlaneTraversal.Scan<Long> rectangle=LoadedPlaneTraversal.scan(cell(0,0),LoadedPlaneTraversalChecks::next,p->true,
                p->x(p)>=0&&x(p)<4&&y(p)>=0&&y(p)<3,4096);
        need(rectangle.complete&&rectangle.members.size()==12,"closed loaded rectangle was not complete");
        LoadedPlaneTraversal.Scan<Long> lone=LoadedPlaneTraversal.scan(cell(-7,9),LoadedPlaneTraversalChecks::next,p->true,p->false,4096);
        need(lone.complete&&lone.members.size()==1&&lone.members.contains(cell(-7,9)),"installed owner must survive with no eligible neighbors");
    }
}
