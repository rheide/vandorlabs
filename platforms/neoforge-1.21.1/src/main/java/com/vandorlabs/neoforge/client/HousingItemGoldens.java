package com.vandorlabs.neoforge.client;

import com.google.gson.*;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.trigger.TriggerRegistration;
import com.vandorlabs.render.ControlItemProjection;
import com.vandorlabs.render.ControlMount.Point;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Baseline export deliberately records mismatches instead of asserting the desired legacy behavior. */
public final class HousingItemGoldens {
    private static final List<ItemStack> ITEMS=new ArrayList<>();
    private static int stage,started;
    private static long frame;
    private HousingItemGoldens(){}
    public static boolean tick(Minecraft mc,int tick){
        if(mc.getOverlay()!=null)return false;
        if(stage==0){if(tick<60)return false;export(mc);mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Gallery());started=tick;frame=ModernClientRuntimeChecks.renderedFrames();stage=1;return false;}
        if(stage==1){if(tick-started<20||ModernClientRuntimeChecks.renderedFrames()-frame<4)return false;ModernClientRuntimeChecks.capture("modern-housing-items.png");stage=2;return false;}
        if(stage==2){if(!ModernClientRuntimeChecks.captured("modern-housing-items.png"))return false;mc.setScreen(null);System.out.println("[vandorlabs][modern] housing-items-client PASS28 actual ItemRenderer GUI previews, baseline exported without legacy-expectation assertions");stage=3;}return stage==3;
    }
    private static void export(Minecraft mc){
        int rectangle=-1;for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)if("screen_console_inputs_analysis_framed_gas_giant_static".equals(ModernFinishCatalog.entry(i).id()))rectangle=i;
        need(rectangle>=0,"rectangular catalog choice");JsonArray cases=new JsonArray();ITEMS.clear();
        for(boolean trigger:new boolean[]{false,true}){Item item=trigger?TriggerRegistration.BLOCK.get().asItem():VandorLabsModern.PROGRAMMABLE_BLOCK.get().asItem();String family=trigger?"trigger":"cube";
            cases.add(record(mc,family+"/default",new ItemStack(item),0,-1,false,false));cases.add(record(mc,family+"/unversioned15",configured(item,15,1,false,false),15,1,false,false));
            for(int material:new int[]{1,rectangle})for(int layout=0;layout<3;layout++)for(boolean faces:new boolean[]{false,true})cases.add(record(mc,family+"/"+material+"/"+layout+"/"+(faces?1:0),configured(item,material,layout,faces,true),material,layout,faces,true));
        }
        need(cases.size()==28,"case coverage");JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("rectangularChoice",rectangle);root.add("cases",cases);
        try{var path=mc.gameDirectory.toPath().resolve("screenshots/modern-housing-item-goldens.json");Files.createDirectories(path.getParent());Files.writeString(path,new Gson().toJson(root),StandardCharsets.UTF_8);}catch(Exception failure){throw new IllegalStateException("Housing item native export",failure);}
        System.out.println("[vandorlabs][modern] housing-items-model PASS28 actual ItemRenderer override models, quads/UV/materials/transforms and unchanged payloads exported");
    }
    private static ItemStack configured(Item item,int main,int layout,boolean faces,boolean versioned){
        ItemStack stack=new ItemStack(item);CompoundTag tag=new CompoundTag();tag.putInt("housingTexture",main);if(versioned)tag.putInt("HousingTextureVersion",1);tag.putInt("MainTextureLayout",layout);tag.putBoolean("FaceTexturesEnabled",faces);tag.putIntArray("FaceTextures",new int[]{2,3,4,5,6,7});tag.putInt("TriggerOnTexture",19);tag.putInt("TriggerLowTexture",20);tag.putInt("TriggerMediumTexture",21);tag.putBoolean("TriggerLevelStates",true);tag.putInt("TriggerExactLevel",0);tag.putInt("ChannelSignal",15);tag.putString("OpaquePreviewMarker","retained");CompoundTag opaque=new CompoundTag();opaque.putInt("Nested",42);tag.put("OpaquePreviewData",opaque);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));return stack;
    }
    private static JsonObject record(Minecraft mc,String key,ItemStack stack,int main,int layout,boolean faces,boolean versioned){
        ITEMS.add(stack);CustomData original=stack.get(DataComponents.BLOCK_ENTITY_DATA);CompoundTag before=original==null?null:original.copyTag();var model=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("main",main);row.addProperty("layout",layout);row.addProperty("faces",faces);row.addProperty("versioned",versioned);row.addProperty("ao",model.useAmbientOcclusion());row.addProperty("gui3d",model.isGui3d());row.addProperty("modelClass",model.getClass().getName());var gui=model.getTransforms().gui;JsonArray transform=new JsonArray();for(double value:new double[]{gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.translation.x,gui.translation.y,gui.translation.z,gui.scale.x,gui.scale.y,gui.scale.z})transform.add(value);row.add("gui",transform);
        double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;JsonArray quads=new JsonArray();
        for(var pass:model.getRenderPasses(stack,false))for(Direction bucket:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(BakedQuad quad:pass.getQuads(null,bucket,RandomSource.create(0))){
            String sprite=quad.getSprite().contents().name().toString(),material="unmapped:"+sprite;float usedU=1,usedV=1;for(var entry:ModernFinishCatalog.ENTRIES)if(entry.texture().equals(sprite)){material=ModernFinishCatalog.entry(entry.canonical()).id();usedU=entry.u();usedV=entry.v();break;}
            JsonObject q=new JsonObject();q.addProperty("bucket",bucket==null?-1:bucket.get3DDataValue());q.addProperty("face",quad.getDirection().get3DDataValue());q.addProperty("shade",quad.isShade());q.addProperty("tint",quad.getTintIndex());q.addProperty("sprite",sprite);q.addProperty("material",material);JsonArray vertices=new JsonArray();int[] values=quad.getVertices();int stride=values.length/4;
            for(int i=0;i<4;i++){int at=i*stride;double x=Float.intBitsToFloat(values[at]),y=Float.intBitsToFloat(values[at+1]),z=Float.intBitsToFloat(values[at+2]);JsonArray vertex=new JsonArray();vertex.add(x);vertex.add(y);vertex.add(z);vertex.add((Float.intBitsToFloat(values[at+4])-quad.getSprite().getU0())/(quad.getSprite().getU1()-quad.getSprite().getU0())/usedU);vertex.add((Float.intBitsToFloat(values[at+5])-quad.getSprite().getV0())/(quad.getSprite().getV1()-quad.getSprite().getV0())/usedV);vertices.add(vertex);Point point=ControlItemProjection.project(new Point(x,y,z),gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.scale.x,gui.scale.y,gui.scale.z);x0=Math.min(x0,point.x+gui.translation.x);x1=Math.max(x1,point.x+gui.translation.x);y0=Math.min(y0,point.y+gui.translation.y);y1=Math.max(y1,point.y+gui.translation.y);}
            q.add("vertices",vertices);quads.add(q);
        }
        need(quads.size()>0,"empty actual model "+key);CustomData after=stack.get(DataComponents.BLOCK_ENTITY_DATA);need(Objects.equals(before,after==null?null:after.copyTag()),"preview mutated payload "+key);row.addProperty("payloadUnchanged",true);row.add("quads",quads);JsonArray bounds=new JsonArray();for(double value:new double[]{x0,y0,x1,y1})bounds.add(value);row.add("projectedBounds",bounds);return row;
    }
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Housing item native: "+message);}
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Configured cube and trigger items"));}
        @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial){}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){graphics.fill(0,0,width,height,0xFF101012);int left=(width-7*22)/2,top=(height-4*22)/2;graphics.drawCenteredString(font,title,width/2,top-16,0xFFFFFF);for(int i=0;i<ITEMS.size();i++){int cx=left+i%7*22,cy=top+i/7*22;graphics.fill(cx,cy,cx+22,cy+22,0xFF282C32);graphics.renderItem(ITEMS.get(i),cx+3,cy+3);}super.render(graphics,x,y,partial);}
    }
}
