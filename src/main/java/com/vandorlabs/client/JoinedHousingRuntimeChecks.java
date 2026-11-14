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
        try{JoinedFitAppearanceChecks.run(mc);}catch(Exception failure){throw new IllegalStateException("Joined Fit vanilla appearance",failure);}
        directional(mc);
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
                    int[] data=quad.getVertexData();int stride=data.length/4;int axis=face.getAxis().ordinal();boolean selected=true;
                    for(int vertex=0;vertex<4;vertex++)selected&=Math.abs(Float.intBitsToFloat(data[vertex*stride+axis])-(face.getAxisDirection()==EnumFacing.AxisDirection.POSITIVE?1:0))<1e-6;
                    if(!selected)continue;
                    double[] a=new double[3],b=new double[3];for(int k=0;k<3;k++){a[k]=Float.intBitsToFloat(data[stride+k])-Float.intBitsToFloat(data[k]);b[k]=Float.intBitsToFloat(data[3*stride+k])-Float.intBitsToFloat(data[k]);}
                    double crossX=a[1]*b[2]-a[2]*b[1],crossY=a[2]*b[0]-a[0]*b[2],crossZ=a[0]*b[1]-a[1]*b[0];double area=Math.sqrt(crossX*crossX+crossY*crossY+crossZ*crossZ);totalArea+=area;
                    if(quad.getSprite().getIconName().equals("minecraft:blocks/concrete_black"))continue;
                    imageArea+=area;
                    for(int vertex=0;vertex<4;vertex++){
                        int off=vertex*stride;
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
                require(Math.abs(minU+maxU-1)<1e-4&&Math.abs(minV+maxV-1)<1e-4,"Fit centers the released image crop");
                double du=maxU-minU,dv=maxV-minV;
                require(Math.abs(Math.max(du,dv)-1)<1e-4&&Math.abs(Math.min(du,dv)-2/3D)<1e-4,"square artwork keeps its aspect through the legacy centered crop");
                require(Math.abs(imageArea-6)<1e-4&&Math.abs(totalArea-6)<1e-4,"Fit covers the entire joined face without added margins");
                require(Math.abs(imageMax[0]-imageMin[0]-2)<1e-4&&Math.abs(imageMax[1]-imageMin[1]-3)<1e-4,"Fit leaves full physical face geometry unchanged");
            }
            if(mode!=1){TileEntityAnimatedScreenSelector changed=(TileEntityAnimatedScreenSelector)world.getTileEntity(base);changed.setHousingTexture(texture==8?9:8);HousingFaceLayouts split=JoinedHousingFaces.sample(changed,63);HousingFaceLayouts.Plane plane=split.face(face);require(plane==null||plane.size(0)*plane.size(1)*plane.size(2)==1,"different face materials split the shared image");}
            count++;
        }
        System.out.println("[vandorlabs][reprolab] joined-block-textures PASS "+count+" real-model cases, six faces four facings, chunk borders, continuous UVs, Fit aspect and material splits");
    }
    private static void directional(Minecraft mc){
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState();
        for(int x=0;x<=8;x++){
            BlockPos pos=new BlockPos(x,30,20);TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(pos);tile.setMainTextureLayout(0);
            tile.setTextureJoin(new com.vandorlabs.surface.TextureJoinSettings(x==3||x==2?5:1,x==3||x==2?1:0));
            cells.add(new VehicleStructure.Cell(pos,state,tile.writeToNBT(new NBTTagCompound())));
        }
        VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
        // Prime the unrestricted owner's cache first; directed neighbors must not inherit it.
        JoinedHousingFaces.sample((TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(0,30,20)),63);
        for(int x:new int[]{3,2,3,2}){
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(x,30,20));
            HousingFaceLayouts.Plane plane=JoinedHousingFaces.sample(tile,63).face(EnumFacing.UP);int width=plane==null?1:plane.size(0);
            require(width==(x==3?3:1),"owner-directed Even coordinate filter and cache isolation owner="+x+" width="+width);
        }
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(3,30,20));
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(saved);
        require(loaded.textureJoin().direction==5&&loaded.textureJoin().parity==1,"direction/parity saved round trip");
        System.out.println("[vandorlabs][reprolab] directional-joins PASS owner3-Even neighbors2/4 owner2-Even singleton mixed-neighbor filters cache-isolation persistence");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("joined block textures: "+message);}
}
