package com.vandorlabs.redstone;

/** Literal bit keys, signed cell edges and sphere limits independent of Minecraft adapters. */
public final class ChannelSpatialPolicyTest {
    private static int cases;
    private static void check(boolean pass,String message){cases++;if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int[] row:new int[][]{{-1,0},{0,0},{4,0},{8,0},{16,0},{17,1},{32,1},{64,1},{65,2},{128,2},{256,2}})check(ChannelSpatialPolicy.tier(row[0])==row[1],"range tier boundary");
        check(ChannelSpatialPolicy.cellSize(0)==16&&ChannelSpatialPolicy.cellSize(1)==64&&ChannelSpatialPolicy.cellSize(2)==256,"three exact cell sizes");
        check(ChannelSpatialPolicy.packedCell(0,0,0)==0L&&ChannelSpatialPolicy.packedCell(1,0,0)==274877906944L&&ChannelSpatialPolicy.packedCell(0,1,0)==67108864L&&ChannelSpatialPolicy.packedCell(0,0,1)==1L,"axis bit placement");
        check(ChannelSpatialPolicy.packedCell(-1,-1,-1)==-1L,"all sign bits retain mask pattern");
        check(ChannelSpatialPolicy.packedCell(33554431,2047,33554431)==9223371899382267903L&&ChannelSpatialPolicy.packedCell(-33554432,-2048,-33554432)==-9223371899382267904L,"signed26/12/26-bit boundaries retain exact key literals");
        check(ChannelSpatialPolicy.packedCell(-2,-2,-2)==-274945015810L,"negative packed cell literal");
        check(ChannelSpatialPolicy.key(-1,-1,-1,16)==-1L&&ChannelSpatialPolicy.key(-16,-16,-16,16)==-1L&&ChannelSpatialPolicy.key(-17,-17,-17,16)==-274945015810L,"negative floor division around cell boundary");
        check(ChannelSpatialPolicy.key(16,16,16,16)==274945015809L,"positive cell boundary literal");
        for(int range:new int[]{4,8,16,32,64,128,256})for(int[] origin:new int[][]{{0,0,0},{-29999984,255,29999984},{29999984,-256,-29999984}})for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            int[] edge=origin.clone();edge[axis]+=sign*range;
            check(ChannelSpatialPolicy.finiteContains(origin[0],origin[1],origin[2],edge[0],edge[1],edge[2],range),"inclusive cardinal sphere edge");
            edge[(axis+1)%3]++;check(!ChannelSpatialPolicy.finiteContains(origin[0],origin[1],origin[2],edge[0],edge[1],edge[2],range),"sphere excludes edge plus orthogonal block");
        }
        check(ChannelSpatialPolicy.finiteContains(0,0,0,3,4,0,5)&&!ChannelSpatialPolicy.finiteContains(0,0,0,3,4,1,5),"noncardinal Pythagorean edge");
        check(!ChannelSpatialPolicy.finiteContains(-30000000,0,0,30000000,0,0,256),"opposite world boundaries do not overlap");
        check(!ChannelSpatialPolicy.finiteContains(Integer.MIN_VALUE,0,0,Integer.MAX_VALUE,0,0,Integer.MAX_VALUE),"coordinate subtraction must widen before overflowing int");
        check(ChannelSpatialPolicy.finiteContains(Integer.MIN_VALUE,0,0,-1,0,0,Integer.MAX_VALUE)&&!ChannelSpatialPolicy.finiteContains(Integer.MIN_VALUE,0,0,0,0,0,Integer.MAX_VALUE),"large double sphere inside/outside boundary");
        check(ChannelSpatialPolicy.finiteContains(1,2,3,1,2,3,0)&&!ChannelSpatialPolicy.finiteContains(1,2,3,2,2,3,0),"finite zero sphere stays distinct from host unbounded semantics");
        for(int size:new int[]{16,64,256})for(int cell:new int[]{-1875000,-17,-1,0,1,17,1875000}){
            int start=cell*size;long key=ChannelSpatialPolicy.key(start,0,0,size);
            check(key==ChannelSpatialPolicy.key(start+size-1,0,0,size),"all positions inside a cell share one key");
            check(key!=ChannelSpatialPolicy.key(start-1,0,0,size)&&key!=ChannelSpatialPolicy.key(start+size,0,0,size),"neighboring cells stay distinct at world-scale coordinates");
        }
        System.out.println("ChannelSpatialPolicy contracts PASS cases="+cases+" signed-floor packed-goldens seven-ranges sphere-world-extreme-boundaries");
    }
}
