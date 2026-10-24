package com.vandorlabs.dynmap;

import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.render.DoorLeafTransform;
import com.vandorlabs.tiles.*;
import net.minecraft.util.math.Vec3d;
import org.dynmap.renderer.*;
import java.util.*;

/** Native authored frame and leaf geometry, transformed exactly as the game's SOUTH-authored doors. */
public final class ProgrammableDoorRenderer extends HousingRenderer {
    static final String[] DOOR_FIELDS={"SpaceDesign","SpaceDetail","SpaceFramed","SpaceDoorSliding","SpaceSlideDirection","SpaceDoorPlacementDepth","SpaceDoorHinges","DoorFaceTexture","LargeDoorColumn","LargeDoorRow"};
    @Override public String[] getTileEntityFieldsNeeded(){return DOOR_FIELDS.clone();}
    @Override protected int getMaximumTextureCount(){return ScreenHousingTextures.BUILTIN_COUNT*2+3;}
    static Object field(MapDataContext c,String key,int x,int y,int z){return x==0&&y==0&&z==0?c.getBlockTileEntityField(key):c.getBlockTileEntityFieldAt(key,x,y,z);}
    static boolean setting(Object value,boolean fallback){return value==null?fallback:booleanValue(value);}
    static int material(MapDataContext c,int x,int y,int z){
        int choice=integer(field(c,"DoorFaceTexture",x,y,z),-1);
        if(choice<0)choice=ScreenHousingTextures.doorIndex(integer(field(c,"SpaceDesign",x,y,z),2),integer(field(c,"SpaceDetail",x,y,z),1));
        return ScreenHousingTextures.BUILTIN_COUNT+texture(choice);
    }
    static Vec3d orient(Vec3d p,DynmapBlockState state){
        if(state.isStateMatch("facing","east"))return new Vec3d(p.z,p.y,1-p.x);
        if(state.isStateMatch("facing","west"))return new Vec3d(1-p.z,p.y,p.x);
        if(state.isStateMatch("facing","north"))return new Vec3d(1-p.x,p.y,1-p.z);
        return p;
    }
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        DynmapBlockState state=context.getBlockType();boolean upper=state.isStateMatch("half","upper");int row=upper?1:0;
        DynmapBlockState lower=upper?context.getBlockTypeAt(0,-1,0):state;
        if(lower==null||!lower.matchingBaseState(state))return new RenderPatch[0];
        DynmapBlockState top=upper?state:context.getBlockTypeAt(0,1,0);
        boolean right=top==null||!top.isStateMatch("hinge","right"),open=lower.isStateMatch("open","true");
        boolean sliding=setting(field(context,"SpaceDoorSliding",0,-row,0),false),frame=setting(field(context,"SpaceFramed",0,-row,0),true),hinges=setting(field(context,"SpaceDoorHinges",0,-row,0),true);
        int direction=Math.max(0,Math.min(2,integer(field(context,"SpaceSlideDirection",0,-row,0),0))),depth=integer(field(context,"SpaceDoorPlacementDepth",0,-row,0),1);
        // TileEntitySpaceDoor.model() uses the Standard design for native motion geometry.
        String base=TileEntitySpaceDoor.modelId(2,sliding,frame)+(lower.isStateMatch("paired","true")?"_paired":"")+(right?"_right":"_left");
        double offset=SpaceDoorData.positionOffset(sliding,frame,hinges,depth);
        DoorLeafTransform pose=DoorLeafTransform.calculate(sliding,right?15:-15,right?15:1,11.24,right?-90:90,open?1:0);
        final DynmapBlockState facing=lower;final int cell=row;
        List<RenderPatch> result=new ArrayList<>();int texture=material(context,0,-row,0),materials=ScreenHousingTextures.BUILTIN_COUNT*2;
        for(boolean leaf:new boolean[]{false,true}){
            final boolean moving=leaf;
            DoorMapMesh.emit(context,result,base+(leaf?"_leaf":"_fixed")+(!sliding&&!hinges?"_no_hinges":""),p->{
                double x=p.x,y=p.y,z=p.z;
                if(moving){
                    if(sliding&&direction!=0)y+=open?SpaceDoorData.verticalTravel(frame,direction):0;
                    else{
                        double a=Math.toRadians(pose.angleDegrees),dx=x-pose.pivotX,dz=z-pose.pivotZ;
                        x=pose.pivotX+Math.cos(a)*dx+Math.sin(a)*dz+pose.translateX;
                        z=pose.pivotZ-Math.sin(a)*dx+Math.cos(a)*dz;
                    }
                }
                return orient(new Vec3d(x,y,z+offset),facing).addVector(0,-cell,0);
            },texture,materials);
        }
        return result.toArray(new RenderPatch[0]);
    }
}
