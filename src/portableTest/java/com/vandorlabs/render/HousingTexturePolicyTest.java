package com.vandorlabs.render;

/** Released arithmetic oracle: compare raw double bits, including float intermediates. */
public final class HousingTexturePolicyTest {
    public static void main(String[] args){
        int checks=0;
        int[][] stair={{0,1,3,2,5,4},{0,1,2,3,4,5},{0,1,4,5,3,2},{0,1,5,4,2,3}};
        int[][] housing={{0,1,5,4,2,3},{0,1,5,4,2,3},{0,1,2,3,4,5},{0,1,3,2,5,4},{0,1,4,5,3,2},{0,1,5,4,2,3}};
        for(int face=0;face<6;face++){
            for(int facing=2;facing<6;facing++){require(HousingTexturePolicy.stairLocalFace(face,facing)==stair[facing-2][face]);checks++;}
            for(int facing=0;facing<6;facing++){require(HousingTexturePolicy.housingWorldFace(face,facing)==housing[facing][face]);checks++;}
        }
        for(int face=0;face<6;face++)for(int main:new int[]{0,384,0x40001234})for(int side:new int[]{-2,-1,0,150})for(boolean enabled:new boolean[]{false,true}){
            require(HousingTexturePolicy.baseChoice(face,main,side,enabled)==(enabled&&face>=2&&side>=0?side:main));checks++;
        }
        for(int raw:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,Integer.MAX_VALUE})for(boolean typed:new boolean[]{false,true})for(boolean present:new boolean[]{false,true})for(boolean sides:new boolean[]{false,true}){
            require(HousingTexturePolicy.itemLayout(typed,raw,present,sides)==(typed?raw:!present||sides?1:0));
            require(HousingTexturePolicy.stairItemLayout(typed,raw,sides)==(typed?raw:sides?1:0));checks+=2;
        }
        java.util.Random random=new java.util.Random(73951);
        for(int i=0;i<20000;i++){
            int mode=i%7-2,face=i%6;boolean fit=(i&1)==0;
            float min=random.nextFloat()*8,max=(i%11==0?min:min+random.nextFloat()*8),value=random.nextFloat()*16;
            double aspect=new double[]{.5,1,2,3}[i%4];
            double oldU=mode==2&&fit&&max>min?(value-min)*16/(max-min):mode==0?value:TextureLayoutMode.tileU(value,aspect);
            double oldV=fit&&max>min&&(mode==2||face>=2)?(value-min)*16/(max-min):mode==0?value:TextureLayoutMode.tileV(value,aspect);
            equal(oldU,HousingTexturePolicy.stairU(mode,fit,value,min,max,aspect));
            equal(oldV,HousingTexturePolicy.stairV(mode,fit,value,min,max,face,aspect));
            double normalized=((double)value-min)/Math.max(1e-8,(double)max-min),span=random.nextDouble()*2;
            equal(fit?normalized:TextureLayoutMode.tileU(normalized*span,aspect),HousingTexturePolicy.itemU(fit,value,min,max,span,aspect));
            equal(fit?normalized:TextureLayoutMode.tileV(normalized*span,aspect),HousingTexturePolicy.itemV(fit,value,min,max,span,aspect));checks+=4;
        }
        System.out.println("PASS: housing artwork scalar policy "+checks+" raw-bit decisions; item defaults/raw invalid modes, side finish and float UV intermediates");
    }
    private static void equal(double a,double b){require(Double.doubleToRawLongBits(a)==Double.doubleToRawLongBits(b));}
    private static void require(boolean condition){if(!condition)throw new AssertionError("released housing artwork decision changed");}
}
