package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Reference records from actual Forge models; no copied mesh, UV or item-transform equations. */
final class LegacyCubeReferenceExport {
    private static final Map<String,String> MATERIALS=new HashMap<>(),SOURCES=new HashMap<>();
    private static final EnumFacing[] BUCKETS={null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST};
    private LegacyCubeReferenceExport(){}
    static void run(Minecraft mc,File output)throws IOException{
        JoinedHousingRuntimeChecks.run(mc);
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++){
            String sprite=ScreenHousingTextures.texture(i);MATERIALS.putIfAbsent(sprite,ScreenHousingTextures.IDS[ScreenHousingTextures.localIndex(ScreenHousingTextures.canonicalChoice(i))]);
            JsonObject entry=ScreenHousingTextures.entry(i);if(entry!=null&&entry.has("source"))SOURCES.putIfAbsent(sprite,entry.get("source").getAsString());
        }
        int square=ScreenHousingTextures.screenIndex("corp/ashiro_logo"),wide=aspectChoice(mc,2),portrait=aspectChoice(mc,.5);
        need(square>0,"square reference artwork");
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("target","forge-1.12.2");root.addProperty("uvSpace","authored-content normalized against actual sprite getMin/Max; atlas padding excluded");
        JsonObject choices=new JsonObject();choices.addProperty("default",0);choices.addProperty("square",square);choices.addProperty("wide",wide);choices.addProperty("portrait",portrait);root.add("choices",choices);
        JsonArray worldRows=new JsonArray(),items=new JsonArray();BlockPos origin=new BlockPos(15,10,15);
        int[] main={0,square,wide,portrait,0,0};String[] profiles={"default","square","wide","portrait","overrides","dormant"};
        for(EnumFacing facing:EnumFacing.values())for(int layout=0;layout<3;layout++)for(int profile=0;profile<profiles.length;profile++){
            IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing);
            TileEntityAnimatedScreenSelector tile=tile(origin,main[profile],layout,profile==4,profile>=4);
            VehicleWorld world=view(mc,Collections.singletonList(new VehicleStructure.Cell(origin,state,tile.writeToNBT(new NBTTagCompound()))));
            JsonObject row=worldRow(mc,world,state,origin,"single/"+facing.getIndex()+"/"+layout+"/"+profiles[profile]);
            row.addProperty("facing",facing.getIndex());row.addProperty("layout",layout);row.addProperty("profile",profiles[profile]);row.addProperty("main",main[profile]);row.addProperty("facesEnabled",profile==4);row.add("faceChoices",numbers(new int[]{1,2,3,4,5,6}));worldRows.add(row);
        }
        int[] joinedMaterials={square,wide,portrait};String[] joinedNames={"square","wide","portrait"};
        for(EnumFacing face:EnumFacing.values())for(int layout=0;layout<3;layout++)for(int material=0;material<joinedMaterials.length;material++){
            EnumFacing u=face.getAxis()==EnumFacing.Axis.X?EnumFacing.SOUTH:EnumFacing.EAST,v=face.getAxis()==EnumFacing.Axis.Y?EnumFacing.SOUTH:EnumFacing.UP;
            IBlockState state=ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.NORTH);
            List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int y=0;y<3;y++)for(int x=0;x<2;x++){BlockPos pos=origin.offset(u,x).offset(v,y);cells.add(new VehicleStructure.Cell(pos,state,tile(pos,joinedMaterials[material],layout,false,false).writeToNBT(new NBTTagCompound())));}
            VehicleWorld world=view(mc,cells);
            for(int member=0;member<cells.size();member++){
                BlockPos pos=cells.get(member).pos;JsonObject row=worldRow(mc,world,state,pos,"joined/"+face.getIndex()+"/"+layout+"/"+joinedNames[material]+"/"+member);
                row.addProperty("surfaceFace",face.getIndex());row.addProperty("facing",EnumFacing.NORTH.getIndex());row.addProperty("layout",layout);row.addProperty("profile",joinedNames[material]);row.addProperty("main",joinedMaterials[material]);row.addProperty("member",member);row.add("position",numbers(new int[]{pos.getX(),pos.getY(),pos.getZ()}));row.add("origin",numbers(new int[]{origin.getX(),origin.getY(),origin.getZ()}));row.add("group",numbers(new int[]{2,3}));worldRows.add(row);
            }
        }
        items.add(item(mc,"default",new ItemStack(ModBlocks.PROGRAMMABLE_BLOCK),0,-1,false,false));
        items.add(item(mc,"unversioned15",configured(15,1,false,false),15,1,false,false));
        for(int material=0;material<joinedMaterials.length;material++)for(int layout=0;layout<3;layout++)for(boolean faces:new boolean[]{false,true})items.add(item(mc,joinedNames[material]+"/"+layout+"/"+faces,configured(joinedMaterials[material],layout,faces,true),joinedMaterials[material],layout,faces,true));
        need(worldRows.size()==432&&items.size()==20,"bounded case coverage");root.add("world",worldRows);root.add("items",items);
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(output,"legacy-cube-reference.json")),StandardCharsets.UTF_8)){new Gson().toJson(root,writer);}
        System.out.println("[vandorlabs][reprolab] cube-reference-export PASS world=432 single=108 joined=324 items=20 actual-models content-UVs transforms unchanged-NBT");
    }
    private static int aspectChoice(Minecraft mc,double target){
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.visible(i)&&(HousingTextureList.generalTexture(i)||HousingTextureList.displayTexture(i))){
            TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(i));
            if(sprite!=mc.getTextureMapBlocks().getMissingSprite()&&Math.abs(UnifiedTextureSprites.aspect(sprite)-target)<.025)return ScreenHousingTextures.choiceAt(i);
        }
        throw new IllegalStateException("Missing visible cube artwork aspect "+target);
    }
    private static TileEntityAnimatedScreenSelector tile(BlockPos pos,int main,int layout,boolean enabled,boolean stored){
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(pos);tile.setHousingTexture(main);tile.setMainTextureLayout(layout);if(stored)tile.setFaceTextures(new FaceTextures(enabled,new int[]{1,2,3,4,5,6}));return tile;
    }
    private static VehicleWorld view(Minecraft mc,List<VehicleStructure.Cell> cells){return new VehicleWorld(mc.world,VehicleStructure.display(cells));}
    private static JsonObject worldRow(Minecraft mc,VehicleWorld world,IBlockState state,BlockPos pos,String key){
        IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);IBlockState extended=state.getBlock().getExtendedState(state,world,pos);
        JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("ao",model.isAmbientOcclusion());row.addProperty("modelClass",model.getClass().getName());row.add("quads",quads(model,extended));
        net.minecraftforge.common.property.IExtendedBlockState sampled=(net.minecraftforge.common.property.IExtendedBlockState)extended;
        row.addProperty("visible",sampled.getValue(ProgrammableHousingState.VISIBLE));JsonObject planes=new JsonObject();
        HousingFaceLayouts joined=sampled.getValue(ProgrammableHousingState.JOINED);
        if(joined!=null)for(EnumFacing face:EnumFacing.values()){
            HousingFaceLayouts.Plane plane=joined.face(face);if(plane==null)continue;JsonObject value=new JsonObject();
            value.add("min",numbers(new int[]{plane.min(0),plane.min(1),plane.min(2)}));value.add("size",numbers(new int[]{plane.size(0),plane.size(1),plane.size(2)}));planes.add(Integer.toString(face.getIndex()),value);
        }
        row.add("planes",planes);return row;
    }
    private static ItemStack configured(int main,int layout,boolean faces,boolean versioned){
        ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_BLOCK);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("housingTexture",main);if(versioned)tag.setInteger("HousingTextureVersion",1);tag.setInteger("MainTextureLayout",layout);tag.setBoolean("FaceTexturesEnabled",faces);tag.setIntArray("FaceTextures",new int[]{1,2,3,4,5,6});tag.setString("OpaquePreviewMarker","retained");NBTTagCompound opaque=new NBTTagCompound();opaque.setInteger("Nested",42);tag.setTag("OpaquePreviewData",opaque);stack.setTagInfo("BlockEntityTag",tag);return stack;
    }
    private static JsonObject item(Minecraft mc,String key,ItemStack stack,int main,int layout,boolean faces,boolean versioned){
        NBTTagCompound before=stack.hasTagCompound()?stack.getTagCompound().copy():null;IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);
        JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("main",main);row.addProperty("layout",layout);row.addProperty("faces",faces);row.addProperty("versioned",versioned);row.addProperty("ao",model.isAmbientOcclusion());row.addProperty("gui3d",model.isGui3d());row.addProperty("modelClass",model.getClass().getName());row.addProperty("nbt",before==null?"":before.toString());
        ItemTransformVec3f gui=model.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GUI);JsonArray transform=new JsonArray();for(double value:new double[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(value);row.add("gui",transform);row.add("quads",quads(model,null));
        need(Objects.equals(before,stack.getTagCompound()),"item overrides changed payload "+key);row.addProperty("payloadUnchanged",true);return row;
    }
    private static JsonArray quads(IBakedModel model,IBlockState state){
        JsonArray out=new JsonArray();
        for(EnumFacing bucket:BUCKETS)for(BakedQuad quad:model.getQuads(state,bucket,0)){
            TextureAtlasSprite sprite=quad.getSprite();JsonObject row=new JsonObject();row.addProperty("bucket",bucket==null?-1:bucket.getIndex());row.addProperty("face",quad.getFace().getIndex());row.addProperty("shade",quad.shouldApplyDiffuseLighting());row.addProperty("tint",quad.getTintIndex());row.addProperty("sprite",sprite.getIconName());row.addProperty("material",MATERIALS.getOrDefault(sprite.getIconName(),sprite.getIconName()));row.addProperty("source",SOURCES.getOrDefault(sprite.getIconName(),sprite.getIconName()));row.addProperty("aspect",UnifiedTextureSprites.aspect(sprite));row.addProperty("contentWidth",UnifiedTextureSprites.contentWidth(sprite));row.addProperty("contentHeight",UnifiedTextureSprites.contentHeight(sprite));row.addProperty("atlasCanvasWidth",sprite.getIconWidth());row.addProperty("atlasCanvasHeight",sprite.getIconHeight());
            JsonArray vertices=new JsonArray(),colors=new JsonArray();int[] data=quad.getVertexData();int stride=data.length/4;
            for(int i=0;i<4;i++){int offset=i*stride;JsonArray vertex=new JsonArray();for(int axis=0;axis<3;axis++)vertex.add(Float.intBitsToFloat(data[offset+axis]));vertex.add((Float.intBitsToFloat(data[offset+4])-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU()));vertex.add((Float.intBitsToFloat(data[offset+5])-sprite.getMinV())/(sprite.getMaxV()-sprite.getMinV()));vertices.add(vertex);colors.add(data[offset+3]);}
            row.add("vertices",vertices);row.add("colors",colors);out.add(row);
        }
        return out;
    }
    private static JsonArray numbers(int[] values){JsonArray out=new JsonArray();for(int value:values)out.add(value);return out;}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Cube reference: "+message);}
}
