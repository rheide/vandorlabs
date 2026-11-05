package com.vandorlabs.render;

public final class PanelDepthPolicyTest {
    private static void require(boolean ok){if(!ok)throw new AssertionError("panel placement");}
    public static void main(String[] ignored){
        float[] values={Float.NEGATIVE_INFINITY,-1F,0F,Math.nextDown(1F/3F),1F/3F,Math.nextUp(1F/3F),.5F,Math.nextDown(2F/3F),2F/3F,Math.nextUp(2F/3F),1F,2F,Float.POSITIVE_INFINITY,Float.NaN};
        int cases=0;
        for(int face=0;face<6;face++)for(float x:values)for(float z:values){
            float old=face==3?1F-z:face==5?1F-x:face==4?x:z;
            int expected=old<1F/3F?1:old>2F/3F?2:0;
            require(PanelDepthPolicy.fromHit(face,x,z)==expected);cases++;
        }
        for(int d:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,Integer.MAX_VALUE}){
            int expected=d==1?0:d==2?12:6;
            require(PanelDepthPolicy.start(d)==expected&&PanelDepthPolicy.offset(d)==expected-6);
        }
        System.out.println("PanelDepthPolicy PASS "+cases+" exact float boundary/orientation cases");
    }
}
