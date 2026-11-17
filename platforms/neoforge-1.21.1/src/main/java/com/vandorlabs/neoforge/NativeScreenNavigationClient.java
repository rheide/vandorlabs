package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.screen.*;
import com.vandorlabs.render.ScreenSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeScreenNavigationClient {
 @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->NativeScreenNavigation.CLIENT=(p,t,slot,action)->{var owner=NativeScreenNavigation.owner(t);if(owner!=null)NativeNavigationClient.action(owner.getBlockPos(),slot,action);return true;});}
 public static void render(NativeScreenEntity screen,int slot,ScreenSurface.Quad content,Matrix4fc pixelToOwner,PoseStack poses,MultiBufferSource buffers){if(!NativeScreenNavigation.selected(screen,slot))return;var c=NativeScreenNavigation.canvas(screen,slot);var ui=new Matrix4f();if(screen.family()==com.vandorlabs.screen.ScreenFamily.SCREEN){var group=NativeScreenChipGroups.group(screen);if(group==null||!group.owner().equals(screen.getBlockPos()))return;double span=content.topRight.x-content.topLeft.x+16*(group.columns()-1),top=content.topRight.y+16*(group.rows()-1);ui.zero().m00((float)(-span/c.width)).m11((float)((content.bottomRight.y-top)/c.height)).m22(content.nz).m30((float)(content.topRight.x+16*(group.columns()-1))).m31((float)(top+content.ny*.02F)).m32((float)(content.topRight.z+content.nz*.02F)).m33(1);}else NativeOverlayCanvas.uiMatrix(content,c.width,c.height,ui);var matrix=new Matrix4f(poses.last().pose()).mul(pixelToOwner).mul(ui);NativeNavigationClient.draw(screen.getBlockPos(),slot,c,matrix,buffers);}
 private NativeScreenNavigationClient(){}
}
