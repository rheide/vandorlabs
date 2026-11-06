package com.vandorlabs.ui;

/** Every released flag combination, including stale unrelated fields in older saves. */
public final class TrapdoorOptionsTest {
    private static void need(boolean value){if(!value)throw new AssertionError("Trapdoor dialog option parity");}
    public static void main(String[] args) {
        String[] regular={"Rotating","Sliding","Slide over surface","Rotate into next block","Slide into next block"};
        String[] diagonal={"Rotating","Slide over wall","Slide into wall"};
        int cases=0;
        for(boolean diag:new boolean[]{false,true})for(boolean offset:new boolean[]{false,true})for(int flags=0;flags<16;flags++) {
            boolean sliding=(flags&1)!=0,cover=(flags&2)!=0,into=(flags&4)!=0,over=(flags&8)!=0;
            // Frozen encoding from the released dialog, independent of the option adapter.
            int mode=diag?(sliding?(into?2:1):0):(cover?(sliding?4:3):(sliding?(over?2:1):0));
            need(TrapdoorOptions.motion(diag,sliding,cover,into,over)==mode);
            need(TrapdoorOptions.motionLabel(diag,sliding,cover,into,over).equals((diag?diagonal:regular)[mode]));
            for(int direction:new int[]{-1,1}) {
                int count=diag||!offset?3:5,next=Math.floorMod(mode+direction,count);
                TrapdoorOptions.Motion actual=TrapdoorOptions.cycleMotion(diag,offset,sliding,cover,into,over,direction);
                need(TrapdoorOptions.motion(diag,actual.sliding,actual.cover,actual.intoWall,actual.overSurface)==next);
                need(diag ? actual.cover==cover&&actual.overSurface==over : actual.intoWall==into);
                TrapdoorOptions.Motion walk=actual;
                for(int i=1;i<count;i++)walk=TrapdoorOptions.cycleMotion(diag,offset,walk.sliding,walk.cover,walk.intoWall,walk.overSurface,direction);
                need(TrapdoorOptions.motion(diag,walk.sliding,walk.cover,walk.intoWall,walk.overSurface)==Math.floorMod(mode,count));
                cases++;
            }
        }
        need(TrapdoorOptions.positionLabel(false,0).equals("Pos: Bottom"));
        need(TrapdoorOptions.positionLabel(false,1).equals("Pos: Middle"));
        need(TrapdoorOptions.positionLabel(false,2).equals("Pos: Top"));
        need(TrapdoorOptions.positionLabel(true,0).equals("Width: Half"));
        need(TrapdoorOptions.positionLabel(true,2).equals("Width: Full"));
        need(TrapdoorOptions.heightLabel(2).equals("Height: Half"));
        System.out.println("Trapdoor dialog contracts PASS cycles="+cases+" reverse wrap old-flags offset-eligibility labels");
    }
}
