package com.vandorlabs.render;

public final class DiagonalChunkPolicyTest {
    private static void need(boolean value){if(!value)throw new AssertionError("diagonal chunk containment");}
    public static void main(String[] args){
        int cases=0;
        for(int facing=2;facing<6;facing++)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++){
            int count=0;
            for(int x=0;x<16;x++)for(int y=0;y<16;y++)for(int z=0;z<16;z++){
                boolean contained=DiagonalChunkPolicy.contained(x,y,z,facing,inverted,mode==2,mode==1);
                need(contained==DiagonalChunkPolicy.contained(x-32,y+64,z-16,facing,inverted,mode==2,mode==1));
                if(x>0&&x<15&&y>0&&y<15&&z>0&&z<15)need(contained);
                if(contained)count++;cases++;
            }
            need(count==(mode==0?3360:mode==1?3136:3840));
        }
        need(!DiagonalChunkPolicy.contained(8,0,8,2,false,true,false));
        need(!DiagonalChunkPolicy.contained(8,15,8,2,true,true,false));
        System.out.println("DiagonalChunkPolicy PASS cases="+cases+" exact conservative coverage, interior and negative coordinates");
    }
}
