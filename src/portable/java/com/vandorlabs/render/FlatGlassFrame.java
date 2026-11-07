package com.vandorlabs.render;

/** Released ordinary glass rail/backing and authored nine-slice face geometry in pixel coordinates. */
public final class FlatGlassFrame {
    public interface Sink<C,M>{void quad(C context,M material,boolean edgeMapping,boolean reverse,double[] a,double[] b,double[] c,double[] d);}
    private FlatGlassFrame(){}
    public static <C,M> void emit(Sink<C,M> sink,C context,int mask,boolean selected,boolean border,double[] widths,double aspect,M rail,M interior,M exterior,M artwork){
        double left=(mask&1)!=0?(selected?widths[0]*16:1):0,right=(mask&2)!=0?(selected?widths[1]*16:1):0;
        double bottom=(mask&4)!=0?(selected?widths[3]*16:1):0,top=(mask&8)!=0?(selected?widths[2]*16:1):0;
        double inset=selected&&border?.016:0;
        if(left>0)box(sink,context,rail,interior,exterior,inset,0,0,left,16);
        if(right>0)box(sink,context,rail,interior,exterior,inset,16-right,0,16,16);
        if(bottom>0)box(sink,context,rail,interior,exterior,inset,left,0,16-right,bottom);
        if(top>0)box(sink,context,rail,interior,exterior,inset,left,16-top,16-right,16);
        for(int corner=0;corner<4;corner++)if((mask&(16<<corner))!=0){boolean r=(corner&1)!=0,t=(corner&2)!=0;double w=selected?widths[r?1:0]*16:1,h=selected?widths[t?2:3]*16:1;box(sink,context,rail,interior,exterior,inset,r?16-w:0,t?16-h:0,r?16:w,t?16:h);}
        if(!selected||!border)return;
        double l=(mask&1)!=0?widths[0]:0,r=(mask&2)!=0?widths[1]:0,t=(mask&8)!=0?widths[2]:0,b=(mask&4)!=0?widths[3]:0;
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)if(row!=1||col!=1){double x0=cut(col,l,r)*16,x1=cut(col+1,l,r)*16,y0=(1-cut(row+1,t,b))*16,y1=(1-cut(row,t,b))*16;if(x1<=x0||y1<=y0)continue;face(sink,context,artwork,x0,y0,x1,y1,cut(col,widths[0],widths[1])*16,cut(row,widths[2],widths[3])*16/aspect,cut(col+1,widths[0],widths[1])*16,cut(row+1,widths[2],widths[3])*16/aspect);}
        for(int corner=0;corner<4;corner++)if((mask&(16<<corner))!=0){boolean rightCorner=(corner&1)!=0,topCorner=(corner&2)!=0;double w=widths[rightCorner?1:0],h=widths[topCorner?2:3];face(sink,context,artwork,rightCorner?16-w*16:0,topCorner?16-h*16:0,rightCorner?16:w*16,topCorner?16:h*16,rightCorner?(1-w)*16:0,topCorner?0:(1-h)*16/aspect,rightCorner?16:w*16,topCorner?h*16/aspect:16/aspect);}
    }
    private static double cut(int index,double first,double last){return index==0?0:index==1?first:index==2?1-last:1;}
    private static <C,M> void face(Sink<C,M> sink,C context,M material,double x,double y,double r,double t,double u0,double v0,double u1,double v1){for(int side=0;side<2;side++){double z=side==0?6:10;sink.quad(context,material,false,side!=0,new double[]{x,y,z,u0,v1},new double[]{x,t,z,u0,v0},new double[]{r,t,z,u1,v0},new double[]{r,y,z,u1,v1});}}
    private static <C,M> void box(Sink<C,M> sink,C context,M front,M inner,M outer,double inset,double x,double y,double r,double t){double u0=x,u1=r,v0=16-t,v1=16-y;
        if(front!=null){sink.quad(context,front,true,false,new double[]{x,y,6+inset,u0,v1},new double[]{x,t,6+inset,u0,v0},new double[]{r,t,6+inset,u1,v0},new double[]{r,y,6+inset,u1,v1});sink.quad(context,front,true,true,new double[]{x,y,10-inset,u0,v1},new double[]{x,t,10-inset,u0,v0},new double[]{r,t,10-inset,u1,v0},new double[]{r,y,10-inset,u1,v1});}
        sink.quad(context,y==0?outer:inner,true,false,new double[]{x,y,6,x,0},new double[]{r,y,6,r,0},new double[]{r,y,10,r,4},new double[]{x,y,10,x,4});
        sink.quad(context,t==16?outer:inner,true,true,new double[]{x,t,6,x,0},new double[]{r,t,6,r,0},new double[]{r,t,10,r,4},new double[]{x,t,10,x,4});
        sink.quad(context,x==0?outer:inner,true,true,new double[]{x,y,6,0,16-y},new double[]{x,t,6,0,16-t},new double[]{x,t,10,4,16-t},new double[]{x,y,10,4,16-y});
        sink.quad(context,r==16?outer:inner,true,false,new double[]{r,y,6,0,16-y},new double[]{r,t,6,0,16-t},new double[]{r,t,10,4,16-t},new double[]{r,y,10,4,16-y});
    }
}
