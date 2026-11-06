package com.vandorlabs.ui;

/** Released trapdoor option order and wording, shared by both dialog adapters. */
public final class TrapdoorOptions {
    private TrapdoorOptions() { }
    public static int motion(boolean diagonal,boolean sliding,boolean cover,boolean intoWall,boolean overSurface) {
        return diagonal ? (sliding ? intoWall ? 2 : 1 : 0)
                : cover ? (sliding ? 4 : 3) : sliding ? (overSurface ? 2 : 1) : 0;
    }
    public static String motionLabel(boolean diagonal,boolean sliding,boolean cover,boolean intoWall,boolean overSurface) {
        if(diagonal)return sliding ? intoWall ? "Slide into wall" : "Slide over wall" : "Rotating";
        if(cover)return sliding ? "Slide into next block" : "Rotate into next block";
        return sliding ? overSurface ? "Slide over surface" : "Sliding" : "Rotating";
    }
    public static String positionLabel(boolean diagonal,int position) {
        return diagonal ? "Width: "+(position==0 ? "Half" : "Full")
                : "Pos: "+(position==0 ? "Bottom" : position==1 ? "Middle" : "Top");
    }
    public static String heightLabel(int position){return "Height: "+(position==2 ? "Half" : "Full");}
    public static String hingeLabel(int side){return "Hinge: "+(side==0 ? "Auto" : side==1 ? "Left" : "Right");}
    public static String rotationLabel(boolean flipped){return "Rotation: "+(flipped ? "Reverse" : "Normal");}
    public static final class Motion {
        public final boolean sliding,cover,intoWall,overSurface;
        private Motion(boolean sliding,boolean cover,boolean intoWall,boolean overSurface) {
            this.sliding=sliding;this.cover=cover;this.intoWall=intoWall;this.overSurface=overSurface;
        }
    }
    public static Motion cycleMotion(boolean diagonal,boolean offsetEligible,boolean sliding,boolean cover,
                                      boolean intoWall,boolean overSurface,int direction) {
        int mode=DialogInput.cycle(motion(diagonal,sliding,cover,intoWall,overSurface),diagonal || !offsetEligible ? 3 : 5,direction);
        // Preserve the unrelated flags exactly as the released dialog does.
        return diagonal ? new Motion(mode!=0,cover,mode==2,overSurface)
                : new Motion(mode==1 || mode==2 || mode==4,mode>=3,intoWall,mode==2);
    }
}
