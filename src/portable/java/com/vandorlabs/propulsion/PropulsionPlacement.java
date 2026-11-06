package com.vandorlabs.propulsion;

/** Released six-face float hit, metadata, yaw/mirror and housing bounds. */
public final class PropulsionPlacement {
    private static final int[] CLOCKWISE={0,1,5,4,2,3};
    private PropulsionPlacement(){}
    public static int facing(int meta,int fallback){int face=meta&7;return face<6?face:fallback;}
    public static boolean powered(int meta){return (meta&8)!=0;}
    public static int metadata(int facing,boolean powered){return facing|(powered?8:0);}
    public static int wedgeCorner(int facing,float x,float y,float z){float u,v;switch(facing){case 3:u=1-x;v=y;break;case 5:u=z;v=y;break;case 4:u=1-z;v=y;break;case 1:u=x;v=z;break;case 0:u=x;v=1-z;break;case 2:u=x;v=y;break;default:throw new IllegalArgumentException("Propulsion face");}boolean right=u>=.5F,top=v>=.5F;return top?(right?2:3):right?1:0;}
    public static PropulsionDefinition select(PropulsionDefinition item,int shape,int facing,float x,float y,float z){if(item.family.isEmpty()||item.maxConnected==0||shape<0||shape>2)return item;return PropulsionDefinition.of(shape==0?item.family:shape==1?item.family+"_hexagonal":PropulsionDefinition.wedgeId(item.family,wedgeCorner(facing,x,y,z)));}
    public static int rotateFacing(int face,int turns){for(int n=0;n<turns;n++)face=CLOCKWISE[face];return face;}
    public static int rotateCorner(int corner,int facing,int turns){if(facing>1)return corner;boolean right=corner==1||corner==2,top=corner==2||corner==3;for(int n=0;n<turns;n++){boolean before=right;right=facing==1?!top:top;top=facing==1?before:!before;}return top?(right?2:3):right?1:0;}
    public static int mirrorTurns(int facing,int mirror){return mirror==1&&(facing==2||facing==3)||mirror==2&&(facing==4||facing==5)?2:0;}
    public static double[] bounds(float depth,int facing){double d=Math.max(0F,Math.min(1F,depth));if(d>=1)return new double[]{0,0,0,1,1,1};switch(facing){case 0:return new double[]{0,1-d,0,1,1,1};case 2:return new double[]{0,0,1-d,1,1,1};case 3:return new double[]{0,0,0,1,1,d};case 4:return new double[]{1-d,0,0,1,1,1};case 5:return new double[]{0,0,0,d,1,1};case 1:return new double[]{0,0,0,1,d,1};default:throw new IllegalArgumentException("Propulsion face");}}
}
