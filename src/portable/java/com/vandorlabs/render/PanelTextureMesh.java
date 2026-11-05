package com.vandorlabs.render;

/** Current repeated panel-UV subdivision; input float records and native outputs stay host-owned. */
public final class PanelTextureMesh {
    public interface Sink<C,M>{
        void atlas(C context,M material,double x,double y,double z,double u,double v,float nx,float ny,float nz);
        void pixel(C context,M material,double x,double y,double z,double u,double v,float nx,float ny,float nz);
    }
    public static <C,M> void map(Sink<C,M> sink,C context,M material,float[] vertices,
            float lowU,float highU,float lowV,float highV,double aspect,int column,int row,int columns,int rows,
            boolean flipU,boolean flipV,boolean raw,double borderX,double borderY,int mode){
        for(int q=0;q<vertices.length;q+=32){
            java.util.List<double[]> polygon=new java.util.ArrayList<>();boolean selected=true;
            double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU,unitU=1,unitV=1;
            for(int i=0;i<4;i++){
                double[] p=new double[8];for(int j=0;j<8;j++)p[j]=vertices[q+i*8+j];
                p[3]=raw?p[3]:(p[3]-lowU)/(highU-lowU);p[4]=raw?p[4]:(p[4]-lowV)/(highV-lowV);
                if(flipU)p[3]=1-p[3];if(flipV)p[4]=1-p[4];
                selected &= p[3]>=-1e-4 && p[3]<=1.0001 && p[4]>=-1e-4 && p[4]<=1.0001;
                minU=Math.min(minU,p[3]);maxU=Math.max(maxU,p[3]);minV=Math.min(minV,p[4]);maxV=Math.max(maxV,p[4]);polygon.add(p);
            }
            if(!selected){for(int i=0;i<4;i++){int p=q+i*8;sink.atlas(context,material,vertices[p],vertices[p+1],vertices[p+2],vertices[p+3],vertices[p+4],vertices[p+5],vertices[p+6],vertices[p+7]);}continue;}
            for(double[] a:polygon)for(double[] b:polygon){double du=Math.abs(a[3]-b[3]),dv=Math.abs(a[4]-b[4]),length=Math.sqrt((a[0]-b[0])*(a[0]-b[0])+(a[1]-b[1])*(a[1]-b[1])+(a[2]-b[2])*(a[2]-b[2]))/16;
                if(du>1e-6 && dv<1e-6)unitU=length/du;if(dv>1e-6 && du<1e-6)unitV=length/dv;
            }
            boolean fit=com.vandorlabs.render.TextureLayoutMode.fit(mode,(columns-2*borderX)*unitU,(rows-2*borderY)*unitV,aspect);
            minU=minV=Double.POSITIVE_INFINITY;maxU=maxV=Double.NEGATIVE_INFINITY;
            for(double[] p:polygon){
                p[3]=com.vandorlabs.render.PanelTextureMapping.u(fit,p[3],column,columns,borderX,unitU,aspect);
                p[4]=com.vandorlabs.render.PanelTextureMapping.v(fit,p[4],row,rows,borderY,unitV,aspect);
                if(fit){p[3]=Math.max(0,Math.min(1,p[3]));p[4]=Math.max(0,Math.min(1,p[4]));}
                minU=Math.min(minU,p[3]);maxU=Math.max(maxU,p[3]);minV=Math.min(minV,p[4]);maxV=Math.max(maxV,p[4]);
            }
            int u0=fit?0:(int)Math.floor(minU),u1=fit?0:(int)Math.ceil(maxU-1e-8)-1,v0=fit?0:(int)Math.floor(minV),v1=fit?0:(int)Math.ceil(maxV-1e-8)-1;
            for(int u=u0;u<=u1;u++)for(int v=v0;v<=v1;v++){
                java.util.List<double[]> clipped=PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(PanelTextureMapping.clip(polygon,3,u,true),3,u+1,false),4,v,true),4,v+1,false);
                for(int i=1;i+1<clipped.size();i++)for(double[] p:new double[][]{clipped.get(0),clipped.get(i),clipped.get(i+1),clipped.get(i+1)})sink.pixel(context,material,p[0],p[1],p[2],Math.max(0,Math.min(1,p[3]-u))*16,Math.max(0,Math.min(1,p[4]-v))*16,(float)p[5],(float)p[6],(float)p[7]);
            }
        }
    }
    private PanelTextureMesh(){}
}
