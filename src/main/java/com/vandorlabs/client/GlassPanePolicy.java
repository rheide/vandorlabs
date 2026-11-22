package com.vandorlabs.client;

/** Startup choices for native stationary panes; no saved world settings change. */
public final class GlassPanePolicy {
    private static final boolean LEGACY=Boolean.getBoolean("vandorlabs.benchmarkLegacyGlazing");
    private static final boolean SINGLE=!LEGACY&&enabled(System.getProperty("vandorlabs.staticGlassSingleSurface",
            System.getProperty("vandorlabs.singleSurfaceGlass")),true);
    private static final boolean SMOOTH=!LEGACY&&"smooth".equalsIgnoreCase(System.getProperty("vandorlabs.staticGlassTexture"));
    /** Omits shadow draws of panes that are not baked into chunks; baked panes always cast shadows. */
    private static final boolean FRAMES=!LEGACY&&"frames".equalsIgnoreCase(System.getProperty("vandorlabs.glassPaneShadows"));
    /** Isolated pipeline fixtures only: exercise the retained fallback without native chunks. Never a user setting. */
    static volatile boolean testRetainedGroupsOnly;
    /** Stationary pane preparation is always active. */
    public static boolean staticEnabled(){return true;}
    /** Native chunk baking is always on; no startup property disables it. */
    public static boolean terrainEnabled(){return !testRetainedGroupsOnly;}
    /** A failed native handoff keeps retained geometry, rather than restoring per-cell draws. */
    public static boolean retainedEnabled(){return true;}
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
        return "renderer="+(terrainEnabled()?"terrain":"groups")+" fallback=retained-groups texture="+(SMOOTH?"smooth":"authored")
                +" surface="+(SINGLE?"single":"double")+" shadows="+(FRAMES?"frames":"full");
    }
    private GlassPanePolicy(){}
}
