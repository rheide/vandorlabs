package com.vandorlabs.neoforge.vehicle.client;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.vehicle.NativeGroundVehicle;
import com.vandorlabs.neoforge.vehicle.NativeVehicleBindings;
import com.vandorlabs.neoforge.vehicle.NativeVehiclePlayerFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeVehiclePlayerChecks {
    private static NativeGroundVehicle craft;
    private static int stage,ticks,frames;
    private static Vec3 start,parking;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!NativeVehiclePlayerFixture.enabled()) return;
        var mc=Minecraft.getInstance();
        if(mc.screen instanceof AccessibilityOnboardingScreen intro){intro.onClose();return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null)return;
        var p=mc.player;
        var root=NativeVehiclePlayerFixture.ROOT;
        var screen=mc.screen instanceof NativeVehicleControllerScreen dialog?dialog:null;
        NativeVehiclePlayerFixture.require(++ticks<1500,"client stage "+stage+" ticks="+ticks+" pos="+p.position()+
                " mode="+mc.gameMode.getPlayerMode()+" slot="+p.getInventory().selected+" hand="+p.getMainHandItem()+
                " screen="+mc.screen+" vehicle="+p.getVehicle()+" craft="+craft);
        frames++;
        switch(stage){
            case 0 -> {
                if(mc.screen!=null || mc.gameMode.getPlayerMode()!=GameType.SURVIVAL || !p.getMainHandItem().isEmpty()
                        || !p.getInventory().getItem(1).is(VandorLabsBootstrap.CONFIGURIZER.get())
                        || !mc.level.getBlockState(root).is(NativeVehicleBindings.CONTROLLER) || !p.onGround())return;
                useBlock(mc,root); next();
            }
            case 1 -> {
                if(screen==null)return;
                p.closeContainer();p.getInventory().selected=1;next();
            }
            case 2 -> {
                if(frames<5||mc.screen!=null||!p.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;
                useBlock(mc,root);next();
            }
            case 3 -> {
                if(screen==null)return;
                click(screen);next();
            }
            case 4 -> {
                if(mc.screen!=null || !mc.level.isEmptyBlock(root))return;
                var found=mc.level.getEntitiesOfClass(NativeGroundVehicle.class,new AABB(root).inflate(6));
                if(found.size()!=1 || !found.get(0).admitted())return;
                craft=found.get(0);
                NativeVehiclePlayerFixture.require(craft.visual()!=null && craft.visual().cells().size()==9,"complete carried hull on client");
                p.getInventory().selected=0;next();
            }
            case 5 -> {
                if(frames<5||!p.getMainHandItem().isEmpty())return;
                var seat=craft.pilotSeat();
                aim(mc,craft.position().add(seat.getX(),seat.getY()+.55,seat.getZ()));
                if(!seat.equals(craft.hit(p)) && !seat.above().equals(craft.hit(p)))return;
                mc.gameMode.interact(p,craft,InteractionHand.MAIN_HAND);next();
            }
            case 6 -> {
                if(p.getVehicle()!=craft)return;
                start=craft.position();mc.options.keyUp.setDown(true);next();
            }
            case 7 -> {
                if(craft.position().subtract(start).horizontalDistanceSqr()<1.7)return;
                mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(true);next();
            }
            case 8 -> {
                if(frames<8||Math.abs(craft.driveSpeed())>.005)return;
                mc.options.keyJump.setDown(false);mc.options.keyShift.setDown(true);next();
            }
            case 9 -> {
                if(p.getVehicle()!=null)return;
                mc.options.keyShift.setDown(false);p.getInventory().selected=1;next();
            }
            case 10 -> {
                if(frames<12||!p.getMainHandItem().is(VandorLabsBootstrap.CONFIGURIZER.get()))return;
                aim(mc,craft.position().add(0,.5,0));
                if(craft.hit(p)==null)return;
                mc.gameMode.interact(p,craft,InteractionHand.MAIN_HAND);next();
            }
            case 11 -> {
                if(screen==null)return;
                NativeVehiclePlayerFixture.require(screen.getMenu().craft==craft,"carried Controller on the selected entity");
                parking=craft.position();click(screen);next();
            }
            case 12 -> {
                if(!craft.isRemoved() || mc.screen!=null)return;
                for(var pos:BlockPos.betweenClosed(BlockPos.containing(parking).offset(-2,-2,-2),BlockPos.containing(parking).offset(2,2,2))){
                    if(mc.level.getBlockState(pos).is(NativeVehicleBindings.CONTROLLER)){
                        System.out.println("[vandorlabs][vehicle-player] client PASS placed and carried Controller Taxi input park");
                        mc.stop();return;
                    }
                }
            }
            default -> throw new AssertionError(stage);
        }
    }
    private static void next(){stage++;frames=0;}
    private static void useBlock(Minecraft mc,BlockPos pos){
        var target=Vec3.atCenterOf(pos).add(0,0,-.5);
        aim(mc,target);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(target,Direction.NORTH,pos,false));
    }
    private static void aim(Minecraft mc,Vec3 target){
        var player=mc.player;var d=target.subtract(player.getEyePosition());
        player.setYRot((float)(Math.atan2(-d.x,d.z)*Mth.RAD_TO_DEG));
        player.setXRot((float)(-Math.atan2(d.y,d.horizontalDistance())*Mth.RAD_TO_DEG));
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(),player.getXRot(),player.onGround()));
    }
    private static void click(NativeVehicleControllerScreen screen){
        var button=screen.actionButton();
        NativeVehiclePlayerFixture.require(screen.mouseClicked(button.getX()+button.getWidth()*.5,button.getY()+10,0),"click real Controller action");
    }
    private NativeVehiclePlayerChecks(){}
}
