package com.vandorlabs.render;

/** Golden third-boundaries retain the float rounding of legacy item hit coordinates. */
public final class ScreenMountContractTest {
    private static int checks;
    private static void check(boolean result,String label){checks++;if(!result)throw new AssertionError(label);}
    public static void main(String[] args){
        float[] hit={0,Math.nextDown(1F/3F),1F/3F,Math.nextUp(1F/3F),Math.nextDown(2F/3F),2F/3F,Math.nextUp(2F/3F),1};
        int[] direct={0,0,1,1,1,2,2,2},reflected={2,2,1,1,1,0,0,0};
        for(int i=0;i<hit.length;i++){
            check(ScreenFamilyPolicy.wallPosition(hit[i])==direct[i],"wall thirds "+i);
            for(int face=0;face<6;face++){
                int[] expected=face==3||face==5?reflected:direct;
                float x=face==4||face==5?hit[i]:.5F,z=face==4||face==5?.5F:hit[i];
                check(ScreenFamilyPolicy.ceilingPosition(face,x,z)==expected[i],"ceiling thirds face="+face+" coordinate="+hit[i]);
            }
        }
        System.out.println("ScreenMountContractTest PASS "+checks+" boundary assertions");
    }
}
