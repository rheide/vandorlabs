package com.vandorlabs.ui;

import com.vandorlabs.render.SpaceDoorMotion;
import java.util.Arrays;

public final class SpaceDoorDialogTest {
    private static void need(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){int cases=0;SpaceDoorMotion[] motions=SpaceDoorMotion.values();
        for(int design=0;design<30;design++){boolean glass=design==15||design==16||design==29;need(SpaceDoorDialog.glassPage(design)==glass,"editor subset unchanged");int[] pages=SpaceDoorDialog.pages(design);need(Arrays.equals(pages,glass?new int[]{0,1,4,3,2}:new int[]{0,1,3,2}),"historical tab order/indices");pages[0]=99;need(SpaceDoorDialog.pages(design)[0]==0,"tab arrays owned by caller");}
        for(boolean large:new boolean[]{false,true})for(SpaceDoorMotion motion:motions)for(int direction:new int[]{-1,1}){
            int next=Math.floorMod(motion.ordinal()+direction,motions.length);if(!large&&next==4)next=Math.floorMod(next+direction,motions.length);need(SpaceDoorDialog.nextMotion(motion,large,direction)==motions[next],"reverse and forward X exclusion only ordinary");need(SpaceDoorDialog.hingesEnabled(motion)==!motion.sliding,"hinges disabled while sliding without changing saved preference");cases++;
        }
        int[] order={0,2,3,4,1};for(int i=0;i<order.length;i++)for(int direction:new int[]{-1,1}){need(SpaceDoorDialog.nextSecurity(order[i],direction)==order[Math.floorMod(i+direction,5)],"security order including reverse");cases++;}
        for(int depth=0;depth<3;depth++)for(int panel=0;panel<3;panel++)for(int direction:new int[]{-1,1}){need(SpaceDoorDialog.nextPanel(panel,depth,direction)==Math.floorMod(panel+direction,depth==0?3:2),"position-dependent panel bounds including dormantBoth");need(SpaceDoorDialog.panelAfterPosition(panel,depth)==(depth!=0&&panel==2?1:panel),"moving away fromMiddle normalizesBoth only");cases++;}
        need(SpaceDoorDialog.MOTION==10&&SpaceDoorDialog.POSITION==13&&SpaceDoorDialog.SECURITY==19&&SpaceDoorDialog.ORIGINAL_GLASS==22&&SpaceDoorDialog.TAB_BASE==190,"stable operation IDs");System.out.println("SpaceDoorDialog PASS cases="+cases+" current tabs reverse motion/security panel bounds and dormant hinges");
    }
}
