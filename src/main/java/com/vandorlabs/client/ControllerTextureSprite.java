package com.vandorlabs.client;

import com.vandorlabs.render.ControllerTexturePixels;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import java.io.IOException;

/** Repairs alpha gutters before mipmapping, including resource-pack replacements. */
public final class ControllerTextureSprite extends TextureAtlasSprite {
    public ControllerTextureSprite(String name) { super(name); }
    @Override public void loadSpriteFrames(IResource resource,int levels) throws IOException {
        super.loadSpriteFrames(resource,levels);
        for (int[][] frame:framesTextureData) if (frame!=null)
            for (int level=0;level<frame.length;level++) if (frame[level]!=null)
                ControllerTexturePixels.seal(frame[level],Math.max(1,width>>level),Math.max(1,height>>level));
    }
}
