package com.vandorlabs.vehicle;

import java.util.*;

public final class SafeParkCandidatesTest {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static SafeParkCandidates.Candidate find(SafeParkCandidates search,boolean coldNorth){
        while(!search.done()){
            SafeParkCandidates.Candidate candidate=search.next((x,z)->!coldNorth||z>=0,1);
            if(candidate!=null&&candidate.y==65&&(candidate.x!=0||candidate.z!=0))return candidate;
        }return null;
    }
    public static void main(String[] args){
        SafeParkCandidates.Candidate beside=find(new SafeParkCandidates(0,67,0,2,65,69),false);
        need(beside!=null&&beside.x==0&&beside.y==65&&beside.z==-1&&beside.distanceSquared==5,"nearest flat placement beside an obstructed column");
        SafeParkCandidates.Candidate loaded=find(new SafeParkCandidates(0,67,0,2,65,69),true);
        need(loaded!=null&&loaded.x==-1&&loaded.y==65&&loaded.z==0,"cold nearby column is skipped without searching its heights");
        SafeParkCandidates empty=new SafeParkCandidates(0,80,0,16,0,255);int calls=0,polls=0;
        while(!empty.done()){
            int[] work={0};need(empty.next((x,z)->{work[0]++;return false;},1)==null,"cold world never returns a placement");need(work[0]<=1,"new-column work respects each slice");calls+=work[0];polls++;need(polls<=1089,"bounded cold exhaustion");
        }need(calls==1089,"each cold horizontal column tested once");need(empty.next((x,z)->{throw new AssertionError("exhausted search read terrain");},1)==null,"completed search is inert");
        SafeParkCandidates complete=new SafeParkCandidates(10,75,-4,3,72,78);Set<String> seen=new HashSet<>();Set<String> columns=new HashSet<>();long previous=-1;int count=0;
        while(!complete.done()){
            SafeParkCandidates.Candidate candidate=complete.next((x,z)->{need(columns.add(x+","+z),"loaded column queried once");return true;},1);if(candidate==null)continue;
            need(candidate.distanceSquared>=previous,"candidate distance never regresses across slices");previous=candidate.distanceSquared;need(seen.add(candidate.x+","+candidate.y+","+candidate.z),"candidate unique");need(candidate.x>=7&&candidate.x<=13&&candidate.y>=72&&candidate.y<=78&&candidate.z>=-7&&candidate.z<=-1,"candidate remains in configured volume");if(count++==0)need(candidate.x==10&&candidate.y==75&&candidate.z==-4,"preferred exact grid is first");
        }need(count==343&&columns.size()==49,"complete bounded volume coverage");
        SafeParkCandidates extreme=new SafeParkCandidates(0,Integer.MAX_VALUE,0,0,0,255);SafeParkCandidates.Candidate high=extreme.next((x,z)->true,1);need(high.y==255&&high.distanceSquared>0,"height score uses long arithmetic");
        boolean refused=false;try{new SafeParkCandidates(0,0,0,17,0,255);}catch(IllegalArgumentException expected){refused=true;}need(refused,"oversized radius rejected");
        refused=false;try{new SafeParkCandidates(0,0,0,1,-2048,2048);}catch(IllegalArgumentException expected){refused=true;}need(refused,"oversized height allocation rejected");
        System.out.println("Safe Park candidate search PASS checks="+checks+" nearest/loaded-only/sliced/height-overflow/exhaustion");
    }
}
