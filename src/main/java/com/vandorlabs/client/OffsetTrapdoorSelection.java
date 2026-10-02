package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

/** Vanilla's voxel ray traversal cannot visit an offset leaf whose destination cell is air. */
public final class OffsetTrapdoorSelection {
    @SubscribeEvent public void highlight(DrawBlockHighlightEvent event) {
        RayTraceResult hit=event.getTarget();
        if(hit!=null && hit.typeOfHit==RayTraceResult.Type.BLOCK && isOffset(event.getPlayer().world.getTileEntity(hit.getBlockPos())))event.setCanceled(true);
    }
    private static boolean isOffset(TileEntity tile) {return tile instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)tile).isCover();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc=Minecraft.getMinecraft();
        if(event.phase==TickEvent.Phase.START && mc.world!=null && mc.playerController!=null && mc.playerController.getClass()==net.minecraft.client.multiplayer.PlayerControllerMP.class)
            mc.playerController=new OffsetTrapdoorController(mc,mc.playerController);
        if(event.phase==TickEvent.Phase.END)updateTarget(1);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void mouse(MouseEvent event){updateTarget(1);}
    @SubscribeEvent public void key(InputEvent.KeyInputEvent event){updateTarget(1);}
    static void updateTarget(float partial) {
        Minecraft mc=Minecraft.getMinecraft();Entity view=mc.getRenderViewEntity();
        if(mc.world==null || view==null || mc.player==null || mc.playerController==null)return;
        Vec3d start=view.getPositionEyes(partial),end=start.add(view.getLook(partial).scale(mc.playerController.getBlockReachDistance()));
        RayTraceResult leaf=OffsetTrapdoorInteractions.trace(mc.world,start,end),vanilla=mc.objectMouseOver;
        if(leaf!=null && (vanilla==null || vanilla.typeOfHit==RayTraceResult.Type.MISS || start.squareDistanceTo(leaf.hitVec)<=start.squareDistanceTo(vanilla.hitVec))) {
            mc.objectMouseOver=leaf;mc.pointedEntity=null;
        }
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft();Entity view=mc.getRenderViewEntity();
        if(mc.world==null || view==null || mc.player==null)return;
        float partial=event.getPartialTicks();updateTarget(partial);
        RayTraceResult selected=mc.objectMouseOver;
        if(mc.gameSettings.hideGUI || selected==null || selected.typeOfHit!=RayTraceResult.Type.BLOCK)return;
        TileEntity tile=mc.world.getTileEntity(selected.getBlockPos());if(!isOffset(tile))return;
        double x=view.lastTickPosX+(view.posX-view.lastTickPosX)*partial,y=view.lastTickPosY+(view.posY-view.lastTickPosY)*partial,z=view.lastTickPosZ+(view.posZ-view.lastTickPosZ)*partial;
        GlStateManager.pushMatrix();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2);GlStateManager.disableTexture2D();GlStateManager.depthMask(false);
        RenderGlobal.drawSelectionBoundingBox(OffsetTrapdoorInteractions.bounds((TileEntityProgrammableTrapdoor)tile).grow(.002).offset(-x,-y,-z),0,0,0,.4F);
        GlStateManager.depthMask(true);GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.popMatrix();
    }
}
