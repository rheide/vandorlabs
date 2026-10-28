package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.nbt.NBTTagCompound;

/** Previous-save defaults and independent finishes exercise the real tile serializers. */
final class FrameAppearanceRuntimeChecks {
    private static boolean done;
    static void run(){if(done)return;done=true;
        int frames=0,lit=0;for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("Frame".equals(ScreenHousingTextures.category(i))){frames++;require(!HousingTextureList.generalTexture(i),"frame leaked into ordinary material picker");FrameAppearance a=new FrameAppearance();a.front=i;if(a.light()>0)lit++;require(a.rail("").length()>0,"missing rail texture");}
        require(frames==58&&lit>0,"frame catalog coverage");
        TileEntityProgrammableGlass glass=new TileEntityProgrammableGlass();glass.readFromNBT(new NBTTagCompound());require(glass.frameAppearance.front==-1&&glass.frameAppearance.interior==-1,"old glass changes appearance");
        int bolted=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");require(bolted>0,"missing legacy bolted frame");
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();NBTTagCompound old=screen.writeToNBT(new NBTTagCompound());old.removeTag("FrameFront");old.setBoolean("framed",true);screen.readFromNBT(old);require(!screen.isFramed()&&screen.frameAppearance.front==bolted,"previous-release framed display migration");
        screen.frameAppearance.interior=bolted;screen.frameAppearance.split=true;screen.frameAppearance.splitTop=0;screen.frameAppearance.splitBottom=1;screen.frameAppearance.displayFit=true;
        TileEntityAnimatedScreenSelector copy=new TileEntityAnimatedScreenSelector();copy.readFromNBT(screen.writeToNBT(new NBTTagCompound()));require(copy.frameAppearance.front==bolted&&copy.frameAppearance.interior==bolted&&copy.frameAppearance.split&&copy.frameAppearance.splitTop==0&&copy.frameAppearance.splitBottom==1&&copy.frameAppearance.displayFit,"frame/split tile persistence");
        com.vandorlabs.network.MessageFrameAppearance packet=new com.vandorlabs.network.MessageFrameAppearance(screen,screen.frameAppearance);io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();packet.toBytes(bytes);require(bytes.readableBytes()==27,"fixed frame packet size");com.vandorlabs.network.MessageFrameAppearance decoded=new com.vandorlabs.network.MessageFrameAppearance();decoded.fromBytes(bytes);io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();decoded.toBytes(round);bytes.readerIndex(0);require(bytes.equals(round),"frame packet roundtrip");bytes.release();round.release();
        for(com.vandorlabs.render.ScreenSurface.Kind kind:com.vandorlabs.render.ScreenSurface.Kind.values()){com.vandorlabs.render.ScreenSurface.Quad full=com.vandorlabs.render.ScreenSurface.quad(kind,false),top=com.vandorlabs.render.ScreenSurface.half(full,true),bottom=com.vandorlabs.render.ScreenSurface.half(full,false);require(top.bottomLeft.y==bottom.topLeft.y&&top.bottomLeft.z==bottom.topLeft.z,"split display gap");require(top==com.vandorlabs.render.ScreenSurface.half(full,true),"split geometry allocates repeatedly");}
        System.out.println("[vandorlabs][reprolab] configurable-frames PASS frames="+frames+" lit="+lit+" legacy-NBT split-NBT fixed-packet split-geometry");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
