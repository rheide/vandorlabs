package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.neoforge.FilesystemMaterials;
import com.vandorlabs.neoforge.FilesystemRuntimeChecks;
import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.screen.ProgrammableScreenEntity;
import com.vandorlabs.neoforge.screen.ScreenClient;
import com.vandorlabs.neoforge.screen.ScreenRenderer;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import com.vandorlabs.ui.ScreenSelectorDialog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual screen selection and emitted housing/surface UVs through an open-picker reload. */
public final class ScreenFilesystemClientChecks {
    private static final BlockPos POS=new BlockPos(4,80,0);
    private static int stage,started;
    private static boolean initialFlight;
    private static long clearedFrame=-1;
    private static Path source;
    private static CompletableFuture<Void> reload;
    private static ScreenClient.Selector openPicker;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Modern client: screen filesystem "+message);}
    private static Object field(Object owner,String name){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static ProgrammableScreenEntity tile(Minecraft mc){return (ProgrammableScreenEntity)mc.level.getBlockEntity(POS);}
    private static void control(ScreenClient.Selector gui,int id,int button){
        var panel=(ProgrammableDialogLayout)field(gui,"panel");var state=(ScreenSelectorDialog.State)field(gui,"state");var profile=(ScreenSelectorDialog.Profile)field(gui,"profile");
        var b=ScreenSelectorDialog.controls(panel,state,profile,key->key).stream().filter(c->c.bounds.id==id).findFirst().orElseThrow().bounds;
        gui.mouseClicked(b.x+b.width/2D,b.y+b.height/2D,button);
    }
    private static void choose(ScreenClient.Selector gui){
        var panel=(ProgrammableDialogLayout)field(gui,"panel");var picker=(NativeMaterialPicker)field(gui,"picker");
        picker.setSelected(FilesystemRuntimeChecks.CHOICE);int row=picker.rows().indexOf(FilesystemRuntimeChecks.CHOICE)-picker.scroll();
        require(row>=0&&row<panel.rows(true),"external high-ID row not visible");
        require(gui.mouseClicked(panel.listX+12,panel.top+54+row*22+11,0),"actual external row click denied");
    }
    private static void open(Minecraft mc){
        mc.player.setShiftKeyDown(true);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(POS).add(0,0,-.5),Direction.NORTH,POS,false));
    }
    private static boolean captured(String suffix){return ModernClientRuntimeChecks.captured("modern-screen-filesystem-"+suffix+".png");}
    private static void capture(String suffix){ModernClientRuntimeChecks.capture("modern-screen-filesystem-"+suffix+".png");}
    private static void reload(Minecraft mc,int tick){clearedFrame=-1;reload=mc.reloadResourcePacks();started=tick;}
    private static boolean loaded(){
        if(!reload.isDone())return false;reload.join();if(Minecraft.getInstance().getOverlay()!=null){clearedFrame=-1;return false;}
        long frame=ModernClientRuntimeChecks.renderedFrames();if(clearedFrame<0){clearedFrame=frame;return false;}return frame>clearedFrame;
    }
    private static final class Vertices implements VertexConsumer {
        int count;float maxU,maxV;
        public VertexConsumer addVertex(float x,float y,float z){require(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"invalid emitted position");count++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){require(Float.isFinite(u)&&Float.isFinite(v),"invalid emitted UV");maxU=Math.max(maxU,u);maxV=Math.max(maxV,v);return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    private static void verify(Minecraft mc,float u,float v,int canvas,boolean missing)throws java.io.IOException {
        var tile=tile(mc);require(tile.surface()==FilesystemRuntimeChecks.CHOICE&&tile.data().housingTexture==FilesystemRuntimeChecks.CHOICE,"high-ID surface/housing lost in server roundtrip");
        var descriptor=ModernFinishCatalog.entry(FilesystemRuntimeChecks.CHOICE);require(descriptor.u()==u&&descriptor.v()==v,"reload dimensions stale");
        var resource=ResourceLocation.parse(descriptor.texture()).withPath("textures/"+ResourceLocation.parse(descriptor.texture()).getPath()+".png");
        try(var stream=mc.getResourceManager().open(resource);var pixels=NativeImage.read(stream)){
            require(pixels.getWidth()==canvas&&pixels.getHeight()==canvas,"native direct texture canvas mismatch");
            int pixel=pixels.getPixelRGBA(0,0);int r=pixel&255,g=pixel>>>8&255,b=pixel>>>16&255;
            require(missing?r==50&&g==57&&b==68:r==224&&g==76&&b==50,"direct renderer texture pixels/fallback differ");
        }
        var batches=new ArrayList<Vertices>();var expected=RenderType.entityCutoutNoCull(resource);
        new ScreenRenderer().render(tile,0,new PoseStack(),type->{require(type==expected,"renderer bound wrong external resource");var out=new Vertices();batches.add(out);return out;},15728880,0);
        require(batches.size()==2&&batches.get(0).count==24&&batches.get(1).count==4,"actual housing/surface vertices missing");
        for(var out:batches)require(Math.abs(out.maxU-u)<.001&&Math.abs(out.maxV-v)<.001,"actual housing/surface UVs retained stale dimensions");
        var saved=tile.saveWithoutMetadata(mc.level.registryAccess());require(saved.getInt("PrimarySurfaceTexture")==FilesystemRuntimeChecks.CHOICE&&saved.getInt("housingTexture")==FilesystemRuntimeChecks.CHOICE,"external IDs lost in native save");
    }
    public static boolean tick(Minecraft mc,int tick){
        if(stage==0){started=tick;initialFlight=mc.player.getAbilities().flying;source=FilesystemMaterials.catalog().file(FilesystemRuntimeChecks.SOURCE);mc.player.closeContainer();mc.options.guiScale().set(2);mc.resizeDisplay();mc.player.setPos(4.5,80,-3);Vec3 delta=Vec3.atCenterOf(POS).subtract(mc.player.getEyePosition());float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));mc.player.setYRot(0);mc.player.setXRot(pitch);mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(4.5,80,-3,0,pitch,true));stage=1;return false;}
        require(tick-started<1200,"reload/menu timed out at stage "+stage+" surface="+tile(mc).surface()+" housing="+tile(mc).data().housingTexture+" sides="+tile(mc).sides());
        try{
            if(stage==1){if(tick-started<10)return false;open(mc);stage=2;started=tick;return false;}
            if(stage==2){if(!(mc.screen instanceof ScreenClient.Selector gui))return false;
                control(gui,90,0);control(gui,2,0);
                var settings=(ScreenSelectorDialog.State)field(gui,"state");
                while(settings.sides!=0){control(gui,32,1);settings=(ScreenSelectorDialog.State)field(gui,"state");}
                while(settings.trigger!=0){control(gui,0,0);settings=(ScreenSelectorDialog.State)field(gui,"state");}
                choose(gui);stage=3;started=tick;return false;}
            if(stage==3){if(tile(mc).surface()!=FilesystemRuntimeChecks.CHOICE||tile(mc).sides()!=0)return false;var gui=(ScreenClient.Selector)mc.screen;control(gui,91,0);choose(gui);stage=4;started=tick;return false;}
            if(stage==4){if(tile(mc).data().housingTexture!=FilesystemRuntimeChecks.CHOICE)return false;openPicker=(ScreenClient.Selector)mc.screen;verify(mc,.5f,1,64,false);capture("picker-portrait");stage=5;return false;}
            if(stage==5){if(!captured("picker-portrait"))return false;mc.player.closeContainer();stage=6;started=tick;return false;}
            if(stage==6){if(tick-started<10)return false;capture("world-portrait");stage=7;return false;}
            if(stage==7){if(!captured("world-portrait"))return false;open(mc);stage=8;started=tick;return false;}
            if(stage==8){if(!(mc.screen instanceof ScreenClient.Selector gui))return false;control(gui,91,0);openPicker=gui;Files.delete(source);reload(mc,tick);stage=9;return false;}
            if(stage==9){if(!loaded())return false;require(mc.screen==openPicker,"missing-file reload replaced open selector");verify(mc,1,1,16,true);capture("picker-missing");stage=10;return false;}
            if(stage==10){if(!captured("picker-missing"))return false;FilesystemRuntimeChecks.write(source,64,16);reload(mc,tick);stage=11;return false;}
            if(stage==11){if(!loaded())return false;require(mc.screen==openPicker,"dimension reload replaced open selector");verify(mc,1,.25f,64,false);capture("picker-wide");stage=12;return false;}
            if(stage==12){if(!captured("picker-wide"))return false;mc.player.closeContainer();stage=13;started=tick;return false;}
            if(stage==13){if(tick-started<10)return false;capture("world-wide");stage=14;return false;}
            if(stage==14){if(!captured("world-wide"))return false;FilesystemRuntimeChecks.write(source,32,64);reload(mc,tick);stage=15;return false;}
            if(stage==15){if(!loaded())return false;verify(mc,.5f,1,64,false);mc.player.setShiftKeyDown(false);mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));stage=16;started=tick;System.out.println("[vandorlabs][modern] screen-filesystem-client PASS actual high-ID selector clicks/server roundtrip, emitted housing/surface UVs, native resource pixels, missing-file and dimension reload in the same open picker");}
            if(stage==16&&tick-started>=10){
                require(!mc.player.isShiftKeyDown()&&!mc.player.isCrouching(),"crouch state leaked to the next suite");
                require(mc.screen==null&&mc.player.containerMenu==mc.player.inventoryMenu,"menu leaked to the next suite");
                require(mc.options.guiScale().get()==2&&mc.player.getAbilities().flying==initialFlight,"GUI scale or flight state leaked");
                require(mc.player.distanceToSqr(4.5,80,-3)<.01,"unexpected exit position");
                return true;
            }
            return false;
        }catch(java.io.IOException error){throw new IllegalStateException("Modern client: screen filesystem fixture IO",error);}
    }
    private ScreenFilesystemClientChecks(){}
}
