package com.vandorlabs.render;

/** Current cached opaque outline rails and cap walls from owned float-record panel geometry. */
public final class PanelOutlineFrame {
    private PanelOutlineFrame(){}
    public interface Textures<M> {
        M edge(M sprite,double[] a,double[] b);
        boolean authored(M original,M edge);
        M map(M sprite,double[][] points);
        double unU(M sprite,float u);
        double unV(M sprite,float v);
    }
    public static <C,M> void emit(PanelTextureMesh.Sink<C,M> sink,C context,Textures<M> textures,float[] vertices,M sprite,M pane,M interior,M exterior,
            boolean hideLeft,boolean hideRight,boolean hideFront,boolean hideBack,boolean hideLower,boolean hideUpper,int rowAxis,boolean flat){
        java.util.Map<String,Integer> edges=new java.util.HashMap<>();
        for(int q=0;q<vertices.length;q+=32)if(paneFace(vertices,q,flat,rowAxis))for(int e=0;e<4;e++) {
            String key=edgeKey(vertices,q+e*8,q+((e+1)&3)*8);edges.put(key,edges.getOrDefault(key,0)+1);
        }
        for(int q=0;q<vertices.length;q+=32)if(paneFace(vertices,q,flat,rowAxis))for(int e=0;e<4;e++) {
            int ai=q+e*8,bi=q+((e+1)&3)*8;
            if(edges.get(edgeKey(vertices,ai,bi))!=1)continue;
            if(hideLeft && edgePlane(vertices,ai,bi,0,0) || hideRight && edgePlane(vertices,ai,bi,0,16)
                    || hideFront && edgePlane(vertices,ai,bi,2,0) || hideBack && edgePlane(vertices,ai,bi,2,16)
                    || hideLower && edgePlane(vertices,ai,bi,rowAxis,0) || hideUpper && edgePlane(vertices,ai,bi,rowAxis,16))continue;
            double dx=vertices[bi]-vertices[ai],dy=vertices[bi+1]-vertices[ai+1],dz=vertices[bi+2]-vertices[ai+2];
            double length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1E-5)continue;
            float nx=vertices[ai+5],ny=vertices[ai+6],nz=vertices[ai+7];
            double ix=(ny*dz-nz*dy)/length,iy=(nz*dx-nx*dz)/length,iz=(nx*dy-ny*dx)/length;
            double cx=0,cy=0,cz=0;
            for(int v=0;v<4;v++){cx+=vertices[q+v*8];cy+=vertices[q+v*8+1];cz+=vertices[q+v*8+2];}
            if(ix*(cx/4-vertices[ai])+iy*(cy/4-vertices[ai+1])+iz*(cz/4-vertices[ai+2])<0){ix=-ix;iy=-iy;iz=-iz;}
            // A tiny outward offset keeps rails separate from the underlying transparent pane.
            double ox=nx*.01,oy=ny*.01,oz=nz*.01;
            M cutRail=textures.edge(sprite,new double[]{vertices[ai],vertices[ai+1],vertices[ai+2]},new double[]{vertices[bi],vertices[bi+1],vertices[bi+2]});
            boolean authored=textures.authored(sprite,cutRail);
            for(int v=0;v<4;v++) {
                int source=v==0 || v==3?ai:bi;boolean inset=v>=2;
                sink.pixel(context,cutRail,vertices[source]+ox+(inset?ix:0),vertices[source+1]+oy+(inset?iy:0),vertices[source+2]+oz+(inset?iz:0),
                        authored?(v==0||v==3?0:16):(inset?.5:0),authored?(inset?16:0):(v==0||v==3?0:Math.min(8,length/2)),nx,ny,nz);
            }
        }
        // The transparent mesh already contains the exact thickness caps, including
        // clipped corner arms. Close each exposed rim with opaque outer and inner walls.
        for(int q=0;q<vertices.length;q+=32)if(!paneFace(vertices,q,flat,rowAxis) && !hiddenCap(vertices,q,hideLeft,hideRight,hideFront,hideBack,hideLower,hideUpper,rowAxis)) {
            double[] inset=capInset(vertices,q,edges,flat,rowAxis);if(inset==null)continue;
            for(int side=0;side<2;side++) {
                double[][] points=new double[4][5];
                for(int v=0;v<4;v++){
                    int source=q+(side==0?v:3-v)*8;float sign=side==0?1:-1;
                    points[v]=new double[]{vertices[source]+(side==0?0:inset[0])+vertices[q+5]*sign*.01,
                        vertices[source+1]+(side==0?0:inset[1])+vertices[q+6]*sign*.01,
                        vertices[source+2]+(side==0?0:inset[2])+vertices[q+7]*sign*.01,
                        interior==null?(v==0||v==3?.16:.28):textures.unU(pane,vertices[source+3]),
                        interior==null?(v<2?4:4.12):textures.unV(pane,vertices[source+4])};
                }
                M cut=interior==null?sprite:textures.map(side==0?exterior:interior,points);
                for(double[] p:points)sink.pixel(context,cut,p[0],p[1],p[2],p[3],p[4],vertices[q+5]*(side==0?1:-1),vertices[q+6]*(side==0?1:-1),vertices[q+7]*(side==0?1:-1));
            }
        }
    }
    private static boolean hiddenCap(float[] vertices,int q,boolean left,boolean right,boolean front,boolean back,boolean lower,boolean upper,int axis) {
        return left && capPlane(vertices,q,0,0) || right && capPlane(vertices,q,0,16) || front && capPlane(vertices,q,2,0) || back && capPlane(vertices,q,2,16) || lower && capPlane(vertices,q,axis,0) || upper && capPlane(vertices,q,axis,16);
    }
    private static boolean capPlane(float[] vertices,int q,int axis,float value){for(int v=0;v<4;v++)if(Math.abs(vertices[q+v*8+axis]-value)>1E-4)return false;return true;}
    private static boolean capContains(float[] vertices,int q,int point){for(int v=0;v<4;v++){int p=q+v*8;if(Math.abs(vertices[p]-vertices[point])+Math.abs(vertices[p+1]-vertices[point+1])+Math.abs(vertices[p+2]-vertices[point+2])<1E-4)return true;}return false;}
    private static double[] capInset(float[] vertices,int cap,java.util.Map<String,Integer> edges,boolean flat,int rowAxis){
        for(int q=0;q<vertices.length;q+=32)if(paneFace(vertices,q,flat,rowAxis))for(int e=0;e<4;e++){
            int a=q+e*8,b=q+((e+1)&3)*8;if(edges.get(edgeKey(vertices,a,b))!=1 || !capContains(vertices,cap,a) || !capContains(vertices,cap,b))continue;
            double[] inset=edgeInset(vertices,q,a,b);if(inset!=null)return inset;
        }
        // Cross arms split the pane outline, while the bridge roof remains one
        // rectangle. Its exposed edge can therefore be only part of a cap edge.
        // Keep exact matches first so existing L frames retain their geometry.
        if(flat)for(int q=0;q<vertices.length;q+=32)if(paneFace(vertices,q,true))for(int e=0;e<4;e++) {
            int a=q+e*8,b=q+((e+1)&3)*8;
            if(edges.get(edgeKey(vertices,a,b))!=1 || !capEdgeContains(vertices,cap,a,b))continue;
            double[] inset=edgeInset(vertices,q,a,b);if(inset!=null)return inset;
        }
        return null;
    }
    private static boolean capEdgeContains(float[] vertices,int cap,int a,int b) {
        for(int e=0;e<4;e++) {
            int start=cap+e*8,end=cap+((e+1)&3)*8;
            if(onSegment(vertices,a,start,end) && onSegment(vertices,b,start,end))return true;
        }
        return false;
    }
    private static boolean onSegment(float[] vertices,int point,int start,int end) {
        double length=0,dot=0;
        for(int axis=0;axis<3;axis++) {double delta=vertices[end+axis]-vertices[start+axis];length+=delta*delta;dot+=(vertices[point+axis]-vertices[start+axis])*delta;}
        if(length<1E-10)return false;
        double t=dot/length;if(t< -1E-5 || t>1+1E-5)return false;
        for(int axis=0;axis<3;axis++)if(Math.abs(vertices[point+axis]-vertices[start+axis]-t*(vertices[end+axis]-vertices[start+axis]))>1E-4)return false;
        return true;
    }
    private static double[] edgeInset(float[] vertices,int q,int a,int b) {
        double dx=vertices[b]-vertices[a],dy=vertices[b+1]-vertices[a+1],dz=vertices[b+2]-vertices[a+2],length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1E-5)return null;
        double x=(vertices[a+6]*dz-vertices[a+7]*dy)/length,y=(vertices[a+7]*dx-vertices[a+5]*dz)/length,z=(vertices[a+5]*dy-vertices[a+6]*dx)/length;
        double cx=0,cy=0,cz=0;for(int v=0;v<4;v++){cx+=vertices[q+v*8];cy+=vertices[q+v*8+1];cz+=vertices[q+v*8+2];}
        if(x*(cx/4-vertices[a])+y*(cy/4-vertices[a+1])+z*(cz/4-vertices[a+2])<0){x=-x;y=-y;z=-z;}return new double[]{x,y,z};
    }
    private static boolean paneFace(float[] vertices,int q) {
        // Sloping face normals distinguish pane faces from the narrow end/depth caps.
        return Math.abs(vertices[q+6])>1E-5 && Math.abs(vertices[q+6])<.99999;
    }
    private static boolean paneFace(float[] vertices,int q,boolean flat,int rowAxis){
        // End transitions can become straight when a half-width slope meets a flat wall.
        // They remain pane faces; treating them as thickness caps drops the last four pixels of rail.
        return paneFace(vertices,q,flat)||!flat&&Math.abs(vertices[q+5+(rowAxis==1?2:1)])>.99999;
    }
    private static boolean paneFace(float[] vertices,int q,boolean flat) {
        if(!flat)return paneFace(vertices,q);
        if(Math.abs(vertices[q+6])>1E-5)return false;
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=minX,maxZ=maxX;
        for(int v=0;v<4;v++) {
            minX=Math.min(minX,vertices[q+v*8]);maxX=Math.max(maxX,vertices[q+v*8]);
            minZ=Math.min(minZ,vertices[q+v*8+2]);maxZ=Math.max(maxZ,vertices[q+v*8+2]);
        }
        return Math.hypot(maxX-minX,maxZ-minZ)>4.00001;
    }
    private static boolean edgePlane(float[] vertices,int a,int b,int axis,float plane) {
        return Math.abs(vertices[a+axis]-plane)<1E-4 && Math.abs(vertices[b+axis]-plane)<1E-4;
    }
    private static String edgeKey(float[] vertices,int a,int b) {
        String aa=Math.round(vertices[a]*10000)+","+Math.round(vertices[a+1]*10000)+","+Math.round(vertices[a+2]*10000);
        String bb=Math.round(vertices[b]*10000)+","+Math.round(vertices[b+1]*10000)+","+Math.round(vertices[b+2]*10000);
        return aa.compareTo(bb)<0?aa+"/"+bb:bb+"/"+aa;
    }

}
