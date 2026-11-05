package com.vandorlabs.render;

/** The same immutable snapshot is passed from topology sampling to both render adapters. */
public final class HousingFacePlaneTest {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        int[] min={-15,-7,0},size={16,8,1};HousingFacePlane plane=new HousingFacePlane(min,size);
        min[0]=999;size[1]=999;
        require(plane.min(0)==-15&&plane.min(1)==-7&&plane.min(2)==0,"caller mutation cannot alter published offsets");
        require(plane.size(0)==16&&plane.size(1)==8&&plane.size(2)==1,"caller mutation cannot alter published spans");
        require("[-15, -7, 0][16, 8, 1]".equals(plane.toString()),"legacy mesh cache key retained");
        int count=0;
        for(int normal=0;normal<3;normal++)for(int width:new int[]{1,2,3,15,16})for(int height:new int[]{1,2,3,15,16}){
            int u=normal==0?2:0,v=normal==1?2:1;
            for(int column=0;column<width;column++)for(int row=0;row<height;row++){
                int[] offset=new int[3],span={1,1,1};offset[u]=-column;offset[v]=-row;span[u]=width;span[v]=height;
                HousingFacePlane p=new HousingFacePlane(offset,span);
                require(p.min(normal)==0&&p.size(normal)==1,"face-normal thickness stays one block");
                require(p.min(u)+column==0&&p.min(v)+row==0,"all members map to the same plane origin");
                require(p.size(u)==width&&p.size(v)==height,"axis mapping retains boundary spans");count++;
            }
        }
        System.out.println("HousingFacePlane contracts PASS cases="+count+" immutable-inputs legacy-key axis-boundaries");
    }
}
