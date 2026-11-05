package com.vandorlabs.fluid;

import java.util.List;

/** Longest-axis coalescing over caller-owned cuts, with direct platform cells and no face graph. */
public abstract class FluidRuns<B,C,W,P> {
    protected FluidRuns(){}
    protected abstract double minimum(B box,int axis);
    protected abstract double maximum(B box,int axis);
    protected abstract int side(W world,P position,double[] aperture,double x,double y,double z);
    protected abstract C createCell(double minX,double minY,double minZ,double maxX,double maxY,double maxZ,int side);
    /** Appends in the current run order over read-only solid/cuts inputs. Adapters are reusable; opaque context stays on this call's stack. */
    public final void append(List<C> cells,List<B> solid,double[][] cuts,W world,P position,double[] aperture){
        int[] sizes={cuts[0].length-1,cuts[1].length-1,cuts[2].length-1};int axis=2;for(int d=0;d<2;d++)if(sizes[d]>sizes[axis])axis=d;
        int a=(axis+1)%3,b=(axis+2)%3;double[] low=new double[3],high=new double[3],center=new double[3];
        for(int i=0;i<sizes[a];i++)for(int j=0;j<sizes[b];j++){
            low[a]=cuts[a][i];high[a]=cuts[a][i+1];center[a]=(low[a]+high[a])*.5;
            low[b]=cuts[b][j];high[b]=cuts[b][j+1];center[b]=(low[b]+high[b])*.5;
            int run=-1;double start=0;
            for(int k=0;k<sizes[axis];k++){
                center[axis]=(cuts[axis][k]+cuts[axis][k+1])*.5;double cx=center[0],cy=center[1],cz=center[2];
                int side=side(world,position,aperture,cx,cy,cz);
                for(int n=0;n<solid.size();n++){B box=solid.get(n);if(cx>minimum(box,0)&&cx<maximum(box,0)&&cy>minimum(box,1)&&cy<maximum(box,1)&&cz>minimum(box,2)&&cz<maximum(box,2)){side=-1;break;}}
                if(side!=run){if(run>=0)add(cells,low,high,axis,start,cuts[axis][k],run);run=side;start=cuts[axis][k];}
            }
            if(run>=0)add(cells,low,high,axis,start,cuts[axis][sizes[axis]],run);
        }
    }
    private void add(List<C> cells,double[] low,double[] high,int axis,double start,double end,int side){
        low[axis]=start;high[axis]=end;cells.add(createCell(low[0],low[1],low[2],high[0],high[1],high[2],side));
    }
}
