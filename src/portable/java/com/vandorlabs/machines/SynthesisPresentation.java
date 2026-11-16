package com.vandorlabs.machines;

import com.vandorlabs.rack.ModuleRackGeometry;

/** Authored tower-local presentation and actual native four-bay rack hit mapping. Front faces NORTH. */
public final class SynthesisPresentation {
    public static final double RACK_X=0;
    public static final double RACK_Y=0;
    public static final double RACK_Z=.375;
    public static final double CHIP_X=.5;
    public static final double CHIP_Y=1.5;
    public static final double CHIP_Z=.35;
    public static final double SCREEN_X=2.125;
    public static final double SCREEN_Y=1.5;
    public static final double SCREEN_Z=.35;
    public static final double SCREEN_SIZE=.75;
    public static final double SCREEN_FRAME=.875;
    public static final double KEYBOARD_X=1.625;
    public static final double KEYBOARD_Y=1.000625;
    public static final double KEYBOARD_Z=.0625;
    public static final double KEYBOARD_WIDTH=1;
    public static final double KEYBOARD_DEPTH=.5;
    public static final double RENDER_FRONT=.5;
    public static final double CORE_Y=2.25;
    public static double screenPreviewScale(double width,double height,double depth){return SCREEN_SIZE*.875/Math.sqrt(width*width+height*height+depth*depth);}
    public static double bayCenterY(int bay){if(!ModuleRackGeometry.validSlot(bay))throw new IllegalArgumentException("Rack bay");return RACK_Y+ModuleRackGeometry.slotCenterY(bay)/16;}
    public static double bayButtonX(){return RACK_X+1-(ModuleRackGeometry.BUTTON_MIN_X+ModuleRackGeometry.BUTTON_MAX_X)/32;}
    public static double rackFront(){return RACK_Z+1-ModuleRackGeometry.FASCIA_DEPTH/16;}
    /** Same four slot/button regions as a placed NORTH-facing Module Rack. */
    public static int bayAt(double x,double y){return ModuleRackGeometry.hitSlot(2,2,x-RACK_X,y-RACK_Y,0);}
    /** Machine-local mounts share the same rack geometry and corner port convention. */
    public static final class Spec {
        public final int width,depth;
        public final double rackX,rackY,rackZ,chipX,chipY,chipZ,screenX,screenY,screenZ,screenSize,screenFrame,keyboardX,keyboardY,keyboardZ,keyboardWidth,keyboardDepth;
        private Spec(int width,boolean tower){this.width=width;depth=3;rackX=0;rackY=0;rackZ=tower?RACK_Z:0;chipX=tower?CHIP_X:.5;chipY=1.5;chipZ=tower?CHIP_Z:.35;screenX=tower?SCREEN_X:width-.375;screenY=1.5;screenZ=tower?SCREEN_Z:-.025;screenSize=tower?SCREEN_SIZE:.5;screenFrame=tower?SCREEN_FRAME:.625;keyboardWidth=tower?KEYBOARD_WIDTH:.625;keyboardDepth=tower?.5:.3125;keyboardX=tower?KEYBOARD_X:width-.6875;keyboardY=KEYBOARD_Y;keyboardZ=tower?KEYBOARD_Z:-.25;}
        public int bayAt(double x,double y){return ModuleRackGeometry.hitSlot(2,2,x-rackX,y-rackY,0);}
        public double rackFront(){return rackZ+1-ModuleRackGeometry.FASCIA_DEPTH/16;}
        public double previewScale(double width,double height,double depth){return screenSize*.875/Math.sqrt(width*width+height*height+depth*depth);}
    }
    private static final Spec TOWER=new Spec(3,true),PRESS=new Spec(4,false),GANTRY=new Spec(5,false);
    public static Spec forMachine(String machine){if("synthesis_tower".equals(machine))return TOWER;if("assembly_press".equals(machine))return PRESS;if("enclosed_gantry".equals(machine))return GANTRY;throw new IllegalArgumentException("Not a builder: "+machine);}
    private SynthesisPresentation(){}
}
