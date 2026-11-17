package com.vandorlabs.navigation;

/** Proportional integer observer footprints, bounded by the existing Core frame axes. */
public final class ObserverScale {
    public static final String WIDTH_TAG="ObserverWidth";
    private ObserverScale(){}
    public static int baseWidth(String design){return "standalone_dish".equals(design)?3:"standalone_antenna".equals(design)?2:0;}
    public static boolean observer(String design){return baseWidth(design)!=0;}
    public static int maximumWidth(String design){if(!observer(design))throw new IllegalArgumentException("Not an observer");return "standalone_antenna".equals(design)?8:16;}
    public static int width(String design,int savedWidth){int base=baseWidth(design);if(base==0)throw new IllegalArgumentException("Not an observer");int width=savedWidth==0?base:savedWidth;if(width<base||width>maximumWidth(design))throw new IllegalArgumentException("Invalid observer width");return width;}
    public static int height(String design,int width){int valid=width(design,width);return "standalone_antenna".equals(design)?valid*2:valid;}
    public static double linearScale(String design,int width){return width(design,width)/(double)baseWidth(design);}
}
