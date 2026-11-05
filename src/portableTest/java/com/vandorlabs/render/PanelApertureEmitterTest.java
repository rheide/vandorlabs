package com.vandorlabs.render;
import java.util.*;
public final class PanelApertureEmitterTest {
    private static final class Capture {final List<double[][]> quads=new ArrayList<double[][]>();final List<Boolean> reversed=new ArrayList<Boolean>();}
    private static final Object MATERIAL=new Object(),TRANSFORM=new Object();
    private static final PanelApertureEmitter.Sink<Capture,Object,Object> SINK=new PanelApertureEmitter.Sink<Capture,Object,Object>(){
        public void quad(Capture c,Object m,Object t,boolean reverse,double[] a,double[] b,double[] d,double[] e){need(m==MATERIAL&&t==TRANSFORM,"same material/transform identity");c.quads.add(new double[][]{a,b,d,e});c.reversed.add(reverse);}
    };
    public static void main(String[] ignored){
        Capture c=new Capture();PanelApertureEmitter.panelQuad(SINK,c,MATERIAL,TRANSFORM,0,0,16,0,16,16,0,16);need(c.quads.size()==2&&c.reversed.get(0)&&!c.reversed.get(1),"two opposite authored panel faces");need(c.quads.get(0)[0][2]==6&&c.quads.get(1)[0][2]==10,"four-pixel housing depth");
        for(double[][] quad:c.quads)for(double[] v:quad)need(v[3]==v[0]&&v[4]==16-v[1],"artwork follows authored XY");
        c=new Capture();PanelApertureEmitter.topRim(SINK,c,MATERIAL,TRANSFORM,16,0,16,4);PanelApertureEmitter.sideRim(SINK,c,MATERIAL,TRANSFORM,0,0,16,16);need(c.quads.size()==2&&c.reversed.get(0)&&!c.reversed.get(1),"top/side orientation");PanelApertureEmitter.topRim(SINK,c,MATERIAL,TRANSFORM,0,3,3+1e-8,4);PanelApertureEmitter.sideRim(SINK,c,MATERIAL,TRANSFORM,0,7,7,4);need(c.quads.size()==2,"degenerate rims omitted");
        c=new Capture();PanelApertureEmitter.rimSegment(SINK,c,MATERIAL,TRANSFORM,0,0,24,32,16);need(c.quads.size()==3,"40-pixel diagonal tiles into three atlas-safe rims");double length=0;for(double[][] quad:c.quads){need(quad[1][3]<=16&&quad[1][3]>0&&quad[2][4]==16,"bounded depth/length UV");length+=quad[1][3];}need(length==40,"whole edge length retained");
        System.out.println("PASS: aperture face winding, exact depth, authored artwork axes, degenerate rims and bounded long-edge tiling");
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
