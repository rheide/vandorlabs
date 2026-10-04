package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Draw housing edges instead of vanilla's enclosing cube. */
public final class DiagonalScreenSelection {
    @SubscribeEvent public void highlight(DrawBlockHighlightEvent event){
        RayTraceResult target=event.getTarget();if(target==null || target.typeOfHit!=RayTraceResult.Type.BLOCK)return;
        EntityPlayer player=event.getPlayer();BlockPos pos=target.getBlockPos();IBlockState state=player.world.getBlockState(pos);
        if(!(state.getBlock() instanceof BlockProgrammableDiagonalScreen))return;
        event.setCanceled(true);float partial=event.getPartialTicks();
        double x=pos.getX()-player.lastTickPosX-(player.posX-player.lastTickPosX)*partial;
        double y=pos.getY()-player.lastTickPosY-(player.posY-player.lastTickPosY)*partial;
        double z=pos.getZ()-player.lastTickPosZ-(player.posZ-player.lastTickPosZ)*partial;
        DiagonalScreenShape shape=DiagonalScreenShape.of(state.getValue(BlockProgrammableDiagonalScreen.FACING),state.getValue(BlockProgrammableDiagonalScreen.INVERTED));
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.glLineWidth(2);
        GlStateManager.disableTexture2D();GlStateManager.depthMask(false);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
        for(double[] edge:shape.outline)for(int i=0;i<6;i+=3)buffer.pos(x+edge[i],y+edge[i+1],z+edge[i+2]).color(0F,0F,0F,.4F).endVertex();
        Tessellator.getInstance().draw();GlStateManager.depthMask(true);GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.glLineWidth(1);
    }
}
