package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import net.minecraft.tileentity.TileEntity;

/** A lit glazing frame backlights its opaque replacement artwork, not transparent glass. */
final class GlassInsertLighting {
    static int block(int ambient,int material,int tint,boolean framed,FrameAppearance frame) {
        return material>=0 && GlassMaterials.opaque(material,tint) && framed && frame.light()>0?240:ambient;
    }
    static int block(TileEntity tile,int ambient) {
        if(tile instanceof TileEntityProgrammableGlass){TileEntityProgrammableGlass t=(TileEntityProgrammableGlass)tile;return block(ambient,t.getMaterial(),t.getTint(),t.isFramed(),t.frameAppearance);}
        if(tile instanceof TileEntityAnimatedScreenSelector && tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableDiagonalGlass){TileEntityAnimatedScreenSelector t=(TileEntityAnimatedScreenSelector)tile;return block(ambient,t.getGlassMaterial(),t.getGlassTint(),t.isGlassFramed(),t.frameAppearance);}
        return ambient;
    }
    private GlassInsertLighting(){}
}
