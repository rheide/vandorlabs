package com.vandorlabs.render;

public final class IndustrialTableGeometryTest {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void close(double actual,double expected){require(Math.abs(actual-expected)<1e-10,"coordinate "+actual+" != "+expected);}
    public static void main(String[] args){
        legacyCoordinates();int cases=0;
        double[][] backboards={{0,.875,1,1},{0,0,.125,1},{0,0,1,.125},{.875,0,1,1}};
        for(int face:new int[]{2,5,3,4})for(boolean inverted:new boolean[]{false,true})for(int mask=0;mask<4;mask++){
            java.util.List<IndustrialTableGeometry.Box> boxes=IndustrialTableGeometry.collision(face,inverted,(mask&1)!=0,(mask&2)!=0);require(boxes.size()==18-8*Integer.bitCount(mask),"connected strut suppression");
            IndustrialTableGeometry.Box top=boxes.get(0);close(top.x0,0);close(top.x1,1);close(top.z0,0);close(top.z1,1);close(top.y0,inverted?0:14D/16);close(top.y1,inverted?2D/16:1);
            int turn=face==2?0:face==5?1:face==3?2:3;IndustrialTableGeometry.Box back=boxes.get(1);double[] expected=backboards[turn];close(back.x0,expected[0]);close(back.z0,expected[1]);close(back.x1,expected[2]);close(back.z1,expected[3]);close(back.y0,inverted?.125:0);close(back.y1,inverted?1:.875);
            if(mask!=3){IndustrialTableGeometry.Box strut=boxes.get(2);boolean right=mask==1,reverse=turn>=2;double low=right!=reverse?.84375:.03125;close(turn%2==0?strut.x0:strut.z0,low);close(turn%2==0?strut.x1:strut.z1,low+.125);}
            require(IndustrialTableGeometry.supportFace(inverted)==(inverted?0:1),"single support face");cases++;
        }
        for(int face:new int[]{2,3,4,5})for(int other:new int[]{2,3,4,5})for(boolean a:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})require(IndustrialTableGeometry.matches(true,true,face,a,other,b)==(face==other&&a==b),"exact facing/inversion membership");
        require(!IndustrialTableGeometry.matches(false,true,2,false,2,false)&&!IndustrialTableGeometry.matches(true,false,2,false,2,false),"unloaded and unrelated exclusion");
        System.out.println("IndustrialTableGeometry PASS "+cases+" authored plate/strut states and exact loaded connection policy");
    }
    private static void legacyCoordinates(){
        java.util.List<IndustrialTableGeometry.Box> boxes=IndustrialTableGeometry.collision(2,false,false,false);IndustrialTableGeometry.Box back=boxes.get(1);close(back.x0,0);close(back.x1,1);close(back.y0,0);close(back.y1,14D/16);close(back.z0,14D/16);close(back.z1,1);
        // Fixed authored first strip values establish the one-pixel approximation and original decimal padding.
        IndustrialTableGeometry.Box strut=boxes.get(2);close(strut.x0,.5/16);close(strut.x1,2.5/16);close(strut.y0,(9-8/Math.sqrt(2)-.707107)/16);close(strut.z1,(17-(9-8/Math.sqrt(2))+.707107)/16);
    }
}
