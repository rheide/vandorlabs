package com.vandorlabs.canopy;

/** Immutable authored or clipped canopy polygon. Package kernels may share its immutable arrays. */
public final class CanopyFace {
    final CanopyPoint[] vertices;
    final double[][] uv;
    public final String material,baseMaterial,group;
    public final boolean doubleSided,glazing,tiled;
    public CanopyFace(CanopyPoint[] vertices,double[][] uv,String material,String baseMaterial,String group,
            boolean doubleSided,boolean glazing,boolean tiled) {
        this(vertices.clone(),copyUv(uv),material,baseMaterial,group,doubleSided,glazing,tiled,true);
    }
    private CanopyFace(CanopyPoint[] vertices,double[][] uv,String material,String baseMaterial,String group,
            boolean doubleSided,boolean glazing,boolean tiled,boolean owned) {
        if(vertices.length<3||vertices.length!=uv.length)throw new IllegalArgumentException("Canopy polygon");
        this.vertices=vertices;this.uv=uv;this.material=java.util.Objects.requireNonNull(material);
        this.baseMaterial=java.util.Objects.requireNonNull(baseMaterial);this.group=java.util.Objects.requireNonNull(group);
        this.doubleSided=doubleSided;this.glazing=glazing;this.tiled=tiled;
        for(int i=0;i<vertices.length;i++)if(vertices[i]==null||uv[i].length!=2)throw new IllegalArgumentException("Canopy vertex/UV");
    }
    private static double[][] copyUv(double[][] uv){double[][] copy=new double[uv.length][];for(int i=0;i<uv.length;i++)copy[i]=uv[i].clone();return copy;}
    public int points(){return vertices.length;}
    public CanopyPoint vertex(int index){return vertices[index];}
    public double u(int index){return uv[index][0];}
    public double v(int index){return uv[index][1];}
    CanopyFace geometryOwned(CanopyPoint[] vertices,double[][] uv){return geometryOwned(vertices,uv,tiled);}
    CanopyFace geometryOwned(CanopyPoint[] vertices,double[][] uv,boolean tiled){return new CanopyFace(vertices,uv,material,baseMaterial,group,doubleSided,glazing,tiled,true);}
    CanopyFace posedOwned(CanopyPoint[] vertices,double[][] uv){return new CanopyFace(vertices,uv,material,baseMaterial,group,doubleSided,glazing||baseMaterial.equals("glass"),tiled,true);}
}
