package com.vandorlabs.render;

public final class SlabMergePolicyTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] ignored){int cases=0;float[] hits={Float.NEGATIVE_INFINITY,-1,0,Math.nextDown(.5F),.5F,Math.nextUp(.5F),1,2,Float.POSITIVE_INFINITY,Float.NaN};boolean[] wallUpper={false,false,false,false,false,true,true,true,true,false},lowerDirect={false,true,false,false,false,false},upperDirect={true,false,false,false,false,false};for(int face=0;face<6;face++)for(int i=0;i<hits.length;i++){require(SlabMergePolicy.upperHalf(face,hits[i])==(face==0||face>1&&wallUpper[i]),"literal placement truth table");require(SlabMergePolicy.directMerge(false,face)==lowerDirect[face]&&SlabMergePolicy.directMerge(true,face)==upperDirect[face],"literal inner-face merge truth table");cases++;}System.out.println("SlabMergePolicy PASS cases="+cases+" all6faces, strict float midpoint, NaN/extremes and direct half merge");}
}
