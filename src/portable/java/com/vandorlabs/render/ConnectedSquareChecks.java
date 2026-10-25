package com.vandorlabs.render;

import java.util.*;

/** Headless regression for complete, seed-independent propulsion square partitions. */
public final class ConnectedSquareChecks {
    private static long key(int x,int y){return(long)x<<32|(y&0xffffffffL);}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("Thruster squares: "+reason);}
    public static void main(String[] args){run();}
    public static void run(){int cases=0;
        for(int max:new int[]{2,4,8})for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1})for(boolean transpose:new boolean[]{false,true}){
            Set<Long> cells=new HashSet<>();for(int square=0;square<2;square++)for(int x=0;x<2;x++)for(int y=0;y<2;y++){int px=(x+square*2)*sx,py=(y+square)*sy;cells.add(key(transpose?py:px,transpose?px:py));}Map<String,Integer> groups=new HashMap<>();
            for(long cell:cells){int x=(int)(cell>>32),y=(int)cell;ConnectedSquare.Part part=ConnectedSquare.findPartitioned(max,(dx,dy)->cells.contains(key(x+dx,y+dy)));require(part.size==2,"staggered member joined as2x2");String group=(x-part.x)+","+(y-part.y);groups.put(group,groups.getOrDefault(group,0)+1);for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++)require(cells.contains(key(x-part.x+dx,y-part.y+dy)),"selected square contains only real cells");cases++;}require(groups.size()==2&&groups.values().stream().allMatch(count->count==4),"every seed agrees on two nonoverlapping complete squares");
        }
        for(int[][] invalid:new int[][][]{{{0,0},{1,0},{0,1},{1,1},{2,0}},{{0,0},{1,0},{0,1},{1,1},{0,2},{1,2}}}){Set<Long> cells=new HashSet<>();for(int[] cell:invalid)cells.add(key(cell[0],cell[1]));for(int[] cell:invalid){require(ConnectedSquare.findPartitioned(8,(x,y)->cells.contains(key(x+cell[0],y+cell[1]))).isSingle(),"incomplete touching remainder never causes a partial join");cases++;}}
        for(int x=0;x<6;x++)for(int y=0;y<2;y++){final int px=x,py=y;require(ConnectedSquare.findPartitioned(2,(dx,dy)->dx+px>=0&&dx+px<6&&dy+py>=0&&dy+py<2).size==2,"three adjacent2x2 squares remain bounded and join consistently");cases++;}
        require(ConnectedSquare.findPartitioned(8,(x,y)->true).isSingle(),"oversized component refuses bounded discovery");System.out.println("[vandorlabs] thruster-square-partitions PASS cases="+cases+" mirrored/transposed stagger2x2 allseeds complete-cover no-partial bounded");
    }
    private ConnectedSquareChecks(){}
}
