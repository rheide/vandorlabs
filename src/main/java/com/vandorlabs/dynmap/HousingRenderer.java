package com.vandorlabs.dynmap;

import com.vandorlabs.tiles.ScreenHousingTextures;
import org.dynmap.renderer.*;

/** Shared saved-material palette for map-only adapters. Does not access live worlds. */
public abstract class HousingRenderer extends CustomRenderer {
    @Override protected int getMaximumTextureCount() { return ScreenHousingTextures.BUILTIN_COUNT; }
    protected static int integer(Object value,int fallback) { return value instanceof Number?((Number)value).intValue():fallback; }
    protected static boolean booleanValue(Object value) { return Boolean.TRUE.equals(value)||integer(value,0)!=0; }
    protected static int texture(Object value) {
        int choice=ScreenHousingTextures.canonicalChoice(integer(value,0));
        return choice<ScreenHousingTextures.BUILTIN_COUNT?choice:0;
    }
    protected static String[] fields(String... extra) {
        String[] fields=new String[extra.length+4];
        fields[0]="housingTexture";fields[1]="SideTexture";fields[2]="FaceTexturesEnabled";fields[3]="FaceTextures";
        System.arraycopy(extra,0,fields,4,extra.length);return fields;
    }
    @Override public String[] getTileEntityFieldsNeeded() { return fields(); }
    protected static int selected(MapDataContext context,int localFace,boolean sides) {
        Object overrides=context.getBlockTileEntityField("FaceTextures");
        if(booleanValue(context.getBlockTileEntityField("FaceTexturesEnabled"))&&overrides instanceof int[]){
            int[] choices=(int[])overrides;
            if(choices.length==6&&choices[localFace]>=0)return texture(choices[localFace]);
        }
        int side=integer(context.getBlockTileEntityField("SideTexture"),-1);
        return texture(sides&&localFace>1&&side>=0?side:context.getBlockTileEntityField("housingTexture"));
    }
    protected static int[] faces(MapDataContext context,boolean sides) {
        net.minecraft.util.EnumFacing front=net.minecraft.util.EnumFacing.NORTH;
        for(net.minecraft.util.EnumFacing facing:net.minecraft.util.EnumFacing.values())
            if(context.getBlockType().isStateMatch("facing",facing.getName()))front=facing;
        int turns=((int)(180-front.getHorizontalAngle())/90)&3;
        net.minecraft.util.EnumFacing[] order={net.minecraft.util.EnumFacing.DOWN,net.minecraft.util.EnumFacing.UP,net.minecraft.util.EnumFacing.WEST,net.minecraft.util.EnumFacing.EAST,net.minecraft.util.EnumFacing.NORTH,net.minecraft.util.EnumFacing.SOUTH};
        int[] textures=new int[6];
        for(int i=0;i<6;i++){
            net.minecraft.util.EnumFacing local=order[i];
            if(local.getAxis()!=net.minecraft.util.EnumFacing.Axis.Y)for(int j=0;j<turns;j++)local=local.rotateYCCW();
            textures[i]=selected(context,local.getIndex(),sides);
        }
        return textures;
    }
}
