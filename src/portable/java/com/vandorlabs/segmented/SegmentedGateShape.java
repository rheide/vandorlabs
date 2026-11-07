package com.vandorlabs.segmented;

import java.util.*;

/** Immutable authored cuboids and short vertical post connectors, in one cell's local units. */
public final class SegmentedGateShape {
    private static final double EPS=1e-7;
    private SegmentedGateShape(){}
    public static final class Box {
        public final double x0,y0,z0,x1,y1,z1;
        public Box(double x0,double y0,double z0,double x1,double y1,double z1){
            if(!(finite(x0)&&finite(y0)&&finite(z0)&&finite(x1)&&finite(y1)&&finite(z1))||x0>x1||y0>y1||z0>z1)throw new IllegalArgumentException("Invalid gate box");
            this.x0=x0;this.y0=y0;this.z0=z0;this.x1=x1;this.y1=y1;this.z1=z1;
        }
        public boolean contains(double x,double y,double z){return x>=x0-EPS&&x<=x1+EPS&&y>=y0-EPS&&y<=y1+EPS&&z>=z0-EPS&&z<=z1+EPS;}
        private boolean overlapsSegment(double ax,double ay,double az,double bx,double by,double bz){return Math.max(ax,bx)>=x0-EPS&&Math.min(ax,bx)<=x1+EPS&&Math.max(ay,by)>=y0-EPS&&Math.min(ay,by)<=y1+EPS&&Math.max(az,bz)>=z0-EPS&&Math.min(az,bz)<=z1+EPS;}
    }
    private static boolean finite(double value){return !Double.isNaN(value)&&!Double.isInfinite(value);}
    public static final class Element {
        private final double[] lo,hi,origin;
        private final int axis,faces;
        private final double c,s,scale;
        public final Box bounds;
        /** Coordinates/origin are the unmodified Minecraft JSON pixel values. */
        public Element(float[] from,float[] to,int axis,float degrees,float[] origin,boolean rescale,int faces){
            if(from==null||to==null||from.length!=3||to.length!=3||axis< -1||axis>2||faces<=0||(faces&~63)!=0||!(degrees==0||Math.abs(degrees)==22.5F||Math.abs(degrees)==45F)||axis<0&&degrees!=0)throw new IllegalArgumentException("Invalid gate element");
            this.lo=new double[3];this.hi=new double[3];this.origin=new double[3];this.axis=degrees==0?-1:axis;this.faces=faces;
            if(axis>=0&&(origin==null||origin.length!=3))throw new IllegalArgumentException("Missing gate element origin");
            for(int a=0;a<3;a++){if(!(finite(from[a])&&finite(to[a]))||from[a]< -16||to[a]>32||from[a]>=to[a])throw new IllegalArgumentException("Invalid gate element extent");lo[a]=from[a]/16F;hi[a]=to[a]/16F;if(axis>=0){if(!finite(origin[a]))throw new IllegalArgumentException("Invalid gate origin");this.origin[a]=origin[a]/16F;}}
            c=Math.cos(Math.toRadians(degrees));s=Math.sin(Math.toRadians(degrees));scale=rescale&&this.axis>=0?1/Math.abs(c):1;
            double[] p=new double[3],min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(int corner=0;corner<8;corner++){point((corner&4)==0?lo[0]:hi[0],(corner&2)==0?lo[1]:hi[1],(corner&1)==0?lo[2]:hi[2],p,0,false);for(int a=0;a<3;a++){min[a]=Math.min(min[a],p[a]);max[a]=Math.max(max[a],p[a]);}}
            bounds=new Box(min[0],min[1],min[2],max[0],max[1],max[2]);
        }
        private void point(double x,double y,double z,double[] out,int at,boolean inverse){
            if(axis<0){out[at]=x;out[at+1]=y;out[at+2]=z;return;}x-=origin[0];y-=origin[1];z-=origin[2];double sine=inverse?-s:s;
            if(inverse){if(axis!=0)x/=scale;if(axis!=1)y/=scale;if(axis!=2)z/=scale;}
            double a=x,b=y,d=z;if(axis==0){b=c*y-sine*z;d=sine*y+c*z;}else if(axis==1){a=c*x+sine*z;d=-sine*x+c*z;}else{a=c*x-sine*y;b=sine*x+c*y;}
            if(!inverse){if(axis!=0)a*=scale;if(axis!=1)b*=scale;if(axis!=2)d*=scale;}out[at]=a+origin[0];out[at+1]=b+origin[1];out[at+2]=d+origin[2];
        }
        public int faceMask(){return faces;}
        public void corner(int corner,double[] out,int at){if(corner<0||corner>7||out==null||at<0||out.length-at<3)throw new IllegalArgumentException("Gate corner output");point((corner&4)==0?lo[0]:hi[0],(corner&2)==0?lo[1]:hi[1],(corner&1)==0?lo[2]:hi[2],out,at,false);}
        public void normal(int face,double[] out,int at){if(face<0||face>5||out==null||at<0||out.length-at<3)throw new IllegalArgumentException("Gate normal output");int direction=face<2?1:face<4?2:0;double sign=(face&1)==0?-1:1,x=direction==0?sign:0,y=direction==1?sign:0,z=direction==2?sign:0,a=x,b=y,d=z;if(axis==0){b=c*y-s*z;d=s*y+c*z;}else if(axis==1){a=c*x+s*z;d=-s*x+c*z;}else if(axis==2){a=c*x-s*y;b=s*x+c*y;}if(axis>=0){if(axis!=0)a/=scale;if(axis!=1)b/=scale;if(axis!=2)d/=scale;}double length=Math.sqrt(a*a+b*b+d*d);out[at]=a/length;out[at+1]=b/length;out[at+2]=d/length;}
        private boolean contains(double x,double y,double z,double[] scratch){point(x,y,z,scratch,0,true);for(int a=0;a<3;a++)if(scratch[a]<lo[a]-EPS||scratch[a]>hi[a]+EPS)return false;return true;}
        private void ray(double ax,double ay,double az,double bx,double by,double bz,int index,Hit hit){
            if(!bounds.overlapsSegment(ax,ay,az,bx,by,bz))return;point(ax,ay,az,hit.scratch,0,true);point(bx,by,bz,hit.scratch,3,true);
            for(int face=0;face<6;face++){if((faces&(1<<face))==0)continue;int axis=face<2?1:face<4?2:0;double delta=hit.scratch[3+axis]-hit.scratch[axis];if(Math.abs(delta)<1e-12)continue;double t=(((face&1)==0?lo:hi)[axis]-hit.scratch[axis])/delta;if(t< -EPS||t>1+EPS||t>=hit.t)continue;boolean inside=true;for(int a=0;a<3;a++)if(a!=axis){double p=hit.scratch[a]+t*(hit.scratch[3+a]-hit.scratch[a]);inside&=p>=lo[a]-EPS&&p<=hi[a]+EPS;}if(inside){hit.t=Math.max(0,Math.min(1,t));hit.element=index;hit.face=face;}}
        }
    }
    public static final class Hit {
        private final double[] scratch=new double[6];
        public double t,x,y,z,nx,ny,nz;public int element=-1,face=-1;
    }
    private static final class Cap {
        final int element;final double x0,x1,z0,z1,y;
        Cap(int element,double x0,double x1,double z0,double z1,double y){this.element=element;this.x0=x0;this.x1=x1;this.z0=z0;this.z1=z1;this.y=y;}
    }
    public static final class Model {
        public final int kind;
        public final Box bounds;
        private final Element[] elements;
        private final List<Cap> bottom,top;
        public Model(int kind,Element[] elements){
            if(kind<0||kind>2||elements==null||elements.length<1||elements.length>128)throw new IllegalArgumentException("Invalid authored gate model");this.kind=kind;this.elements=elements.clone();double x0=Double.POSITIVE_INFINITY,y0=x0,z0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1,z1=x1;
            for(Element e:this.elements){if(e==null)throw new IllegalArgumentException("Missing gate element");x0=Math.min(x0,e.bounds.x0);y0=Math.min(y0,e.bounds.y0);z0=Math.min(z0,e.bounds.z0);x1=Math.max(x1,e.bounds.x1);y1=Math.max(y1,e.bounds.y1);z1=Math.max(z1,e.bounds.z1);}bounds=new Box(x0,y0,z0,x1,y1,z1);bottom=caps(false);top=caps(true);
        }
        public int count(){return elements.length;}public Element element(int index){return elements[index];}
        public boolean contains(double x,double y,double z,Hit scratch){if(!bounds.contains(x,y,z))return false;for(Element e:elements)if(e.contains(x,y,z,scratch.scratch))return true;return false;}
        public boolean ray(double ax,double ay,double az,double bx,double by,double bz,Hit hit){hit.t=Double.POSITIVE_INFINITY;hit.element=hit.face=-1;if(!(finite(ax)&&finite(ay)&&finite(az)&&finite(bx)&&finite(by)&&finite(bz))||!bounds.overlapsSegment(ax,ay,az,bx,by,bz))return false;for(int i=0;i<elements.length;i++)elements[i].ray(ax,ay,az,bx,by,bz,i,hit);if(hit.element<0)return false;hit.x=ax+(bx-ax)*hit.t;hit.y=ay+(by-ay)*hit.t;hit.z=az+(bz-az)*hit.t;elements[hit.element].normal(hit.face,hit.scratch,0);hit.nx=hit.scratch[0];hit.ny=hit.scratch[1];hit.nz=hit.scratch[2];return true;}
        /** Includes already prepared vertical extensions without allocating or rebuilding authored elements. */
        public boolean ray(double ax,double ay,double az,double bx,double by,double bz,List<Connector> below,List<Connector> above,Hit hit){
            if(below==null||above==null||below.size()>256||above.size()>256)throw new IllegalArgumentException("Gate ray connector capacity");
            ray(ax,ay,az,bx,by,bz,hit);
            if(!(finite(ax)&&finite(ay)&&finite(az)&&finite(bx)&&finite(by)&&finite(bz)))return false;
            connectorRays(ax,ay,az,bx,by,bz,below,hit);connectorRays(ax,ay,az,bx,by,bz,above,hit);
            if(hit.element<0)return false;hit.x=ax+(bx-ax)*hit.t;hit.y=ay+(by-ay)*hit.t;hit.z=az+(bz-az)*hit.t;
            elements[hit.element].normal(hit.face,hit.scratch,0);hit.nx=hit.scratch[0];hit.ny=hit.scratch[1];hit.nz=hit.scratch[2];return true;
        }
        private void connectorRays(double ax,double ay,double az,double bx,double by,double bz,List<Connector> connectors,Hit hit){
            for(int index=0;index<connectors.size();index++){
                Connector connector=connectors.get(index);Box box=connector.bounds;Element element=elements[connector.element];
                if(!box.overlapsSegment(ax,ay,az,bx,by,bz))continue;
                for(int face=0;face<6;face++){
                    if((element.faces&(1<<face))==0)continue;int axis=face<2?1:face<4?2:0;
                    double a=axis==0?ax:axis==1?ay:az,b=axis==0?bx:axis==1?by:bz,delta=b-a;
                    if(Math.abs(delta)<1e-12)continue;
                    double edge=axis==0?((face&1)==0?box.x0:box.x1):axis==1?((face&1)==0?box.y0:box.y1):((face&1)==0?box.z0:box.z1),t=(edge-a)/delta;
                    if(t< -EPS||t>1+EPS||t>=hit.t)continue;
                    double x=ax+t*(bx-ax),y=ay+t*(by-ay),z=az+t*(bz-az);
                    if(box.contains(x,y,z)){hit.t=Math.max(0,Math.min(1,t));hit.element=connector.element;hit.face=face;}
                }
            }
        }
        private List<Cap> caps(boolean upper){
            List<Cap> result=new ArrayList<>();for(Element seed:elements){if(seed.axis>=0||seed.hi[1]-seed.lo[1]<.25-EPS||seed.hi[0]-seed.lo[0]>.125+EPS||seed.hi[2]-seed.lo[2]>.125+EPS)continue;
                double end=upper?seed.hi[1]:seed.lo[1];for(Element e:elements)if(e.axis<0&&overlap(seed.lo[0],seed.hi[0],e.lo[0],e.hi[0])&&overlap(seed.lo[2],seed.hi[2],e.lo[2],e.hi[2]))end=upper?Math.max(end,e.hi[1]):Math.min(end,e.lo[1]);
                if(upper?end<.75-EPS||end>1+EPS:end>.25+EPS||end< -EPS)continue;
                for(int index=0;index<elements.length;index++){Element e=elements[index];if(e.axis>=0||Math.abs((upper?e.hi[1]:e.lo[1])-end)>EPS)continue;double x0=Math.max(seed.lo[0],e.lo[0]),x1=Math.min(seed.hi[0],e.hi[0]),z0=Math.max(seed.lo[2],e.lo[2]),z1=Math.min(seed.hi[2],e.hi[2]);if(x1-x0>EPS&&z1-z0>EPS){boolean duplicate=false;for(Cap old:result)if(Math.abs(old.x0-x0)<EPS&&Math.abs(old.x1-x1)<EPS&&Math.abs(old.z0-z0)<EPS&&Math.abs(old.z1-z1)<EPS&&Math.abs(old.y-end)<EPS){duplicate=true;break;}if(!duplicate){if(result.size()>=256)throw new IllegalArgumentException("Gate post profile capacity");result.add(new Cap(index,x0,x1,z0,z1,end));}}}
            }return Collections.unmodifiableList(result);
        }
    }
    private static boolean overlap(double a0,double a1,double b0,double b1){return Math.min(a1,b1)-Math.max(a0,b0)>EPS;}
    public static final class Connector {
        public final int element;public final Box bounds;
        private Connector(int element,Box bounds){this.element=element;this.bounds=bounds;}
    }
    /** Only call for a prepared compatible ABOVE/BELOW bit. No original geometry is removed/scaled. */
    public static List<Connector> connectors(Model model,Model neighbour,boolean above,int mask){
        if((mask&~15)!=0)throw new IllegalArgumentException("Invalid gate join mask");if(model==null||neighbour==null||model.kind!=neighbour.kind||(mask&(above?SegmentedGateJoin.ABOVE:SegmentedGateJoin.BELOW))==0)return Collections.emptyList();List<Connector> result=new ArrayList<>();
        for(Cap a:above?model.top:model.bottom)for(Cap b:above?neighbour.bottom:neighbour.top){double x0=Math.max(0,Math.max(a.x0,b.x0)),x1=Math.min(1,Math.min(a.x1,b.x1)),z0=Math.max(7D/16,Math.max(a.z0,b.z0)),z1=Math.min(9D/16,Math.min(a.z1,b.z1));if(x1-x0<=EPS||z1-z0<=EPS)continue;double y0=above?Math.max(0,a.y):0,y1=above?1:Math.min(1,a.y);if(y1-y0<=EPS)continue;Box box=new Box(x0,y0,z0,x1,y1,z1);boolean duplicate=false;for(Connector old:result){Box q=old.bounds;if(Math.abs(q.x0-x0)<EPS&&Math.abs(q.x1-x1)<EPS&&Math.abs(q.y0-y0)<EPS&&Math.abs(q.y1-y1)<EPS&&Math.abs(q.z0-z0)<EPS&&Math.abs(q.z1-z1)<EPS){duplicate=true;break;}}if(!duplicate){if(result.size()>=256)throw new IllegalArgumentException("Gate connector capacity");result.add(new Connector(a.element,box));}}
        return Collections.unmodifiableList(result);
    }
}
