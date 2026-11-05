package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.NativePortholeLayout;
import com.vandorlabs.surface.WindowOutline;
import java.util.List;

/** Owned, scalar-only aperture data: cached mutable WindowOutline arrays never escape. */
final class NativePanelSlice {
    private record Key(int columns,int rows,int column,int row,int shape,int border){}
    private static final com.google.common.cache.Cache<Key,NativePanelSlice> CACHE=com.google.common.cache.CacheBuilder.newBuilder()
            .maximumWeight(2*1024*1024).weigher((Key key,NativePanelSlice slice)->slice.cost()).build();
    private final double[] frame,edge,glass,openings;
    private static double[] copy(List<double[]> source,int stride){double[] result=new double[Math.multiplyExact(source.size(),stride)];int offset=0;for(double[] value:source){if(value.length!=stride)throw new IllegalArgumentException("Aperture record stride");System.arraycopy(value,0,result,offset,stride);offset+=stride;}return result;}
    NativePanelSlice(WindowOutline.Slice source){
        frame=copy(source.frameQuads,8);edge=copy(source.hexEdges,4);glass=copy(source.glassQuads,8);openings=new double[8];java.util.Arrays.fill(openings,Double.NaN);
        for(int axis=0;axis<2;axis++)for(int side=0;side<2;side++){double[] opening=source.edgeOpening(axis,side*16);if(opening!=null){int at=(axis*2+side)*2;openings[at]=opening[0];openings[at+1]=opening[1];}}
    }
    static NativePanelSlice get(NativePortholeLayout descriptor){
        var pane=descriptor.pane();Key key=new Key(pane.columns(),pane.rows(),pane.flipU()?pane.columns()-1-pane.column():pane.column(),pane.flipV()?pane.rows()-1-pane.row():pane.row(),descriptor.shape(),descriptor.extraBorder());var found=CACHE.getIfPresent(key);if(found!=null)return found;
        try{return CACHE.get(key,()->new NativePanelSlice(WindowOutline.localSlice(key.columns,key.rows,key.shape,key.border,key.column,key.row)));}
        catch(java.util.concurrent.ExecutionException failure){throw new IllegalStateException("Aperture construction failed",failure.getCause());}
    }
    int frameCount(){return frame.length/8;}double frame(int quad,int value){return frame[quad*8+value];}
    int edgeCount(){return edge.length/4;}double edge(int segment,int value){return edge[segment*4+value];}
    int glassCount(){return glass.length/8;}double glass(int quad,int value){return glass[quad*8+value];}
    double opening(int axis,int side,int end){return openings[(axis*2+side)*2+end];}
    int cost(){return 384+(frame.length+edge.length+glass.length+openings.length)*Double.BYTES;}
    static long retainedBytes(){CACHE.cleanUp();long result=0;for(var value:CACHE.asMap().values())result+=value.cost();return result;}
}
