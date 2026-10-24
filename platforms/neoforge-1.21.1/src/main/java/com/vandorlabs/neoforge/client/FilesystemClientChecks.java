package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.FilesystemMaterials;
import com.vandorlabs.neoforge.FilesystemRuntimeChecks;
import com.vandorlabs.neoforge.ModernClientRuntimeChecks;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ProgrammableBlock;
import com.vandorlabs.neoforge.ProgrammableBlockEntity;
import com.vandorlabs.neoforge.ProgrammableMenu;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.ProgrammableDialogLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real native pack reloads preserve saved IDs and update an already-open picker. */
public final class FilesystemClientChecks {
    private static int stage,started;
    private static boolean initialized;
    private static long clearedFrame=-1;
    private static CompletableFuture<Void> reload;
    private static Screen openPicker;
    private static BakedModel originalModel;
    private static Path source;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Filesystem client: "+message);}
    private static ProgrammableBlockEntity tile(Minecraft mc){return (ProgrammableBlockEntity)mc.level.getBlockEntity(FilesystemRuntimeChecks.POS);}
    private static Object field(Object owner,String name){try{var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void verifyGeometry(Minecraft mc,BakedModel model,float expectedU,float expectedV,int canvas,boolean missing){
        var tile=tile(mc);require(tile!=null&&tile.settings().mainTexture==FilesystemRuntimeChecks.CHOICE,"saved external choice changed");
        var quads=model.getQuads(tile.getBlockState(),Direction.NORTH,RandomSource.create(0),tile.getModelData(),null);
        require(quads.size()==1,"expected full native face");var quad=quads.get(0);var sprite=quad.getSprite();
        require(sprite.contents().name().toString().equals("vandorlabs:filesystem/file_"+Integer.toHexString(FilesystemRuntimeChecks.CHOICE)),"filesystem sprite missing from actual atlas");
        require(sprite.contents().width()==canvas&&sprite.contents().height()==canvas,"padded sprite dimensions");
        float maxU=0,maxV=0;int[] vertices=quad.getVertices();int stride=vertices.length/4;
        for(int vertex=0;vertex<4;vertex++){maxU=Math.max(maxU,sprite.getUOffset(Float.intBitsToFloat(vertices[vertex*stride+4])));maxV=Math.max(maxV,sprite.getVOffset(Float.intBitsToFloat(vertices[vertex*stride+5])));}
        require(Math.abs(maxU-expectedU)<.005&&Math.abs(maxV-expectedV)<.005,"immutable quad/sprite used UVs: "+maxU+","+maxV);
        int color=sprite.contents().getOriginalImage().getPixelRGBA(0,0);
        if(missing)require(FastColor.ABGR32.red(color)==0x32&&FastColor.ABGR32.green(color)==0x39&&FastColor.ABGR32.blue(color)==0x44,"legacy missing-file fallback appearance");
        else require(FastColor.ABGR32.red(color)==0xe0&&FastColor.ABGR32.green(color)==0x4c&&FastColor.ABGR32.blue(color)==0x32,"restored authored pixels");
    }
    private static BakedModel model(Minecraft mc){return mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(FilesystemRuntimeChecks.POS));}
    private static boolean loaded(int tick){
        if(!reload.isDone())return false;reload.join();
        if(Minecraft.getInstance().getOverlay()!=null){clearedFrame=-1;return false;}
        long frame=ModernClientRuntimeChecks.renderedFrames();
        if(clearedFrame<0){clearedFrame=frame;return false;}
        return frame>clearedFrame&&tick-started>=20;
    }
    private static void startReload(Minecraft mc,int tick){clearedFrame=-1;reload=mc.reloadResourcePacks();started=tick;}
    public static boolean tick(Minecraft mc,int tick){
        if(!initialized){started=tick;initialized=true;}
        require(tick-started<1000,"native reload checks timed out at stage "+stage);
        try{
            if(stage==0){
                if(tile(mc)==null)return false;
                source=FilesystemMaterials.catalog().file(FilesystemRuntimeChecks.SOURCE);originalModel=model(mc);
                verifyGeometry(mc,originalModel,.5f,1,64,false);
                mc.player.setPos(-1.5,81,-3);mc.player.setYRot(0);mc.player.setXRot(31);
                mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(-1.5,81,-3,0,31,true));
                stage=11;started=tick;return false;
            }
            if(stage==11){
                if(tick-started<10)return false;Vec3 direction=Vec3.atCenterOf(FilesystemRuntimeChecks.POS).subtract(mc.player.getEyePosition());
                mc.player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z))));
                ModernClientRuntimeChecks.capture("modern-filesystem-world.png");stage=12;started=tick;return false;
            }
            if(stage==12){
                if(tick-started<10)return false;
                mc.player.setShiftKeyDown(true);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(FilesystemRuntimeChecks.POS).add(0,0,-.5),Direction.NORTH,FilesystemRuntimeChecks.POS,false));
                stage=1;started=tick;return false;
            }
            if(stage==1){
                if(!(mc.screen instanceof MenuAccess<?> access)||!(access.getMenu() instanceof ProgrammableMenu))return false;
                openPicker=mc.screen;
                NativeMaterialPicker picker=(NativeMaterialPicker)field(openPicker,"picker");
                require(picker.selected()==FilesystemRuntimeChecks.CHOICE&&picker.rows().contains(FilesystemRuntimeChecks.CHOICE),"actual filesystem picker selection/group");
                var panel=(ProgrammableDialogLayout)field(openPicker,"panel");var state=(ProgrammableBlockDialog.State)field(openPicker,"state");
                var layout=ProgrammableBlockDialog.controls(panel,state).stream().filter(control->control.bounds.id==ProgrammableBlockDialog.TEXTURE_LAYOUT).findFirst().orElseThrow().bounds;
                mc.screen.mouseClicked(layout.x+3,layout.y+3,1);mc.screen.mouseClicked(layout.x+3,layout.y+3,0);
                ModernClientRuntimeChecks.capture("modern-filesystem-picker-portrait.png");stage=2;started=tick;return false;
            }
            if(stage==2){if(tick-started<20||tile(mc).settings().layout!=0)return false;Files.delete(source);startReload(mc,tick);stage=3;return false;}
            if(stage==3){
                if(!loaded(tick))return false;require(mc.screen==openPicker,"resource reload closed the active configuration screen");
                verifyGeometry(mc,model(mc),1,1,16,true);require(ModernFinishCatalog.entry(FilesystemRuntimeChecks.CHOICE).u()==1,"missing source descriptor not refreshed");
                ModernClientRuntimeChecks.capture("modern-filesystem-picker-missing.png");stage=4;started=tick;return false;
            }
            if(stage==4){if(tick-started<15)return false;FilesystemRuntimeChecks.write(source,32,64);startReload(mc,tick);stage=5;return false;}
            if(stage==5){
                if(!loaded(tick))return false;require(mc.screen==openPicker,"restoring source closed the active configuration screen");
                verifyGeometry(mc,model(mc),.5f,1,64,false);stage=6;started=tick;return false;
            }
            if(stage==6){if(tick-started<15)return false;FilesystemRuntimeChecks.write(source,64,16);startReload(mc,tick);stage=7;return false;}
            if(stage==7){
                if(!loaded(tick))return false;require(mc.screen==openPicker,"dimension reload replaced the open picker");
                verifyGeometry(mc,model(mc),1,.25f,64,false);
                var descriptor=ModernFinishCatalog.entry(FilesystemRuntimeChecks.CHOICE);require(descriptor.u()==1&&descriptor.v()==.25f,"changed source dimensions not published");
                // The original model retains its own UV dimensions even after catalog reload.
                var old=originalModel.getQuads(tile(mc).getBlockState(),Direction.NORTH,RandomSource.create(0),tile(mc).getModelData(),null).get(0);
                float oldMaxU=0;int[] data=old.getVertices();for(int v=0;v<4;v++)oldMaxU=Math.max(oldMaxU,old.getSprite().getUOffset(Float.intBitsToFloat(data[v*(data.length/4)+4])));
                require(Math.abs(oldMaxU-.5f)<.005,"old model mixed a new descriptor with old atlas UVs");
                ModernClientRuntimeChecks.capture("modern-filesystem-picker-wide.png");stage=8;started=tick;return false;
            }
            if(stage==8){if(tick-started<15)return false;FilesystemRuntimeChecks.write(source,32,64);startReload(mc,tick);stage=9;return false;}
            if(stage==9){
                if(!loaded(tick))return false;verifyGeometry(mc,model(mc),.5f,1,64,false);
                var saved=tile(mc).saveWithoutMetadata(mc.level.registryAccess());require(saved.getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY)==FilesystemRuntimeChecks.CHOICE,"reload overwrote saved identity");
                mc.player.closeContainer();stage=10;started=tick;
                System.out.println("[vandorlabs][modern] filesystem-client PASS actual native pack, open picker, immutable model UVs, missing-file legacy fallback, restored pixels and portrait/wide reload with stable saved ID");return false;
            }
            return stage==10&&tick-started>=10;
        }catch(java.io.IOException error){throw new IllegalStateException("Filesystem client fixture IO",error);}
    }
    private FilesystemClientChecks(){}
}
