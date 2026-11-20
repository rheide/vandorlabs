package com.vandorlabs.render;

import java.util.*;

/** Joined Large Door tiling is the same from every module of a cluster. */
public final class LargeDoorJoinChecks {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    /** Resolve every module of a layout; returns the group anchor per module as "x,y". */
    private static Map<String,String> groups(int[][] modules){
        Set<String> present=new HashSet<>();for(int[] m:modules)present.add(m[0]+","+m[1]);
        Map<String,String> result=new HashMap<>();
        for(int[] m:modules){
            LargeDoorJoin.Group<String> group=LargeDoorJoin.resolve(m[0]+","+m[1],(dx,dy)->{String k=(m[0]+dx)+","+(m[1]+dy);return present.contains(k)?k:null;});
            require(group.members.contains(m[0]+","+m[1]),"group contains its start");
            require(group.members.size()==group.columns*group.rows,"group is a complete rectangle");
            for(String member:group.members){
                // Every member must resolve to the same group.
                String[] p=member.split(",");int x=Integer.parseInt(p[0]),y=Integer.parseInt(p[1]);
                LargeDoorJoin.Group<String> other=LargeDoorJoin.resolve(member,(dx,dy)->{String k=(x+dx)+","+(y+dy);return present.contains(k)?k:null;});
                require(other.anchor.equals(group.anchor)&&other.members.equals(group.members),"consistent group from "+member);
            }
            result.put(m[0]+","+m[1],group.anchor+" "+group.columns+"x"+group.rows);
        }
        return result;
    }
    public static void main(String[] args){
        Map<String,String> l=groups(new int[][]{{0,0},{1,0},{0,1}});
        require(l.get("0,0").equals("0,0 2x1")&&l.get("1,0").equals("0,0 2x1")&&l.get("0,1").equals("0,1 1x1"),"L keeps its pair "+l);
        Map<String,String> square=groups(new int[][]{{0,0},{1,0},{0,1},{1,1}});
        for(String v:square.values())require(v.equals("0,0 2x2"),"2x2 joins "+square);
        Map<String,String> row=groups(new int[][]{{0,0},{1,0},{2,0}});
        require(row.get("0,0").equals("0,0 2x1")&&row.get("2,0").equals("2,0 1x1"),"row of three "+row);
        Map<String,String> column=groups(new int[][]{{0,0},{0,1}});
        require(column.get("0,0").equals("0,0 1x2")&&column.get("0,1").equals("0,0 1x2"),"vertical pair "+column);
        Map<String,String> wide=groups(new int[][]{{0,0},{1,0},{2,0},{0,1},{1,1},{2,1}});
        require(wide.get("0,0").equals("0,0 2x2")&&wide.get("2,0").equals("2,0 1x2"),"2x3 tiles a square and a pair "+wide);
        System.out.println("large-door-join portable PASS checks="+checks);
    }
}
