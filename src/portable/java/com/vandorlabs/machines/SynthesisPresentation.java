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
    private SynthesisPresentation(){}
}
