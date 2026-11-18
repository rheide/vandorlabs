package com.vandorlabs.navigation;

/** Live, bounded metadata for a navigation table. Circles and points use world X/Z. */
public final class NavigationOverlay {
    public static final int MAX_CIRCLES=64,MAX_PLAYERS=64,MAX_BYTES=3+24*MAX_CIRCLES+8*MAX_PLAYERS;
    public static final NavigationOverlay EMPTY=new NavigationOverlay(new int[0],new int[0]);
    private static final int ARRAY_OUTER=0xe0cb4451, ARRAY_INNER=0xfffc7884, ARRAY_SWEEP=0xffffaab1;
    private static final int DISH_OUTER=0xe0299cac, DISH_INNER=0xff70ecf1, DISH_SWEEP=0xffbdfcff;
    public final int[] circles,players,radars;

    /** The terrain radius sign carries the observer type without widening the overlay packet. */
    public static int encodeRadius(int radius,boolean sentinel){return sentinel?-radius:radius;}

    public NavigationOverlay(int[] circles,int[] players){this(circles,players,new int[0]);}
    public NavigationOverlay(int[] circles,int[] players,int[] radars){
        if(circles.length%3!=0||circles.length>3*MAX_CIRCLES||players.length%2!=0||players.length>2*MAX_PLAYERS||radars.length%3!=0||radars.length>3*MAX_CIRCLES)
            throw new IllegalArgumentException("Navigation overlay bounds");
        for(int i=0;i<circles.length;i+=3){
            position(circles[i],circles[i+1]);
            if(circles[i+2]<-10000||circles[i+2]==0||circles[i+2]>10000)throw new IllegalArgumentException("Navigation circle radius");
        }
        for(int i=0;i<players.length;i+=2)position(players[i],players[i+1]);
        for(int i=0;i<radars.length;i+=3){
            position(radars[i],radars[i+1]);
            int radius=radars[i+2];
            if(radius!=NavigationRange.SENTINEL_RADAR_RADIUS&&radius!=NavigationRange.SURVEY_RADAR_RADIUS)
                throw new IllegalArgumentException("Navigation radar radius");
        }
        this.circles=circles;this.players=players;this.radars=radars;
    }
    private static void position(int x,int z){
        if(NavigationMap.bound(x)!=x||NavigationMap.bound(z)!=z)throw new IllegalArgumentException("Navigation overlay position");
    }
    public static String zoomLabel(int zoom){return "Zoom "+(NavigationMap.SPANS.length-zoom)+"/"+NavigationMap.SPANS.length;}
    public static boolean visible(NavigationCanvas c,double x,double z,double scale,double px,double pz,double radius){
        return Math.abs(px-x)<=c.width*scale/2+radius&&Math.abs(pz-z)<=(c.mapBottom-c.mapTop)*scale/2+radius;
    }
    public interface Segment {void draw(double x,double y,double endX,double endY,int color);}
    private static final int STEPS=128;
    private static final double[] COS=new double[STEPS+1],SIN=new double[STEPS+1];
    static {for(int i=0;i<=STEPS;i++){COS[i]=Math.cos(i*2*Math.PI/STEPS);SIN[i]=Math.sin(i*2*Math.PI/STEPS);}}
    public void drawCircles(NavigationCanvas c,double x,double z,double scale,Segment segment){
        double middle=(c.mapTop+c.mapBottom)/2.0;
        for(int i=0;i<circles.length;i+=3){
            int radius=circles[i+2];
            circle(c,c.width/2.0+(circles[i]-x)/scale,middle+(circles[i+1]-z)/scale,
                    Math.abs(radius)/scale,radius<0?ARRAY_OUTER:DISH_OUTER,segment);
        }
    }
    public void drawRadar(NavigationCanvas c,double x,double z,double scale,double ticks,Segment ring,Segment sweep){
        if(radars.length==0)return;
        double mid=(c.mapTop+c.mapBottom)/2.0;
        double surveyAngle=ticks*2*Math.PI/NavigationSurface.period(NavigationRange.SURVEY_RADAR_RADIUS);
        double sentinelAngle=ticks*2*Math.PI/NavigationSurface.period(NavigationRange.SENTINEL_RADAR_RADIUS);
        double surveyX=Math.sin(surveyAngle),surveyY=-Math.cos(surveyAngle);
        double sentinelX=Math.sin(sentinelAngle),sentinelY=-Math.cos(sentinelAngle);
        for(int i=0;i<radars.length;i+=3){
            int radarRadius=radars[i+2];
            double radius=radarRadius/scale;
            boolean sentinel=radarRadius==NavigationRange.SENTINEL_RADAR_RADIUS;
            double dx=radius*(sentinel?sentinelX:surveyX),dy=radius*(sentinel?sentinelY:surveyY);
            double cx=c.width/2.0+(radars[i]-x)/scale,cy=mid+(radars[i+1]-z)/scale;
            if(cx+radius<0||cx-radius>c.width||cy+radius<c.mapTop||cy-radius>c.mapBottom)continue;
            circle(c,cx,cy,radius,sentinel?ARRAY_INNER:DISH_INNER,ring);
            clip(c,cx,cy,cx+dx,cy+dy,sentinel?ARRAY_SWEEP:DISH_SWEEP,sweep);
        }
    }
    private static void circle(NavigationCanvas c,double cx,double cy,double radius,int color,Segment segment){
        if(cx+radius<0||cx-radius>c.width||cy+radius<c.mapTop||cy-radius>c.mapBottom)return;
        double farX=Math.max(Math.abs(cx),Math.abs(cx-c.width)),farY=Math.max(Math.abs(cy-c.mapTop),Math.abs(cy-c.mapBottom));
        if(farX*farX+farY*farY<radius*radius)return;
        for(int i=0;i<STEPS;i++)clip(c,cx+radius*COS[i],cy+radius*SIN[i],cx+radius*COS[i+1],cy+radius*SIN[i+1],color,segment);
    }
    private static void clip(NavigationCanvas c,double x,double y,double endX,double endY,int color,Segment segment){
        double dx=endX-x,dy=endY-y;
        if(dx==0&&(x<0||x>c.width)||dy==0&&(y<c.mapTop||y>c.mapBottom))return;
        double l=dx==0?0:(-x)/dx,r=dx==0?1:(c.width-x)/dx;
        double t=dy==0?0:(c.mapTop-y)/dy,b=dy==0?1:(c.mapBottom-y)/dy;
        double start=Math.max(0,Math.max(Math.min(l,r),Math.min(t,b))),end=Math.min(1,Math.min(Math.max(l,r),Math.max(t,b)));
        if(start<end)segment.draw(x+dx*start,y+dy*start,x+dx*end,y+dy*end,color);
    }
}
