package com.vandorlabs.ui;

/** Scroll geometry keeps integer thumb placement and nearest-row drag as separate rules. */
public final class DialogInputTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(DialogInput.maxScroll(0,4)==0&&DialogInput.maxScroll(4,4)==0&&DialogInput.maxScroll(5,4)==1,"empty/exact/overflow rows");
        check(DialogInput.thumbHeight(88,4,20)==17,"thumb height truncates to whole pixels");
        check(DialogInput.thumbHeight(88,4,0)==88&&DialogInput.thumbHeight(88,4,4)==88,"non-scrolling list fills track");
        check(DialogInput.thumbHeight(88,4,1000)==8,"minimum thumb height");
        check(DialogInput.thumbY(100,88,17,2,16)==108,"thumb placement uses integer floor, not nearest pixel");
        check(DialogInput.thumbY(100,88,17,16,16)==171&&DialogInput.thumbY(100,88,88,0,0)==100,"last row and no-scroll thumb placement");
        int[] expected={0,1,1,2,3,4,4,5};
        for(int pixel=0;pixel<expected.length;pixel++)check(DialogInput.scrollForDrag(100+pixel+2,100,10,3,5,2)==expected[pixel],"nearest-row drag step "+pixel);
        check(DialogInput.scrollForDrag(105,100,12,2,3,0)==2,"half row rounds up");
        check(DialogInput.scrollForDrag(-100,100,12,2,3,0)==0&&DialogInput.scrollForDrag(999,100,12,2,3,0)==3,"drag clamps outside track");
        check(DialogInput.scrollForDrag(999,100,8,8,3,0)==0&&DialogInput.scrollForDrag(999,100,7,8,3,0)==0&&DialogInput.scrollForDrag(999,100,100,8,0,0)==0,"zero/negative travel and zero maximum");
        for(int delta:new int[]{-120,-1,Integer.MIN_VALUE})check(DialogInput.wheel(4,10,delta)==5,"negative wheel magnitude advances one row");
        for(int delta:new int[]{120,1,Integer.MAX_VALUE})check(DialogInput.wheel(4,10,delta)==3,"positive wheel magnitude retreats one row");
        check(DialogInput.wheel(4,10,0)==4,"zero delta leaves valid scroll unchanged");
        check(DialogInput.wheel(0,10,120)==0&&DialogInput.wheel(10,10,-120)==10,"wheel endpoints");
        int cases=0;
        for(int maximum:new int[]{1,2,7,100})for(int travel:new int[]{1,2,7,99})for(int grab:new int[]{0,2,4}){
            int previous=-1;
            for(int pixel=0;pixel<=travel;pixel++){
                int next=DialogInput.scrollForDrag(50+grab+pixel,50,travel+8,8,maximum,grab);
                check(next>=previous&&next>=0&&next<=maximum,"monotonic bounded drag");previous=next;cases++;
            }
            check(previous==maximum,"every drag reaches final row");
        }
        System.out.println("DialogInput contracts PASS cases="+cases+" floor-thumb rounded-drag zero-wheel boundaries magnitude grab-offset");
    }
}
