package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.render.ControlItemProjection;
import com.vandorlabs.render.ControlMount.Point;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** Actual RenderItem item-mesh selection and final override models, independent of world rendering. */
final class LegacyHousingItemGoldens {
    private static final Map<String,String> MATERIALS=new HashMap<>();
    private static final List<ItemStack> ITEMS=new ArrayList<>();
    private static int stage,wait;
    private LegacyHousingItemGoldens(){}
    static boolean tick(Minecraft mc,File output)throws IOException {
        if(stage==0){run(mc,output);mc.gameSettings.guiScale=2;mc.displayGuiScreen(new Gallery());stage=1;return false;}
        if(++wait<20)return false;
        javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"legacy-housing-items.png"));mc.displayGuiScreen(null);
        System.out.println("[vandorlabs][reprolab] housing-items-gallery PASS28 actual RenderItem GUI previews atscale2");return true;
    }
    static void run(Minecraft mc,File output)throws IOException {
        int rectangle=-1;for(int i=0;i<ScreenHousingTextures.IDS.length;i++){MATERIALS.putIfAbsent(ScreenHousingTextures.texture(i),ScreenHousingTextures.IDS[ScreenHousingTextures.canonicalChoice(i)]);if("screen_console_inputs_analysis_framed_gas_giant_static".equals(ScreenHousingTextures.IDS[i]))rectangle=i;}
        need(rectangle>=0,"true2:1 material");JsonArray cases=new JsonArray();ITEMS.clear();
        for(boolean trigger:new boolean[]{false,true}){
            Block block=trigger?ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK:ModBlocks.PROGRAMMABLE_BLOCK;String family=trigger?"trigger":"cube";
            cases.add(record(mc,family+"/default",new ItemStack(block),0,-1,false,false));
            cases.add(record(mc,family+"/unversioned15",configured(block,15,1,false,false),15,1,false,false));
            for(int material:new int[]{1,rectangle})for(int layout=0;layout<3;layout++){
                JsonObject disabled=record(mc,family+"/"+material+"/"+layout+"/0",configured(block,material,layout,false,true),material,layout,false,true);
                JsonObject enabled=record(mc,family+"/"+material+"/"+layout+"/1",configured(block,material,layout,true,true),material,layout,true,true);
                need(disabled.get("quads").equals(enabled.get("quads")),"dormant face overrides must not alter "+family+" item");cases.add(disabled);cases.add(enabled);
            }
        }
        need(cases.size()==28,"bounded full case coverage");JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("rectangularChoice",rectangle);root.add("cases",cases);
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(output,"legacy-housing-item-goldens.json")),StandardCharsets.UTF_8)){new Gson().toJson(root,writer);}
        System.out.println("[vandorlabs][reprolab] housing-items-goldens PASS actual28 RenderItem overrides, cube/trigger materials/layouts, dormant faces+exact0, raw unversioned previews, transforms and unchanged payloads");
    }
    private static ItemStack configured(Block block,int main,int layout,boolean faces,boolean versioned){
        ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("housingTexture",main);if(versioned)tag.setInteger("HousingTextureVersion",1);tag.setInteger("MainTextureLayout",layout);tag.setBoolean("FaceTexturesEnabled",faces);tag.setIntArray("FaceTextures",new int[]{2,3,4,5,6,7});tag.setInteger("TriggerOnTexture",19);tag.setInteger("TriggerLowTexture",20);tag.setInteger("TriggerMediumTexture",21);tag.setBoolean("TriggerLevelStates",true);tag.setInteger("TriggerExactLevel",0);tag.setInteger("ChannelSignal",15);tag.setString("OpaquePreviewMarker","retained");NBTTagCompound opaque=new NBTTagCompound();opaque.setInteger("Nested",42);tag.setTag("OpaquePreviewData",opaque);stack.setTagInfo("BlockEntityTag",tag);return stack;
    }
    private static JsonObject record(Minecraft mc,String key,ItemStack stack,int main,int layout,boolean faces,boolean versioned){
        ITEMS.add(stack);NBTTagCompound before=stack.hasTagCompound()?stack.getTagCompound().copy():null;IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("main",main);row.addProperty("layout",layout);row.addProperty("faces",faces);row.addProperty("versioned",versioned);row.addProperty("ao",model.isAmbientOcclusion());row.addProperty("gui3d",model.isGui3d());row.addProperty("modelClass",model.getClass().getName());JsonArray quads=new JsonArray();
        ItemTransformVec3f gui=model.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GUI);JsonArray transform=new JsonArray();for(double value:new double[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(value);row.add("gui",transform);
        double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
        for(EnumFacing bucket:new EnumFacing[]{null,EnumFacing.DOWN,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST})for(BakedQuad quad:model.getQuads(null,bucket,0)){
            String material=MATERIALS.get(quad.getSprite().getIconName());need(material!=null,"actual atlas sprite catalog identity");need(material.equals(MATERIALS.get(ScreenHousingTextures.texture(ScreenHousingTextures.clamp(main)))),"item preview must use uniform raw OFF housing choice "+key+" actual="+material);
            JsonObject q=new JsonObject();q.addProperty("bucket",bucket==null?-1:bucket.getIndex());q.addProperty("face",quad.getFace().getIndex());q.addProperty("shade",quad.shouldApplyDiffuseLighting());q.addProperty("tint",quad.getTintIndex());q.addProperty("sprite",quad.getSprite().getIconName());q.addProperty("material",material);JsonArray vertices=new JsonArray();int[] values=quad.getVertexData();int stride=values.length/4;
            for(int i=0;i<4;i++){int offset=i*stride;JsonArray vertex=new JsonArray();double x=Float.intBitsToFloat(values[offset]),y=Float.intBitsToFloat(values[offset+1]),z=Float.intBitsToFloat(values[offset+2]);vertex.add(x);vertex.add(y);vertex.add(z);vertex.add((Float.intBitsToFloat(values[offset+4])-quad.getSprite().getMinU())/(quad.getSprite().getMaxU()-quad.getSprite().getMinU()));vertex.add((Float.intBitsToFloat(values[offset+5])-quad.getSprite().getMinV())/(quad.getSprite().getMaxV()-quad.getSprite().getMinV()));vertices.add(vertex);Point p=ControlItemProjection.project(new Point(x,y,z),gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.scale.x,gui.scale.y,gui.scale.z);x0=Math.min(x0,p.x+gui.translation.x);x1=Math.max(x1,p.x+gui.translation.x);y0=Math.min(y0,p.y+gui.translation.y);y1=Math.max(y1,p.y+gui.translation.y);}
            q.add("vertices",vertices);quads.add(q);
        }
        need(quads.size()==6,"actual six cube item quads "+key);need(Objects.equals(before,stack.getTagCompound()),"RenderItem overrides changed retained item NBT");row.addProperty("payloadUnchanged",true);row.add("quads",quads);JsonArray bounds=new JsonArray();for(double value:new double[]{x0,y0,x1,y1})bounds.add(value);row.add("projectedBounds",bounds);return row;
    }
    private static final class Gallery extends net.minecraft.client.gui.GuiScreen {
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void drawScreen(int mouseX,int mouseY,float partial){
            drawRect(0,0,width,height,0xFF101012);int left=(width-7*22)/2,top=(height-4*22)/2;
            drawCenteredString(fontRenderer,"Configured cube and trigger items",width/2,top-16,0xFFFFFF);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            for(int i=0;i<ITEMS.size();i++){int x=left+i%7*22,y=top+i/7*22;drawRect(x,y,x+22,y+22,0xFF282C32);mc.getRenderItem().renderItemAndEffectIntoGUI(ITEMS.get(i),x+3,y+3);}
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }
    }
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Housing item goldens: "+message);}
}
