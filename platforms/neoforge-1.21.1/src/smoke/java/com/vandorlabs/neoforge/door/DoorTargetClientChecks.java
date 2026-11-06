package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.client.NativeDoorSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real survival pose, post-pick target and Minecraft.startUseItem path over the actual dedicated connection. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class DoorTargetClientChecks {
    private static int ticks,stage,wait,renderTargets;
    private static void need(boolean pass,String message){if(!pass)throw new IllegalStateException("Door target client stage="+stage+": "+message);}
    private static void click(Minecraft mc){try{var method=Minecraft.class.getDeclaredMethod("startUseItem");method.setAccessible(true);method.invoke(mc);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Real Minecraft use entry",failure);}}
    @SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event){if(!DoorTargetClientFixture.enabled()||stage!=0)return;var mc=Minecraft.getInstance();if(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(DoorTargetClientFixture.POS.above())&&hit.getLocation().z<0)renderTargets++;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!DoorTargetClientFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}
        if(stage==9&&mc.level==null){System.out.println("[vandorlabs][door-target-client] client PASS actual survival moved upper-half hover/key/9byte intent/occlusion; Door body rendering separate");mc.stop();stage=10;return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null)return;
        need(++ticks<1500,"timeout mode="+mc.gameMode.getPlayerMode()+" slot="+mc.player.getInventory().selected+" held="+mc.player.getMainHandItem()+" pos="+mc.player.position()+" hit="+mc.hitResult);
        if(!(mc.level.getBlockEntity(DoorTargetClientFixture.POS) instanceof NativeProgrammableDoorEntity))return;var state=mc.level.getBlockState(DoorTargetClientFixture.POS);
        if(stage==0){if(ticks<80||mc.gameMode.getPlayerMode()!=net.minecraft.world.level.GameType.SURVIVAL||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().isEmpty()||Math.abs(mc.player.getYRot()+90)>.01||mc.player.getZ()>=0)return;
            if(renderTargets<8)return;var target=NativeDoorSelection.update(1);need(target!=null&&target.root().equals(DoorTargetClientFixture.POS)&&target.hit().getBlockPos().equals(DoorTargetClientFixture.POS.above())&&target.hit().getLocation().z<0,"actual moved upper leaf outside vanilla voxel ray");click(mc);stage=1;wait=ticks;
        }else if(stage==1){if(state.getValue(NativeProgrammableDoorBlock.OPEN))return;PacketDistributor.sendToServer(new DoorTargetClientFixture.Mode(1));stage=2;wait=ticks;
        }else if(stage==2){if(!state.getValue(NativeProgrammableDoorBlock.OPEN)||!mc.level.getBlockState(DoorTargetClientFixture.BARRIER).is(net.minecraft.world.level.block.Blocks.STONE)||ticks<wait+20)return;need(NativeDoorSelection.update(1)==null,"nearer stone must block moved target");need(mc.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(DoorTargetClientFixture.BARRIER),"vanilla nearer target retained");click(mc);stage=3;wait=ticks;
        }else if(stage==3&&ticks>wait+20){need(state.getValue(NativeProgrammableDoorBlock.OPEN),"occluded real use changed Door");PacketDistributor.sendToServer(new DoorTargetClientFixture.Mode(2));stage=4;wait=ticks;
        }else if(stage==4&&ticks>wait+20){mc.level.disconnect();mc.disconnect();stage=9;}
    }
}
