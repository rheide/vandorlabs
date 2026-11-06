package com.vandorlabs.render;
/** Float Rodrigues and center/corner multiplication used by released Forge model variants. */
public final class LegacyVariantMatrix {
 private LegacyVariantMatrix(){}
 private static float[][] identity(){float[][] m=new float[4][4];for(int i=0;i<4;i++)m[i][i]=1;return m;}
 private static float[][] multiply(float[][] a,float[][] b){float[][] result=new float[4][4];for(int i=0;i<4;i++)for(int j=0;j<4;j++)result[i][j]=a[i][0]*b[0][j]+a[i][1]*b[1][j]+a[i][2]*b[2][j]+a[i][3]*b[3][j];return result;}
 public static float[][] y(float degrees){float[][] m=identity();float t=-degrees*.017453292F,c=(float)Math.cos(t),s=(float)Math.sin(t);m[0][0]=m[2][2]=c;m[0][2]=s;m[2][0]=-s;m[1][1]=(1-c)+c;float[][] plus=identity(),minus=identity();for(int i=0;i<3;i++){plus[i][3]=.5F;minus[i][3]=-.5F;}return multiply(multiply(plus,m),minus);}
 public static void apply(float[][] m,float x,float y,float z,float[] out){out[0]=m[0][0]*x+m[0][1]*y+m[0][2]*z+m[0][3];out[1]=m[1][0]*x+m[1][1]*y+m[1][2]*z+m[1][3];out[2]=m[2][0]*x+m[2][1]*y+m[2][2]*z+m[2][3];}
}
