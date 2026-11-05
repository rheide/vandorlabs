package com.vandorlabs.surface;
import java.util.*;
public final class WindowOutlineLocalSliceTest {
    private static long cases,coordinates;
    private static void same(List<double[]> expected,List<double[]> actual,String key){
        if(expected.size()!=actual.size())throw new AssertionError(key+" rows "+expected.size()+" != "+actual.size()+" expected="+text(expected)+" actual="+text(actual));
        for(int i=0;i<expected.size();i++){double[] a=expected.get(i),b=actual.get(i);if(a.length!=b.length)throw new AssertionError(key+" width");for(int n=0;n<a.length;n++){coordinates++;if(Double.doubleToRawLongBits(a[n])!=Double.doubleToRawLongBits(b[n]))throw new AssertionError(key+" ["+i+"]["+n+"] "+a[n]+" != "+b[n]+" expected="+text(expected)+" actual="+text(actual));}}
    }
    private static String text(List<double[]> values){StringBuilder s=new StringBuilder();for(double[] value:values)s.append(Arrays.toString(value));return s.toString();}
    private static void check(WindowOutline outline,int columns,int rows,int shape,int border,int column,int row){
        WindowOutline.Slice full=outline.slice(column,row),local=WindowOutline.localSlice(columns,rows,shape,border,column,row);String key=columns+"x"+rows+" shape"+shape+" border"+border+" cell"+column+","+row;
        same(full.polygon,local.polygon,key+" polygon");same(full.frameQuads,local.frameQuads,key+" frame");same(full.hexEdges,local.hexEdges,key+" edges");same(full.glassQuads,local.glassQuads,key+" glass");cases++;
    }
    private static void rejected(int columns,int rows,int shape,int border,int column,int row) {
        try { WindowOutline.localSlice(columns,rows,shape,border,column,row); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid slice accepted");
    }
    public static void main(String[] ignored){
        rejected(0,1,0,0,0,0); rejected(1,1,4,0,0,0);
        rejected(1,1,0,2,0,0); rejected(1,1,0,0,1,0);
        rejected(Integer.MAX_VALUE,1,3,0,0,0);
        rejected(1,Integer.MAX_VALUE,3,0,0,0);
        for(int columns=1;columns<=6;columns++)for(int rows=1;rows<=6;rows++)for(int shape=0;shape<4;shape++)for(int border=0;border<2;border++){WindowOutline outline=new WindowOutline(columns,rows,shape,border);for(int row=0;row<rows;row++)for(int column=0;column<columns;column++)check(outline,columns,rows,shape,border,column,row);}
        for(int border=0;border<2;border++)for(int[] size:new int[][]{{1,4095},{4095,1},{2,2047},{2047,2}}){int columns=size[0],rows=size[1];WindowOutline outline=new WindowOutline(columns,rows,WindowOutline.ROUND,border);Set<Integer> sampleRows=new LinkedHashSet<Integer>(Arrays.asList(0,1,rows/4,rows/2,rows*3/4,rows-2,rows-1));Set<Integer> sampleColumns=new LinkedHashSet<Integer>(Arrays.asList(0,1,columns/2,columns-2,columns-1));for(int row:sampleRows)for(int column:sampleColumns)if(row>=0&&row<rows&&column>=0&&column<columns)check(outline,columns,rows,WindowOutline.ROUND,border,column,row);}
        System.out.println("PASS: local aperture slices retain exact raw polygon anchor/winding, frame/edge/glass arrays across "+cases+" cells and "+coordinates+" coordinates including legal thin4095 groups");
    }
}
