package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual Forge shape material/UV/item evidence, independent of the extracted scalar policy. */
final class ShapeTextureRuntimeChecks {
    private static final BlockPos POS=new BlockPos(300,40,146);
    private static final String[] PROFILES={"default","square","wide","portrait","side","overrides","dormant","unavailable"};
    private static final int[] TREADS={1,2,4,8,16};
    private static int square,wide,portrait,worlds,items;
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Shape textures: "+message);}
    private static int aspectChoice(Minecraft mc,double aspect){for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if(ScreenHousingTextures.visible(i)&&(HousingTextureList.generalTexture(i)||HousingTextureList.displayTexture(i))){TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(i));if(sprite!=mc.getTextureMapBlocks().getMissingSprite()&&Math.abs(UnifiedTextureSprites.aspect(sprite)-aspect)<.025)return ScreenHousingTextures.choiceAt(i);}throw new IllegalStateException("Missing shape reference aspect "+aspect);}
    private static int main(int p){return p==1?square:p==2||p==4?wide:p==3||p==6?portrait:p==7?0x40001234:0;}
    private static int side(int p){return p==4?portrait:p==5?wide:p==6?square:p==7?0x40005678:-1;}
    private static int[] faces(){return new int[]{0,square,wide,portrait,2,3};}
    private static NBTTagCompound tag(int p,int layout,int tread,int roll){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("HousingTextureVersion",1);tag.setInteger("housingTexture",main(p));tag.setInteger("SideTexture",side(p));tag.setInteger("MainTextureLayout",layout);tag.setBoolean("SlabTileSides",layout==1);tag.setBoolean("FaceTexturesEnabled",p==5);tag.setIntArray("FaceTextures",p==5||p==6?faces():new int[]{-1,-1,-1,-1,-1,-1});tag.setInteger("StairTread",tread);tag.setInteger("StairRoll",roll);tag.setString("UnknownItemField","retained");return tag;}
    private static JsonArray quads(IBakedModel model,IBlockState state){JsonArray result=BinaryControlReference.quads(model,state);need(result.size()>0,"empty model");for(JsonElement e:result){JsonObject quad=e.getAsJsonObject();need(!quad.get("sprite").getAsString().contains("missingno"),"missing texture "+quad);for(JsonElement point:quad.getAsJsonArray("points"))for(JsonElement value:point.getAsJsonArray())need(Double.isFinite(value.getAsDouble()),"nonfinite vertex/UV");}return result;}
    private static void row(Writer writer,Gson gson,JsonObject row)throws IOException{gson.toJson(row,writer);writer.write('\n');}
    private static void world(Minecraft mc,Writer writer,Gson gson,boolean slab,EnumFacing facing,boolean upper,BlockStairs.EnumShape shape,int tread,int roll,int layout,int profile)throws IOException{
        IBlockState clean=slab?ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,facing).withProperty(BlockProgrammableSlab.HALF,upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM):ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,upper?BlockStairs.EnumHalf.TOP:BlockStairs.EnumHalf.BOTTOM).withProperty(BlockStairs.SHAPE,shape);
        mc.world.setBlockState(POS,clean,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);need(tile!=null,"fixture chunk must be client-loaded");tile.setHousingTexture(main(profile));tile.setSideTexture(side(profile));tile.setMainTextureLayout(layout);tile.setFaceTextures(new FaceTextures(profile==5,profile==5||profile==6?faces():new int[]{-1,-1,-1,-1,-1,-1}));tile.setStairGeometry(tread,roll);
        IBlockState extended=clean.getBlock().getExtendedState(clean,mc.world,POS);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(clean);JsonObject value=new JsonObject();value.addProperty("kind","world");value.addProperty("key",(slab?"slab":"stairs")+"/"+facing.getName()+"/"+upper+"/"+shape.name()+"/"+tread+"/"+roll+"/"+layout+"/"+PROFILES[profile]);value.addProperty("main",main(profile));value.addProperty("side",side(profile));value.addProperty("facesEnabled",profile==5);value.add("faces",gson.toJsonTree(profile==5||profile==6?faces():new int[]{-1,-1,-1,-1,-1,-1}));value.add("quads",quads(model,extended));row(writer,gson,value);worlds++;
    }
    private static void item(Minecraft mc,Writer writer,Gson gson,boolean slab,String key,NBTTagCompound tag)throws IOException{
        ItemStack stack=new ItemStack(slab?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_STAIRS);if(tag!=null)stack.setTagInfo("BlockEntityTag",tag);ItemStack before=stack.copy();IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);JsonObject value=new JsonObject();value.addProperty("kind","item");value.addProperty("key",(slab?"slab":"stairs")+"/"+key);value.addProperty("nbt",tag==null?"<absent>":tag.toString());value.add("quads",quads(model,null));ItemTransformVec3f gui=model.getItemCameraTransforms().gui;JsonArray transform=new JsonArray();for(float v:new float[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(v);value.add("gui",transform);need(ItemStack.areItemStacksEqual(before,stack),"item override changed saved payload");row(writer,gson,value);items++;
    }
    private static NBTTagCompound malformed(String kind){NBTTagCompound tag=tag(4,0,4,1);tag.removeTag("MainTextureLayout");tag.setBoolean("SlabTileSides",false);
        if(kind.startsWith("layout-int")){String v=kind.substring(10);tag.setInteger("MainTextureLayout",v.equals("MIN")?Integer.MIN_VALUE:v.equals("MAX")?Integer.MAX_VALUE:Integer.parseInt(v));}
        else if(kind.equals("layout-byte"))tag.setByte("MainTextureLayout",(byte)2);else if(kind.equals("layout-long"))tag.setLong("MainTextureLayout",2);else if(kind.equals("layout-float"))tag.setFloat("MainTextureLayout",2);else if(kind.equals("layout-string"))tag.setString("MainTextureLayout","2");
        else if(kind.equals("sides-missing"))tag.removeTag("SlabTileSides");else if(kind.equals("sides-byte0"))tag.setBoolean("SlabTileSides",false);else if(kind.equals("sides-byte1"))tag.setBoolean("SlabTileSides",true);else if(kind.equals("sides-int0"))tag.setInteger("SlabTileSides",0);else if(kind.equals("sides-int1"))tag.setInteger("SlabTileSides",1);else if(kind.equals("sides-string"))tag.setString("SlabTileSides","1");
        else if(kind.equals("side-byte"))tag.setByte("SideTexture",(byte)3);else if(kind.equals("side-long"))tag.setLong("SideTexture",3);else if(kind.equals("side-string"))tag.setString("SideTexture","3");
        else if(kind.equals("main-byte"))tag.setByte("housingTexture",(byte)3);else if(kind.equals("main-long"))tag.setLong("housingTexture",wide);else if(kind.equals("main-float"))tag.setFloat("housingTexture",wide+.75F);else if(kind.equals("main-string"))tag.setString("housingTexture","3");else if(kind.equals("unversioned15")){tag.removeTag("HousingTextureVersion");tag.setInteger("housingTexture",15);}else if(kind.equals("faces-short")){tag.setBoolean("FaceTexturesEnabled",true);tag.setIntArray("FaceTextures",new int[]{1,2,3});}else throw new IllegalArgumentException(kind);return tag;
    }
    static void run(Minecraft mc,File output)throws Exception{
        worlds=items=0;square=ScreenHousingTextures.screenIndex("corp/ashiro_logo");wide=aspectChoice(mc,2);portrait=aspectChoice(mc,.5);need(square>0,"canonical square choice");MessageDigest digest=MessageDigest.getInstance("SHA-256");Gson gson=new Gson();boolean export="true".equals(System.getenv("VANDOR_LABS_SHAPE_TEXTURE_EXPORT"));OutputStream target=export?new FileOutputStream(new File(output,"legacy-shape-textures.jsonl")):new OutputStream(){public void write(int value){}public void write(byte[] value,int offset,int length){}};
        try(Writer writer=new BufferedWriter(new OutputStreamWriter(new DigestOutputStream(target,digest),StandardCharsets.UTF_8))){JsonObject header=new JsonObject();header.addProperty("format",1);header.addProperty("source","Forge actual slab/stair world and item models");header.addProperty("square",square);header.addProperty("wide",wide);header.addProperty("portrait",portrait);row(writer,gson,header);
            for(EnumFacing face:EnumFacing.values())for(boolean upper:new boolean[]{false,true})for(int layout=0;layout<3;layout++)for(int profile=0;profile<8;profile++)world(mc,writer,gson,true,face,upper,BlockStairs.EnumShape.STRAIGHT,8,0,layout,profile);
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean upper:new boolean[]{false,true})for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values())for(int roll=0;roll<3;roll++)for(int layout=0;layout<3;layout++)for(int profile=0;profile<8;profile++)world(mc,writer,gson,false,face,upper,shape,4,roll,layout,profile);
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean upper:new boolean[]{false,true})for(BlockStairs.EnumShape shape:BlockStairs.EnumShape.values())for(int layout=0;layout<3;layout++)for(int profile=0;profile<8;profile++)world(mc,writer,gson,false,face,upper,shape,8,0,layout,profile);
            for(EnumFacing face:EnumFacing.HORIZONTALS)for(boolean upper:new boolean[]{false,true})for(int tread:new int[]{1,2,8,16})for(int roll=0;roll<3;roll++)if(tread!=8||roll!=0)for(int layout=0;layout<3;layout++)for(int profile=1;profile<=3;profile++)world(mc,writer,gson,false,face,upper,BlockStairs.EnumShape.STRAIGHT,tread,roll,layout,profile);
            for(boolean slab:new boolean[]{true,false}){item(mc,writer,gson,slab,"no-tag",null);item(mc,writer,gson,slab,"empty-tag",new NBTTagCompound());for(int tread:TREADS){if(slab&&tread!=8)continue;for(int roll=0;roll<(slab?1:3);roll++)for(int layout=0;layout<3;layout++)for(int profile=0;profile<8;profile++)item(mc,writer,gson,slab,tread+"/"+roll+"/"+layout+"/"+PROFILES[profile],tag(profile,layout,tread,roll));}for(String kind:new String[]{"layout-intMIN","layout-int-1","layout-int0","layout-int1","layout-int2","layout-int3","layout-intMAX","layout-byte","layout-long","layout-float","layout-string","sides-missing","sides-byte0","sides-byte1","sides-int0","sides-int1","sides-string","side-byte","side-long","side-string","main-byte","main-long","main-float","main-string","unversioned15","faces-short"})item(mc,writer,gson,slab,kind,malformed(kind));}
        }finally{mc.world.setBlockToAir(POS);}need(worlds==4920&&items==440,"bounded reference coverage "+worlds+"/"+items);StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));String expected=System.getenv("VANDOR_LABS_SHAPE_TEXTURE_EXPECTED_HASH");need(expected==null||expected.equals(hash.toString()),"actual emitted/item reference changed "+hash);System.out.println("[vandorlabs][reprolab] shape-texture-reference PASS world="+worlds+" items="+items+" SHA256="+hash+" square="+square+" wide="+wide+" portrait="+portrait+" actual UVs/materials/transforms and retained inputNBT");
    }
    private ShapeTextureRuntimeChecks(){}
}
