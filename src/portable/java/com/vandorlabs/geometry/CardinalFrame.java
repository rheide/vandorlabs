package com.vandorlabs.geometry;

/** The 24 proper axis-aligned frames. Direction IDs retain the Minecraft six-face order. */
public final class CardinalFrame {
    private static final int[][] VECTOR={{0,-1,0},{0,1,0},{0,0,-1},{0,0,1},{-1,0,0},{1,0,0}};
    private static final CardinalFrame[][] FRAMES=new CardinalFrame[6][6];
    static{for(int forward=0;forward<6;forward++)for(int up=0;up<6;up++)if(dot(VECTOR[forward],VECTOR[up])==0)FRAMES[forward][up]=new CardinalFrame(forward,up);}
    private final int forward,up;
    private final int[] matrix=new int[9];
    private CardinalFrame(int forward,int up){
        this.forward=forward;this.up=up;int[] f=VECTOR[forward],u=VECTOR[up];
        matrix[0]=f[1]*u[2]-f[2]*u[1];matrix[3]=f[2]*u[0]-f[0]*u[2];matrix[6]=f[0]*u[1]-f[1]*u[0];
        for(int row=0;row<3;row++){matrix[row*3+1]=u[row];matrix[row*3+2]=-f[row];}
    }
    public static CardinalFrame of(int forward,int up){if(forward<0||forward>=6||up<0||up>=6||FRAMES[forward][up]==null)throw new IllegalArgumentException("Invalid perpendicular frame");return FRAMES[forward][up];}
    public static int defaultUp(int forward){if(forward<0||forward>=6)throw new IllegalArgumentException("Invalid frame facing");return forward<2?2:1;}
    public int forward(){return forward;}
    public int up(){return up;}
    public int component(int row,int col){return matrix[row*3+col];}
    public double transform(int row,double x,double y,double z){return matrix[row*3]*x+matrix[row*3+1]*y+matrix[row*3+2]*z;}
    public int direction(int direction){int[] v=VECTOR[direction];int x=(int)transform(0,v[0],v[1],v[2]),y=(int)transform(1,v[0],v[1],v[2]),z=(int)transform(2,v[0],v[1],v[2]);for(int i=0;i<6;i++)if(VECTOR[i][0]==x&&VECTOR[i][1]==y&&VECTOR[i][2]==z)return i;throw new AssertionError("Non-cardinal frame direction");}
    private static int dot(int[] a,int[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}
