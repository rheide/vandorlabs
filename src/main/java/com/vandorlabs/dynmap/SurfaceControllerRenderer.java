package com.vandorlabs.dynmap;

import com.vandorlabs.tiles.ScreenHousingTextures;
import java.util.*;
import net.minecraft.util.EnumFacing;
import org.dynmap.renderer.*;

/** Native controller faces also register the state fields consumed by attached surfaces. */
public final class SurfaceControllerRenderer extends HousingRenderer {
    @Override protected int getMaximumTextureCount(){return 2*ScreenHousingTextures.BUILTIN_COUNT+21;}
    @Override public String[] getTileEntityFieldsNeeded(){return new String[]{"Running","Clockwise","CopyBusy","CopyActiveTicks"};}
    @Override public RenderPatch[] getRenderPatchList(MapDataContext context){
        int base=2*ScreenHousingTextures.BUILTIN_COUNT+5;boolean copy="vandorlabs:copy_controller".equals(context.getBlockType().blockName),active=booleanValue(context.getBlockTileEntityField("Running")),clockwise=booleanValue(context.getBlockTileEntityField("Clockwise"));
        int[] local=copy?new int[]{0,1,2,3,4,5}:new int[]{6,active?8:7,9,10,(active?14:12)+(clockwise?0:1),11};
        EnumFacing front=EnumFacing.NORTH;for(EnumFacing face:EnumFacing.HORIZONTALS)if(context.getBlockType().isStateMatch("facing",face.getName()))front=face;int turns=front==EnumFacing.EAST?1:front==EnumFacing.SOUTH?2:front==EnumFacing.WEST?3:0;
        EnumFacing[] order={EnumFacing.DOWN,EnumFacing.UP,EnumFacing.WEST,EnumFacing.EAST,EnumFacing.NORTH,EnumFacing.SOUTH};int[] textures=new int[6];for(int i=0;i<6;i++){EnumFacing face=order[i];for(int turn=0;turn<turns&&face.getAxis()!=EnumFacing.Axis.Y;turn++)face=face.rotateYCCW();int index=0;while(order[index]!=face)index++;textures[i]=base+local[index];}
        List<RenderPatch> out=new ArrayList<>();addBox(context.getPatchFactory(),out,0,1,0,1,0,1,textures);return out.toArray(new RenderPatch[0]);
    }
}
