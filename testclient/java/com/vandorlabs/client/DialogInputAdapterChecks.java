package com.vandorlabs.client;

/** Keep the legacy static helper signatures while sharing the exact drag policy. */
public final class DialogInputAdapterChecks {
    public static void main(String[] args){
        int cases=0;
        for(int[] fixture:new int[][]{{105,100,100,20,10,5,0},{145,100,100,20,10,5,5},{185,100,100,20,10,5,10},{105,100,12,2,3,0,2},{999,100,7,8,3,0,0},{999,100,100,8,0,0,0}}){
            int wall=GuiProgrammableWall.scrollForDrag(fixture[0],fixture[1],fixture[2],fixture[3],fixture[4],fixture[5]);
            int input=GuiProgrammableInput.scrollForDrag(fixture[0],fixture[1],fixture[2],fixture[3],fixture[4],fixture[5]);
            if(wall!=fixture[6]||input!=fixture[6])throw new AssertionError("Legacy scroll wrapper changed fixture="+cases);cases++;
        }
        System.out.println("Dialog input adapters PASS cases="+cases+" wall-and-input static signatures retained");
    }
}
