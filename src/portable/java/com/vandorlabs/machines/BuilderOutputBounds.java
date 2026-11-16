package com.vandorlabs.machines;

/** Finished item presentation and pickup share one machine-local box, facing NORTH. */
public final class BuilderOutputBounds {
    public final double minX,minY,minZ,maxX,maxY,maxZ;
    private BuilderOutputBounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){this.minX=minX;this.minY=minY;this.minZ=minZ;this.maxX=maxX;this.maxY=maxY;this.maxZ=maxZ;}
    private static final BuilderOutputBounds TOWER=new BuilderOutputBounds(1,SynthesisPresentation.CORE_Y,1.125,2,SynthesisPresentation.CORE_Y+1,2.125);
    private static final BuilderOutputBounds PRESS=new BuilderOutputBounds(1.625,1.3125,1,2.625,1.4375,2);
    private static final BuilderOutputBounds GANTRY=new BuilderOutputBounds(1.5,1.125,.5625,3.5,3.125,2.5625);
    public static BuilderOutputBounds forMachine(String machine){if("synthesis_tower".equals(machine))return TOWER;if("assembly_press".equals(machine))return PRESS;if("enclosed_gantry".equals(machine))return GANTRY;throw new IllegalArgumentException("Not a builder: "+machine);}
    public double centerX(){return (minX+maxX)/2;}
    public double centerY(){return (minY+maxY)/2;}
    public double centerZ(){return (minZ+maxZ)/2;}
}
