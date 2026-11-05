package com.vandorlabs.ui;

import com.vandorlabs.render.SpaceDoorMotion;

/** Current ordinary/Large Door choices. Hosts retain channel drafts, packet sends and picker transactions. */
public final class SpaceDoorDialog {
    public static final int DONE=1,MOTION=10,FRAME=12,POSITION=13,HINGES=15,TRIGGER=16,PANEL=17,
            TEXTURE_LAYOUT=18,SECURITY=19,GLASS_TINT=20,GLASS_LAYOUT=21,ORIGINAL_GLASS=22,TAB_BASE=190;
    private static final String[] PAGE_LABELS={"Door","Frame","Redstone","Security","Glass"},PANELS={"Off","On","Both"},POSITIONS={"Middle","Near","Far"};
    private static final SpaceDoorMotion[] MOTIONS=SpaceDoorMotion.values();
    private static final int[] SECURITY_ORDER={0,2,3,4,1};
    private SpaceDoorDialog(){}
    /** This editor's historical subset is narrower than the renderer's hasGlassDesign policy. */
    public static boolean glassPage(int design){return design==15||design==16||design==29;}
    public static int[] pages(int design){return glassPage(design)?new int[]{0,1,4,3,2}:new int[]{0,1,3,2};}
    public static String pageLabel(int page){return PAGE_LABELS[page];}
    public static String panelLabel(int mode){return "Panel: "+PANELS[mode];}
    public static String positionLabel(int depth){return "Pos: "+POSITIONS[depth];}
    public static boolean hingesEnabled(SpaceDoorMotion motion){return !motion.sliding;}
    public static String hingesLabel(SpaceDoorMotion motion,boolean hinges){return !motion.sliding&&hinges?"Hinges: On":"Hinges: Off";}
    public static SpaceDoorMotion nextMotion(SpaceDoorMotion motion,boolean large,int direction){
        SpaceDoorMotion next=MOTIONS[DialogInput.cycle(motion.ordinal(),MOTIONS.length,direction)];
        if(next==SpaceDoorMotion.X_SPLIT&&!large)next=MOTIONS[DialogInput.cycle(next.ordinal(),MOTIONS.length,direction)];return next;
    }
    public static int nextPanel(int mode,int depth,int direction){return DialogInput.cycle(mode,depth==0?3:2,direction);}
    public static int panelAfterPosition(int mode,int depth){return depth!=0&&mode==2?1:mode;}
    public static int nextSecurity(int mode,int direction){int index=0;while(SECURITY_ORDER[index]!=mode)index++;return SECURITY_ORDER[DialogInput.cycle(index,SECURITY_ORDER.length,direction)];}
}
