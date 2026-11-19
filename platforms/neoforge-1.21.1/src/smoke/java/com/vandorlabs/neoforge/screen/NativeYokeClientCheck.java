package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.client.NativeScreenScreen;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import static com.vandorlabs.neoforge.screen.NativeYokeFixture.*;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeYokeClientCheck {
    private static int stage,ticks,wait;
    private static String screenshot;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!enabled())return;
        var game=Minecraft.getInstance();
        var player=game.player;
        if(player==null||game.level==null||game.gameMode==null||game.getOverlay()!=null)return;
        check(++ticks<900,"timeout stage="+stage+" pos="+player.position()+" selected="+player.getInventory().selected+
            " hand="+player.getMainHandItem()+" mode="+game.gameMode.getPlayerMode());
        var tile=game.level.getBlockEntity(POS) instanceof NativeScreenEntity yoke?yoke:null;
        switch(stage){
            case 0 -> {
                if(game.gameMode.getPlayerMode()!=GameType.CREATIVE||player.position().distanceToSqr(START)>.01
                    ||!player.getMainHandItem().is(NativeScreenRegistrations.YOKE_ITEM.get()))return;
                click(game,POS.below(),Direction.UP);
                stage++;
            }
            case 1 -> {
                if(tile==null||!tile.yoke())return;
                var state=tile.getBlockState();
                check(state.getValue(NativeScreenBlock.HORIZONTAL_FACING)==Direction.NORTH,"placed the authored facing");
                check(NativeYokeGeometry.face(state,0).height()==64&&NativeYokeGeometry.face(state,1).height()==64,
                    "both shapes use square pixels");
                check(!state.getCollisionShape(game.level,POS).isEmpty(),"authored physical housing");
                select(game,1);
                wait=ticks;stage++;
            }
            case 2 -> {
                if(ticks<wait+6||!player.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;
                click(game,POS,Direction.NORTH);
                stage++;
            }
            case 3 -> {
                if(!(game.screen instanceof NativeScreenScreen screen))return;
                check(screen.getTitle().getString().equals("Yoke and Screen"),"localized registered name");
                for(int i=0;i<6;i++)screen.action(590,1);
                check(!screen.button(590).active,"six-row server/client capacity");
                for(int i=0;i<5;i++)screen.mouseScrolled(0,0,0,-1);
                int capacity=Math.max(1,(screen.panel().height-138)/24);
                screen.rowLabel(Math.min(5,capacity-1)).setValue("Last");
                screen.rowChannels(Math.min(5,capacity-1)).setValue(Integer.toString(MAIN));
                screen.action(593,1);
                for(int i=0;i<3;i++)screen.action(590,1);
                check(!screen.button(590).active,"three-row touch capacity");
                screen.rowLabel(2).setValue("Trim");
                screen.rowChannels(2).setValue(Integer.toString(TOUCH));
                screen.action(702,1);
                screenshot="yoke-screen-editor.png";
                stage++;
            }
            case 4 -> {
                if(screenshot!=null||!(game.screen instanceof NativeScreenScreen screen))return;
                screen.action(20,1);
                select(game,2);
                wait=ticks;stage++;
            }
            case 5 -> {
                if(game.screen!=null||tile==null||ticks<wait+6||!player.getMainHandItem().isEmpty()
                    ||player.position().distanceToSqr(CONTROLS)>.01||tile.controls(0).rows().size()!=6||tile.controls(1).rows().size()!=3)return;
                aim(game,tile,0,108.0/128,5.5/6);
                wait=ticks;stage++;
            }
            case 6 -> {
                if(ticks<wait+8)return;
                clickSurface(game);
                stage++;
            }
            case 7 -> {
                if(tile==null||!tile.controls(0).rows().get(5).active(game.level.getGameTime()))return;
                aim(game,tile,1,98.0/128,2.5/3);
                wait=ticks;stage++;
            }
            case 8 -> {
                if(ticks<wait+8)return;
                clickSurface(game);
                stage++;
            }
            case 9 -> {
                if(tile==null||tile.controls(1).rows().get(2).level()!=10)return;
                screenshot="yoke-screen-energized.png";
                player.setShiftKeyDown(true);
                game.options.keyShift.setDown(true);
                wait=ticks;stage++;
            }
            case 10 -> {
                if(ticks<wait+8||screenshot!=null)return;
                clickSurface(game);
                stage++;
            }
            case 11 -> {
                if(!(game.screen instanceof NativeScreenScreen))return;
                game.options.keyShift.setDown(false);
                System.out.println("[vandorlabs][yoke-screen] client PASS");
                game.stop();
                stage++;
            }
        }
    }

    private static void select(Minecraft game,int slot){
        game.player.getInventory().selected=slot;
        game.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static void click(Minecraft game,BlockPos pos,Direction facing){
        var hit=Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(.5));
        game.gameMode.useItemOn(game.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,facing,pos,false));
    }

    private static void aim(Minecraft game,NativeScreenEntity tile,int slot,double x,double y){
        var face=NativeYokeGeometry.face(tile.getBlockState(),slot);
        Vec3 target=Vec3.atLowerCornerOf(POS).add(face.topLeft()).add(face.right().scale(x)).add(face.down().scale(y));
        Vec3 delta=target.subtract(game.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        game.player.setYRot(yaw);
        game.player.setXRot(pitch);
        game.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw,pitch,game.player.onGround()));
    }

    private static void clickSurface(Minecraft game){
        var eye=game.player.getEyePosition();
        var hit=game.level.clip(new ClipContext(eye,eye.add(game.player.getViewVector(1).scale(4)),
            ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,game.player));
        check(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(POS),"real held-view ray hits the authored Yoke");
        game.gameMode.useItemOn(game.player,InteractionHand.MAIN_HAND,hit);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) throws Exception {
        if(!enabled()||screenshot==null)return;
        var game=Minecraft.getInstance();
        var path=game.gameDirectory.toPath().resolve("screenshots/"+screenshot);
        try(var image=Screenshot.takeScreenshot(game.getMainRenderTarget())){
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
        }finally{screenshot=null;}
    }
}
