package com.vandorlabs.ui;

public final class WallAppearanceDialogTest {
    private static void require(boolean ok){if(!ok)throw new AssertionError("wall dialog");}
    public static void main(String[] args){
        String[] shapes={"Hexagon","Octagon","Square","Round"};
        for(int i=0;i<4;i++){require(WallAppearanceDialog.shapeLabel(i).equals("Shape: "+shapes[i]));for(int d:new int[]{-1,1})require(WallAppearanceDialog.nextShape(i,d)==(i+d+4)%4);}
        String[] joins={"Auto","Straight","Front left corner","Front right corner","Back left corner","Back right corner","Front left T","Front right T","Back left T","Back right T","Left cross","Right cross"};
        for(int i=0;i<12;i++){require(WallAppearanceDialog.joinLabel(i).equals("Join: "+joins[i]));for(int d:new int[]{-1,1})require(WallAppearanceDialog.nextJoin(i,d)==(i+d+12)%12);}
        for(int i=0;i<3;i++)for(int d:new int[]{-1,1})require(WallAppearanceDialog.nextSize(i,d)==(i+d+3)%3);
        require(WallAppearanceDialog.sizeLabel(true,false).equals("Size: Wide / half height"));require(WallAppearanceDialog.sizeLabel(false,true).equals("Size: Full width / height"));require(WallAppearanceDialog.sizeLabel(false,false).equals("Size: Tall / half width"));
        require(WallAppearanceDialog.portholeJoinLabel(true).equals("Join: On")&&WallAppearanceDialog.portholeJoinLabel(false).equals("Join: Off"));
        System.out.println("WallAppearanceDialog PASS exact labels and left/right cycles for4shapes/12joins/3sizes");
    }
}
