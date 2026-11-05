package com.vandorlabs.ui;

/** Released panel coordinates, custom caps and allocated search-inclusive row counts. */
public final class ProgrammableDialogLayoutTest {
    private ProgrammableDialogLayoutTest() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void check(int sw,int sh,int mw,int mh,int[] expected){
        ProgrammableDialogLayout p=new ProgrammableDialogLayout(sw,sh,mw,mh);
        int[] actual={p.left,p.top,p.width,p.height,p.controlsX,p.listX,p.listWidth,p.doneY,p.rows(false),p.rows(true)};
        require(java.util.Arrays.equals(actual,expected),sw+"x"+sh+" cap"+mw+"x"+mh+": "+java.util.Arrays.toString(actual));
    }
    public static void main(String[] args){
        require(ProgrammableDialogLayout.CONTROLS_WIDTH==154&&ProgrammableDialogLayout.ROW_HEIGHT==22,"Established widget/row constants changed");
        check(1280,720,460,332,new int[]{410,194,460,332,704,422,263,500,13,12});
        check(320,240,460,332,new int[]{4,4,312,232,150,16,115,210,8,7});
        check(460,332,460,332,new int[]{4,4,452,324,290,16,255,302,13,12});
        check(1280,720,340,216,new int[]{470,252,340,216,644,482,143,442,8,7});
        check(321,241,460,332,new int[]{4,4,313,233,151,16,116,211,8,7});
        check(1280,720,700,400,new int[]{290,160,700,400,824,302,503,534,16,15});
        check(180,120,460,332,new int[]{4,4,172,112,10,16,-25,90,3,2});
        check(641,361,460,332,new int[]{90,14,460,332,384,102,263,320,13,12});
        for(int[] size:new int[][]{{180,120},{320,240},{641,361},{1280,720}}){
            ProgrammableDialogLayout a=new ProgrammableDialogLayout(size[0],size[1]),b=new ProgrammableDialogLayout(size[0],size[1],460,332);
            require(a.left==b.left&&a.top==b.top&&a.width==b.width&&a.height==b.height&&a.controlsX==b.controlsX&&a.listX==b.listX&&a.listWidth==b.listWidth&&a.doneY==b.doneY,"Default caps overload differs");
        }
        System.out.println("Programmable dialog panel dimensions PASS default/custom caps, odd/narrow windows, search-inclusive rows");
    }
}
