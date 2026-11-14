package com.vandorlabs.client;

/** Startup choices for native stationary panes; no saved world settings change. */
public final class GlassPanePolicy {
    private static final boolean LEGACY=Boolean.getBoolean("vandorlabs.benchmarkLegacyGlazing");
    private static final boolean STATIC=!LEGACY&&enabled(System.getProperty("vandorlabs.staticGlass"),true);
    private static final boolean SINGLE=!LEGACY&&enabled(System.getProperty("vandorlabs.staticGlassSingleSurface",
            System.getProperty("vandorlabs.singleSurfaceGlass")),true);
    private static final boolean SMOOTH=!LEGACY&&"smooth".equalsIgnoreCase(System.getProperty("vandorlabs.staticGlassTexture"));
    private static final boolean FRAMES=!LEGACY&&"frames".equalsIgnoreCase(System.getProperty("vandorlabs.glassPaneShadows"));
    private static final String RENDERER=System.getProperty("vandorlabs.staticGlassRenderer","terrain");
    public static boolean staticEnabled(){return STATIC;}
    public static boolean terrainEnabled(){return STATIC&&!FRAMES&&"terrain".equalsIgnoreCase(RENDERER);}
    /** A failed native handoff keeps retained geometry, rather than restoring per-cell draws. */
    public static boolean retainedEnabled(){return STATIC&&("terrain".equalsIgnoreCase(RENDERER)||"groups".equalsIgnoreCase(RENDERER));}
    public static boolean singleSurface(){return SINGLE;}
    public static boolean smoothTextures(){return SMOOTH;}
    public static boolean framesOnlyShadows(){return FRAMES;}
    public static boolean omitPane(boolean opaque,boolean shadow){return FRAMES&&shadow&&!opaque;}
    static boolean enabled(String value,boolean fallback){return value==null?fallback:Boolean.parseBoolean(value);}
    static boolean terrainAlphaSafe(int minimumPositiveAlpha,boolean single){
        int alpha=single?com.vandorlabs.render.GlassPanePixels.singleAlpha(minimumPositiveAlpha):minimumPositiveAlpha;
        // Native GL_GREATER .1 needs a one-byte margin around its quantized reference.
        return alpha==0||alpha>=27;
    }
    public static String diagnostics(){
        if(!retainedEnabled())return "renderer=legacy texture=authored surface=double shadows="+(FRAMES?"frames":"full");
        return "renderer="+(terrainEnabled()?"terrain":"groups")+" fallback=retained-groups texture="+(SMOOTH?"smooth":"authored")
                +" surface="+(SINGLE?"single":"double")+" shadows="+(FRAMES?"frames":"full");
    }
    private GlassPanePolicy(){}
}
