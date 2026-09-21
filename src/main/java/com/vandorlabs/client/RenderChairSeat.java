package com.vandorlabs.client;

import com.vandorlabs.entity.EntityChairSeat;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;

/** The chair is a block model; its mount is deliberately invisible. */
public class RenderChairSeat extends Render<EntityChairSeat> {
    public RenderChairSeat(RenderManager manager) { super(manager); }

    @Override
    public void doRender(EntityChairSeat entity, double x, double y, double z,
            float entityYaw, float partialTicks) { }

    @Override
    protected ResourceLocation getEntityTexture(EntityChairSeat entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
