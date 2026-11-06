package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.NativeLightRenderer;
import com.vandorlabs.neoforge.light.NativeLightEntity;
import com.vandorlabs.render.HousingLightPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;

/** Light BER adapter. Topology and artwork are prepared; only six loaded-neighbour light samples are live. */
public final class NativeLightBlockEntityRenderer implements BlockEntityRenderer<NativeLightEntity> {
    private static final Direction[] SIDES=Direction.values();
    private static final ThreadLocal<BlockPos.MutableBlockPos> POSITION=ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    public NativeLightBlockEntityRenderer(BlockEntityRendererProvider.Context context){}
    /** Match World.getCombinedLight(next,0): do not inject owner emission or emissive-rendering fullbright. */
    public static int neighborLight(Level world,BlockPos owner){
        if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Light rendering brightness off client thread");
        var probe=POSITION.get();int packed=0;for(var side:SIDES){probe.setWithOffset(owner,side);if(world.getChunkSource().getChunkNow(probe.getX()>>4,probe.getZ()>>4)==null)continue;packed=HousingLightPolicy.max(packed,LightTexture.pack(world.getBrightness(LightLayer.BLOCK,probe),world.getBrightness(LightLayer.SKY,probe)));}return packed;
    }
    @Override public void render(NativeLightEntity owner,float partialTick,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        var world=owner.getLevel();if(world==null)return;var publication=owner.renderPublication();
        NativeLightRenderer.render(publication.visual(),publication.projection(),owner.getBlockPos(),poses,buffers,neighborLight(world,owner.getBlockPos()),overlay);
    }
}
