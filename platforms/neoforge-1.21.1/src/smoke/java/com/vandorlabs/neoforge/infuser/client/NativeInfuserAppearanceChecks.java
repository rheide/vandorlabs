package com.vandorlabs.neoforge.infuser.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.infuser.*;
import com.vandorlabs.ui.DialogStyle;
import com.vandorlabs.ui.InfuserDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;

/** Actual atlas and GUI pixels, tied to the released Infuser source artwork manifest. */
public final class NativeInfuserAppearanceChecks {
    private static void need(boolean value,String why){if(!value)throw new AssertionError("Infuser appearance: "+why);}
    public static void verify()throws Exception{
        var mc=Minecraft.getInstance();int faces=0;
        for(var front:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
            var state=NativeInfuserBindings.BLOCK.defaultBlockState().setValue(NativeInfuserBlock.FACING,front);var model=mc.getBlockRenderer().getBlockModel(state);
            need(model!=mc.getModelManager().getMissingModel(),"registered Infuser model "+front);
            for(var side:Direction.values()){
                var quads=model.getQuads(state,side,RandomSource.create(0));need(quads.size()==1,"one opaque native face "+front+"/"+side);
                var sprite=quads.getFirst().getSprite();String name=side==front?"front":side==Direction.UP?"lens":"casing";
                need(sprite.contents().name().toString().equals("vandorlabs:block/native_infuser/"+name),"source face orientation "+front+"/"+side);
                need(sprite.contents().width()==64&&sprite.contents().height()==64,"exact source resolution "+name);faces++;
            }
        }
        var id=ResourceLocation.parse("vandorlabs:data/native_infuser.json");
        try(var reader=new InputStreamReader(mc.getResourceManager().getResourceOrThrow(id).open(),StandardCharsets.UTF_8)){
            var textures=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("textures");
            for(var entry:textures.entrySet()){
                var texture=ResourceLocation.parse(entry.getKey());var path=ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),"textures/"+texture.getPath()+".png");
                byte[] pixels;try(var input=mc.getResourceManager().getResourceOrThrow(path).open()){pixels=input.readAllBytes();}
                need(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels)).equals(entry.getValue().getAsJsonObject().get("sha256").getAsString()),"live resource source-byte parity "+path);
            }
        }
        var block=mc.getItemRenderer().getModel(new ItemStack(NativeInfuserBindings.BLOCK),mc.level,mc.player,0);var blockScale=block.getTransforms().getTransform(ItemDisplayContext.GUI).scale;
        need(blockScale.x<=.5F&&blockScale.y<=.5F&&blockScale.z<=.5F,"3D block icon retains source padding");
        var core=mc.getItemRenderer().getModel(new ItemStack(NativeInfuserBindings.CORE),mc.level,mc.player,0);var coreScale=core.getTransforms().getTransform(ItemDisplayContext.GUI).scale;
        need(coreScale.x<1&&coreScale.y<1,"flat Core icon leaves visible slot padding");
        System.out.println("[vandorlabs][infuser-appearance] PASS faces="+faces+" exactSourcePNGBytes64px lensUp/front4facings paddedBlockAndCoreModels");
    }
    public static void verifyGuiPixels(NativeImage screenshot,NativeInfuserScreen screen)throws Exception{
        var mc=Minecraft.getInstance();need(screen.controlsFit(),"actual standard-mod viewport contains every slot and label");
        for(int y:new int[]{4,14,25})pixel(screenshot,screen,4,y,InfuserDialog.STYLE.header,"standard header");
        for(int y:new int[]{50,100,204})pixel(screenshot,screen,4,y,InfuserDialog.STYLE.body,"standard body");
        int slots=0;
        for(var slot:screen.getMenu().slots){
            pixel(screenshot,screen,slot.x-1,slot.y-1,InfuserDialog.FRAME,"slot upper border "+slots);
            pixel(screenshot,screen,slot.x+16,slot.y+16,InfuserDialog.FRAME,"slot lower border "+slots);slots++;
        }
        need(slots==39,"three machine slots plus36player slots");
        int x=InfuserDialog.PROGRESS_X,y=InfuserDialog.PROGRESS_Y,fill=screen.progressPixels();
        pixel(screenshot,screen,x-1,y-1,InfuserDialog.FRAME,"progress border");
        for(int column=0;column<InfuserDialog.PROGRESS_WIDTH;column+=3)pixel(screenshot,screen,x+column,y+InfuserDialog.PROGRESS_HEIGHT/2,column<fill?InfuserDialog.PROGRESS:InfuserDialog.SLOT,"authoritative progress fill "+column);
        // The former furnace flame location is clear panel artwork, including while catalyst burns.
        for(int py=48;py<52;py++)for(int px=56;px<70;px+=2)pixel(screenshot,screen,px,py,InfuserDialog.STYLE.body,"no legacy flame sprite");
        int h=DialogStyle.logoHeight(InfuserDialog.STYLE.logoHeaderHeight),w=DialogStyle.logoWidth(InfuserDialog.STYLE.logoHeaderHeight),left=DialogStyle.logoLeft(0,InfuserDialog.WIDTH,InfuserDialog.STYLE.logoHeaderHeight),top=DialogStyle.logoTop(0,InfuserDialog.STYLE.logoHeaderHeight),logoPixels=0;
        double scale=mc.getWindow().getGuiScale();
        try(var input=mc.getResourceManager().getResourceOrThrow(ResourceLocation.parse(DialogStyle.LOGO)).open();var source=NativeImage.read(input)){
            for(int ly=0;ly<h;ly+=2)for(int lx=0;lx<w;lx+=2){
                int px=(int)Math.floor((screen.left()+left+lx+.5)*scale),py=(int)Math.floor((screen.top()+top+ly+.5)*scale);
                double u=((px+.5)/scale-screen.left()-left)/w,v=((py+.5)/scale-screen.top()-top)/h;
                int expected=source.getPixelRGBA(Math.clamp((int)(u*source.getWidth()),0,source.getWidth()-1),Math.clamp((int)(v*source.getHeight()),0,source.getHeight()-1));
                if((expected>>>24)<250)continue;need((screenshot.getPixelRGBA(px,py)&0xffffff)==(expected&0xffffff),"exact Source logo pixel "+lx+","+ly);logoPixels++;
            }
        }
        need(logoPixels>12,"opaque Source logo samples");
        System.out.println("[vandorlabs][infuser-gui-pixels] PASS standardHeader/SourceLogoPixels="+logoPixels+" slots="+slots+" progressPixels="+fill+" noFurnaceFlames");
    }
    private static void pixel(NativeImage image,NativeInfuserScreen screen,int x,int y,int argb,String why){
        double scale=Minecraft.getInstance().getWindow().getGuiScale();int px=(int)Math.floor((screen.left()+x+.5)*scale),py=(int)Math.floor((screen.top()+y+.5)*scale);
        need(px>=0&&py>=0&&px<image.getWidth()&&py<image.getHeight(),"visible source pixel "+why);
        int expected=(argb>>>16&255)|(argb&0xff00)|((argb&255)<<16),actual=image.getPixelRGBA(px,py)&0xffffff;
        need(actual==expected,why+" at"+x+","+y+" actual="+Integer.toHexString(actual)+" expected="+Integer.toHexString(expected));
    }
    private NativeInfuserAppearanceChecks(){}
}
