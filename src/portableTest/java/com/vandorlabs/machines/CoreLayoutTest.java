package com.vandorlabs.machines;

/** Independent legacy coordinate/frame oracle, including the maximum supported volume. */
public final class CoreLayoutTest {
    private static int checks;
    private static void need(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static int[] old(int x,int z,int width,int depth,int turns){switch(turns){case 1:return new int[]{depth-1-z,x};case 2:return new int[]{width-1-x,depth-1-z};case 3:return new int[]{z,width-1-x};default:return new int[]{x,z};}}
    public static void main(String[] ignored){
        int[][] sizes={{4,3,3},{3,5,3},{5,3,3},{3,3,4},{3,3,3},{1,3,1},{3,2,3},{2,4,2},{16,16,16}};int[] facing={2,5,3,4};
        for(int[] size:sizes)for(int turn=0;turn<4;turn++)for(int cornerX:new int[]{0,size[0]-1})for(int cornerZ:new int[]{0,size[2]-1}){
            CoreLayout layout=new CoreLayout(size[0],size[1],size[2],facing[turn],cornerX,cornerZ);need(layout.canonicalCorner()&&layout.cells()<=CoreLayout.MAX_CELLS,"bounded canonical layout");int[] pivot=old(cornerX,cornerZ,size[0],size[2],turn);
            need(layout.pivotX()==pivot[0]&&layout.pivotZ()==pivot[1],"legacy pivot");
            for(int x=0;x<size[0];x++)for(int y=0;y<size[1];y++)for(int z=0;z<size[2];z++){
                int[] point=old(x,z,size[0],size[2],turn);need(layout.x(x,z)==point[0]-pivot[0]&&layout.z(x,z)==point[1]-pivot[1],"legacy cell rotation and corner anchoring");
                boolean frame=y==0||((x==0||x==size[0]-1)&&(z==0||z==size[2]-1))||y==size[1]-1&&(x==0||x==size[0]-1||z==0||z==size[2]-1);need(layout.frame(x,y,z)==frame,"legacy floor/corners/top-rim policy");
            }
        }
        need(!new CoreLayout(3,3,3,2,1,1).canonicalCorner(),"corrupt saved corner is representable but cannot claim a complete assembly");
        for(int[] bad:new int[][]{{0,3,3,2,0,0},{3,1,3,2,0,0},{17,3,3,2,0,0},{3,3,3,0,0,0},{3,3,3,2,16,0}}){boolean refused=false;try{new CoreLayout(bad[0],bad[1],bad[2],bad[3],bad[4],bad[5]);}catch(IllegalArgumentException expected){refused=true;}need(refused,"invalid dimensions/direction/corner rejected");}
        System.out.println("CoreLayout PASS checks="+checks+" legacy geometry/frame parity all facings/corners and4096-cell bound");
    }
    private CoreLayoutTest(){}
}
