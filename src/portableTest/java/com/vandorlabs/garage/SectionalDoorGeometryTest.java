package com.vandorlabs.garage;
/** All directions share the authored Up bend; Straight retains vertical panel orientation. */
public final class SectionalDoorGeometryTest {
    private static int checks;
    private static void near(double a,double b,String why){checks++;if(!Double.isFinite(a)||Math.abs(a-b)>1e-9)throw new AssertionError(why+": "+a+" != "+b);}
    public static void main(String[] args){
        double[] old=new double[4],pose=new double[6];
        for(int width=1;width<=9;width++)for(int height=1;height<=9;height++)for(int col=0;col<width;col++)for(int row=0;row<height;row++)for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int step=0;step<=8;step++){
            double p=step/8D;GarageDoorGeometry.panel(row,height,col,width,p,direction,straight,pose);near(pose[3]*pose[3]+pose[4]*pose[4],1,"rigid orientation");
            if(step==0){near(pose[0],.5,"closed X");near(pose[1],.5-row,"closed Y");near(pose[2],.5,"closed Z");}
            if(straight){near(pose[3],0,"straight never bends");near(pose[4],1,"straight retains orientation");near(pose[2],.5,"straight stays in door plane");near(pose[0],.5+(direction==2?-(width-1D/16)*p:direction==3?(width-1D/16)*p:0),"straight horizontal travel");near(pose[1],.5-row+(direction==0?(height-1D/16)*p:direction==1?-(height-1D/16)*p:0),"straight vertical travel");}
            else if(direction==0){GarageDoorGeometry.section(row,height,p,old);near(pose[0],.5,"legacy X");near(pose[1],old[0],"legacy Y");near(pose[2],old[1],"legacy Z");near(pose[3],old[2],"legacy sine");near(pose[4],old[3],"legacy cosine");}
        }
        System.out.println("Sectional door geometry PASS checks="+checks+" four directions perpendicular/straight exact legacy Up closed alignment rigid normals");
    }
}
