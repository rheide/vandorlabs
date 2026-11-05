package com.vandorlabs.render;
import java.util.*;
public final class PanelBoxEmitterTest {
    private static final class Face {final String material;final double[] points;Face(String material,double[] points){this.material=material;this.points=points;}}
    private static final PanelBoxEmitter.Sink<List<Face>,String> SINK=(faces,material,x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3)->faces.add(new Face(material,new double[]{x0,y0,z0,x1,y1,z1,x2,y2,z2,x3,y3,z3}));
    public static void main(String[] ignored){
        List<Face> faces=new ArrayList<Face>();PanelBoxEmitter.box(SINK,faces,"art","rim",false,0,0,0,4,16,6,false);need(faces.size()==5,"only intentional front face omitted");String[] materials={"rim","rim","art","art","rim"};for(int i=0;i<faces.size();i++)need(materials[i].equals(faces.get(i).material),"X broad faces use artwork, other faces use side finish");for(Face face:faces)for(int i=0;i<4;i++)need(face.points[i*3]>=0&&face.points[i*3]<=4&&face.points[i*3+1]>=0&&face.points[i*3+1]<=16&&face.points[i*3+2]>=0&&face.points[i*3+2]<=6,"authored component bounds");
        faces.clear();PanelBoxEmitter.box(SINK,faces,"art","rim",true,0,0,0,4,16,6,true);need(faces.size()==6&&faces.get(2).material.equals("rim")&&faces.get(4).material.equals("art")&&faces.get(5).material.equals("art"),"Z broad faces and complete box order");
        faces.clear();PanelBoxEmitter.box(SINK,faces,"art","rim",false,0,0,0,4,16,0,false);need(faces.size()==5,"zero-length arm retains original end-face calls for target degeneracy policy");
        System.out.println("PASS: scalar panel box material axes, source order, omitted junction face and degenerate end-cap calls");
    }
    private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
