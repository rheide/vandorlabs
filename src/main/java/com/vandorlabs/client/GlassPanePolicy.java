package com.vandorlabs.client;

/** Client performance choices. These never change saved glass settings. */
public final class GlassPanePolicy {
    private static final boolean LEGACY=Boolean.getBoolean("vandorlabs.benchmarkLegacyGlazing");
    private static final boolean STATIC=!LEGACY&&enabled(System.getProperty("vandorlabs.staticGlass"),true);
    private static final boolean SINGLE=!LEGACY&&enabled(System.getProperty("vandorlabs.staticGlassSingleSurface",
            System.getProperty("vandorlabs.singleSurfaceGlass")),true);
    private static final boolean SMOOTH=!LEGACY&&smooth(System.getProperty("vandorlabs.staticGlassTexture"));
    private static final boolean FRAMES=!LEGACY&&frames(System.getProperty("vandorlabs.glassPaneShadows"));

    public static boolean staticEnabled(){return STATIC;}
    public static boolean singleSurface(){return SINGLE;}
    public static boolean smoothTextures(){return SMOOTH;}
    public static boolean framesOnlyShadows(){return FRAMES;}
    public static boolean omitPane(boolean opaque,boolean shadow){return omitPane(opaque,shadow,FRAMES);}
    static boolean omitPane(boolean opaque,boolean shadow,boolean frames){return frames&&shadow&&!opaque;}
    static boolean enabled(String value,boolean fallback){return value==null?fallback:Boolean.parseBoolean(value);}
    static boolean smooth(String value){return value==null||"smooth".equalsIgnoreCase(value);}
    // Unknown values retain the complete shadow material rather than silently
    // opting out of a user-selected effect because of a misspelled option.
    static boolean frames(String value){return value==null||"frames".equalsIgnoreCase(value);}
    public static String diagnostics(){return "static="+STATIC+" surface="+(SINGLE?"single":"double")
            +" texture="+(SMOOTH?"smooth-stock":"authored")+" shadows="+(FRAMES?"frames":"full");}
    private GlassPanePolicy(){}
}
