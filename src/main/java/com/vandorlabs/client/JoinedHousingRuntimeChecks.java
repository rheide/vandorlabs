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
        try{tilesPicker();JoinedFitAppearanceChecks.run(mc);}catch(Exception failure){throw new IllegalStateException("Joined Fit vanilla appearance",failure);}
        legacyParity(mc);directional(mc);
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
    private static void tilesPicker()throws Exception{
        HousingTextureList picker=new HousingTextureList(20,40,220,-1,20);
        java.lang.reflect.Field categories=HousingTextureList.class.getDeclaredField("categories");categories.setAccessible(true);
        List<?> labels=(List<?>)categories.get(picker);require(labels.contains("Tiles")&&!labels.contains("Floor"),"real picker uses Tiles category only");
        require(picker.searchClick(25,47,0),"Tiles search focused");for(char c:"Tiles".toCharArray())picker.searchKey(c,0);
        java.lang.reflect.Field rows=HousingTextureList.class.getDeclaredField("rows");rows.setAccessible(true);List<?> visible=(List<?>)rows.get(picker);
        require(visible.size()==13,"Tiles search contains all thirteen supplied textures");for(Object choice:visible)require("Tiles".equals(ScreenHousingTextures.category((Integer)choice)),"Tiles search excludes other categories");
        require(picker.click(35,73,0)&&"Tiles".equals(ScreenHousingTextures.category(picker.selected())),"real Tiles search row selected");
        System.out.println("[vandorlabs][reprolab] tiles-picker PASS category rename thirteen rows actual search/click stable catalog IDs");
    }
    private static void legacyParity(Minecraft mc){
        for(net.minecraft.block.Block block:new net.minecraft.block.Block[]{ModBlocks.PROGRAMMABLE_BLOCK,net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass")})for(int old:new int[]{1,2}){
            IBlockState state=block.getDefaultState();BlockPos pos=new BlockPos(1,30,1);net.minecraft.tileentity.TileEntity seed=block.createTileEntity(mc.world,state);seed.setPos(pos);
            NBTTagCompound raw=seed.writeToNBT(new NBTTagCompound());raw.setInteger("TextureJoinDirection",5);raw.setInteger("TextureJoinParity",old);NBTTagCompound opaque=new NBTTagCompound();opaque.setInteger("TextureJoinParity",old);opaque.setString("owner","external");raw.setTag("ForgeCaps",opaque.copy());
            VehicleStructure structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,state,raw)));NBTTagCompound saved=structure.write();saved.getTagList("Blocks",10).getCompoundTagAt(0).getCompoundTag("Tile").setInteger("TextureJoinParity",old);NBTTagCompound before=saved.copy();
            VehicleStructure loaded=VehicleStructure.readDisplay(saved);VehicleWorld view=new VehicleWorld(mc.world,loaded);MovingBlockSupport.validate(loaded,view);
            NBTTagCompound migrated=loaded.cells.get(0).tileData();require(migrated.getInteger("TextureJoinDirection")==5&&migrated.getInteger("TextureJoinParity")==old&&((com.vandorlabs.surface.TextureJoinOwner)view.getTileEntity(pos)).textureJoin().parity==0,"old saved palette retains raw policy while staged tile normalizes All");require(opaque.equals(migrated.getCompoundTag("ForgeCaps"))&&saved.equals(before),"old saved preimage and opaque capabilities unchanged");
            NBTTagCompound payload=new NBTTagCompound();payload.setTag("Blocks",saved.copy());payload.setLong("Offset",0);payload.setLong("PlacedOffset",0);payload.setBoolean("Materialized",false);payload.setBoolean("GridAligned",true);
            require(com.vandorlabs.gear.ArmPayload.read(payload).write().equals(payload),"actual nested old ArmPayload round trip keeps all original bytes/fields");
            net.minecraft.block.Block armBlock=net.minecraft.block.Block.getBlockFromName("vandorlabs:gear_extender");net.minecraft.tileentity.TileEntity arm=armBlock.createTileEntity(mc.world,armBlock.getDefaultState());arm.setPos(pos);NBTTagCompound armTag=arm.writeToNBT(new NBTTagCompound());armTag.setTag("ArmPayload",payload.copy());
            VehicleStructure outer=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,armBlock.getDefaultState(),armTag)));VehicleStructure outerRead=VehicleStructure.readDisplay(outer.write());require(outerRead.cells.get(0).tileData().equals(armTag),"outer VehicleCell retains nested ArmPayload and capabilities exactly");
            NBTTagCompound changed=raw.copy();changed.setInteger("TextureJoinParity",0);changed.getCompoundTag("ForgeCaps").setInteger("TextureJoinParity",99);require(!com.vandorlabs.surface.TextureJoinMigration.same(raw,changed),"unknown capability fields remain exact");
            changed=raw.copy();changed.setInteger("TextureJoinParity",0);changed.setInteger("UnknownOwnField",1);require(!com.vandorlabs.surface.TextureJoinMigration.same(raw,changed),"unknown own edits remain refused");
            NBTTagCompound moved=VehicleTileData.translated(raw,new BlockPos(5,0,0));require(moved.getInteger("TextureJoinParity")==0&&opaque.equals(moved.getCompoundTag("ForgeCaps"))&&raw.getInteger("TextureJoinParity")==old,"translation canonicalizes only known own field on a copy");
        }
        System.out.println("[vandorlabs][reprolab] legacy-join-parity PASS four saved palettes/actual carried strict preflight/opaque capabilities/original preimages");
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
            require(width==9,"retired parity keeps directional joins and cache isolation owner="+x+" width="+width);
        }
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(new BlockPos(3,30,20));
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(saved);
        require(loaded.textureJoin().direction==5&&loaded.textureJoin().parity==0,"direction/parity saved round trip");
        System.out.println("[vandorlabs][reprolab] directional-joins PASS legacyParityAll nine-cell cardinal span cache-isolation persistence");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("joined block textures: "+message);}
}
