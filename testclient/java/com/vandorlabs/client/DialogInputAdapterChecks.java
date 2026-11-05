package com.vandorlabs.client;

/** Keep legacy helper signatures while sharing drag and option-cycle policy. */
public final class DialogInputAdapterChecks {
    public static void main(String[] args)throws Exception{
        int cases=0;
        for(int[] fixture:new int[][]{{105,100,100,20,10,5,0},{145,100,100,20,10,5,5},{185,100,100,20,10,5,10},{105,100,12,2,3,0,2},{999,100,7,8,3,0,0},{999,100,100,8,0,0,0}}){
            int wall=GuiProgrammableWall.scrollForDrag(fixture[0],fixture[1],fixture[2],fixture[3],fixture[4],fixture[5]);
            int input=GuiProgrammableInput.scrollForDrag(fixture[0],fixture[1],fixture[2],fixture[3],fixture[4],fixture[5]);
            if(wall!=fixture[6]||input!=fixture[6])throw new AssertionError("Legacy scroll wrapper changed fixture="+cases);cases++;
        }
        java.lang.reflect.Field direction=GuiOptionCycle.class.getDeclaredField("step");direction.setAccessible(true);
        int previous=direction.getInt(null);
        try{
            for(int[] fixture:new int[][]{{0,0,2,1,2},{1,0,2,2,0},{2,0,2,0,1},{-1,-1,15,0,15},{0,-1,15,1,-1},{15,-1,15,-1,14},{7,7,7,7,7}}){
                direction.setInt(null,1);
                if(GuiOptionCycle.next(fixture[0],fixture[1],fixture[2])!=fixture[3])throw new AssertionError("Forward option cycle");
                direction.setInt(null,-1);
                if(GuiOptionCycle.next(fixture[0],fixture[1],fixture[2])!=fixture[4])throw new AssertionError("Reverse option cycle");
                cases++;
            }
        }finally{direction.setInt(null,previous);}
        System.out.println("Dialog input adapters PASS cases="+cases+" scroll signatures and forward/reverse inclusive option bounds retained");
    }
}
