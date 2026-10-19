package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** UV seams on real chunk-model quads for every face, facing and layout. */
final class JoinedHousingRuntimeChecks {
    static void run(Minecraft mc){
        int count=0;BlockPos base=new BlockPos(15,10,15);
        int texture=ScreenHousingTextures.screenIndex("corp/ashiro_logo");
        require(texture>0,"company logo fixture");
        for(EnumFacing face:EnumFacing.values())for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mode=0;mode<3;mode++){
            EnumFacing u=face.getAxis()==EnumFacing.Axis.X?EnumFacing.SOUTH:EnumFacing.EAST,v=face.getAxis()==EnumFacing.Axis.Y?EnumFacing.SOUTH:EnumFacing.UP;
            IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int y=0;y<3;y++)for(int x=0;x<2;x++){
                BlockPos pos=base.offset(u,x).offset(v,y);TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(pos);tile.setHousingTexture(texture);tile.setMainTextureLayout(mode);
                cells.add(new VehicleStructure.Cell(pos,state,tile.writeToNBT(new NBTTagCompound())));
            }
            VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
            ProgrammableHousingModel model=new ProgrammableHousingModel(mc.getBlockRendererDispatcher().getModelForState(state),false);
            Map<String,float[]> seams=new HashMap<>();double minU=2,minV=2,maxU=-1,maxV=-1,imageArea=0,totalArea=0;
            double[] imageMin={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},imageMax={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            for(VehicleStructure.Cell cell:cells){
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(cell.pos);
                IBlockState extended=ModBlocks.PROGRAMMABLE_BLOCK.getExtendedState(cell.state,world,cell.pos);
                com.vandorlabs.blocks.HousingFaceLayouts layout=((net.minecraftforge.common.property.IExtendedBlockState)extended).getValue(ProgrammableHousingState.JOINED);
                if(mode!=1){HousingFaceLayouts.Plane plane=layout.face(face);require(plane!=null&&plane.size(0)*plane.size(1)*plane.size(2)==6,"two-by-three plane crosses chunk borders");}
                for(BakedQuad quad:model.getQuads(extended,null,0)){
                    int[] data=quad.getVertexData();int axis=face.getAxis().ordinal();boolean selected=true;
                    for(int vertex=0;vertex<4;vertex++)selected&=Math.abs(Float.intBitsToFloat(data[vertex*7+axis])-(face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:0))<1e-6;
                    if(!selected)continue;
                    double[] a=new double[3],b=new double[3];for(int k=0;k<3;k++){a[k]=Float.intBitsToFloat(data[7+k])-Float.intBitsToFloat(data[k]);b[k]=Float.intBitsToFloat(data[21+k])-Float.intBitsToFloat(data[k]);}
                    double crossX=a[1]*b[2]-a[2]*b[1],crossY=a[2]*b[0]-a[0]*b[2],crossZ=a[0]*b[1]-a[1]*b[0];double area=Math.sqrt(crossX*crossX+crossY*crossY+crossZ*crossZ);totalArea+=area;
                    if(quad.getSprite().getIconName().equals("minecraft:blocks/concrete_black"))continue;
                    imageArea+=area;
                    for(int vertex=0;vertex<4;vertex++){
                        int off=vertex*7;
                        for(int index=0;index<2;index++){int worldAxis=(index==0?u:v).getAxis().ordinal();double value=(worldAxis==0?cell.pos.getX():worldAxis==1?cell.pos.getY():cell.pos.getZ())+Float.intBitsToFloat(data[off+worldAxis]);imageMin[index]=Math.min(imageMin[index],value);imageMax[index]=Math.max(imageMax[index],value);}
                        String key=(cell.pos.getX()+Float.intBitsToFloat(data[off]))+":"+(cell.pos.getY()+Float.intBitsToFloat(data[off+1]))+":"+(cell.pos.getZ()+Float.intBitsToFloat(data[off+2]));
                        float uu=(Float.intBitsToFloat(data[off+4])-quad.getSprite().getMinU())/(quad.getSprite().getMaxU()-quad.getSprite().getMinU()),vv=(Float.intBitsToFloat(data[off+5])-quad.getSprite().getMinV())/(quad.getSprite().getMaxV()-quad.getSprite().getMinV());
                        minU=Math.min(minU,uu);minV=Math.min(minV,vv);maxU=Math.max(maxU,uu);maxV=Math.max(maxV,vv);
                        if(mode!=1){float[] previous=seams.put(key,new float[]{uu,vv});if(previous!=null)require(Math.abs(previous[0]-uu)<1e-4&&Math.abs(previous[1]-vv)<1e-4,"joined texture has no UV seam "+face+facing+mode);}
                    }
                }
            }
            if(mode==0)require(Math.abs(minU)<1e-4&&Math.abs(minV)<1e-4&&Math.abs(maxU-1)<1e-4&&Math.abs(maxV-1)<1e-4,"Stretch maps one complete image over the group");
            if(mode==2){
                require(Math.abs(minU)<1e-4&&Math.abs(minV)<1e-4&&Math.abs(maxU-1)<1e-4&&Math.abs(maxV-1)<1e-4,"Fit retains the complete image");
                require(Math.abs(imageArea-4)<1e-4&&Math.abs(totalArea-6)<1e-4,"Fit covers 2x2 with opaque margins filling the 2x3 face");
                require(Math.abs(imageMax[0]-imageMin[0]-2)<1e-4&&Math.abs(imageMax[1]-imageMin[1]-2)<1e-4,"square texture has square physical dimensions");
                double groupU=base.getX()*u.getFrontOffsetX()+base.getY()*u.getFrontOffsetY()+base.getZ()*u.getFrontOffsetZ(),groupV=base.getX()*v.getFrontOffsetX()+base.getY()*v.getFrontOffsetY()+base.getZ()*v.getFrontOffsetZ();
                require(Math.abs(imageMin[0]-groupU)<1e-4&&Math.abs(imageMin[1]-groupV-.5)<1e-4,"Fit centers the image without cropping");
            }
            if(mode!=1){TileEntityAnimatedScreenSelector changed=(TileEntityAnimatedScreenSelector)world.getTileEntity(base);changed.setHousingTexture(texture==8?9:8);HousingFaceLayouts split=JoinedHousingFaces.sample(changed,63);HousingFaceLayouts.Plane plane=split.face(face);require(plane==null||plane.size(0)*plane.size(1)*plane.size(2)==1,"different face materials split the shared image");}
            count++;
        }
        System.out.println("[vandorlabs][reprolab] joined-block-textures PASS "+count+" real-model cases, six faces four facings, chunk borders, continuous UVs, Fit aspect and material splits");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("joined block textures: "+message);}
}
