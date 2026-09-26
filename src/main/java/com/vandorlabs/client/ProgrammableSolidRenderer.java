package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityProgrammableLight;
import com.vandorlabs.tiles.ProgrammableLightTextures;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Emits atlas geometry into Forge's shared buffer without changing GL state. */
final class ProgrammableSolidRenderer {
    private ProgrammableSolidRenderer() { }

    static void emit(TileEntityAnimatedScreenSelector tile, double x, double y, double z,
            BufferBuilder buffer) {
        if (tile.getWorld() == null || !tile.hasFastRenderer()) return;
        buffer.setTranslation(0, 0, 0);
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        boolean light = tile instanceof TileEntityProgrammableLight;
        // Non-light solids historically use the horizontal transform even for vertical metadata.
        int rotation = light && !facing.getAxis().isHorizontal()
                ? (facing == EnumFacing.UP ? 4 : 5)
                : ((int)(180-facing.getHorizontalAngle())/90)&3;
        int brightness = TEAnimatedScreenSelector.neighborLight(tile);
        TextureAtlasSprite housing = TEAnimatedScreenSelector.wallSprite(tile);
        ScreenHousingMesh mesh = state.getBlock() instanceof BlockProgrammableSlab
                ? ScreenHousingMesh.slab(state.getValue(BlockProgrammableSlab.HALF)
                        == BlockSlab.EnumBlockHalf.TOP, tile.isSlabTileSides())
                : ScreenHousingMesh.cube();
        for (ScreenHousingMesh.Face face : mesh.quads)
            for (ScreenHousingMesh.Vertex vertex : face.vertices)
                vertex(buffer, housing, rotation, brightness, x,y,z,
                        vertex.x,vertex.y,vertex.z,vertex.u,vertex.v);
        if (light) {
            TileEntityProgrammableLight lamp = (TileEntityProgrammableLight) tile;
            TextureAtlasSprite face = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                    ProgrammableLightTextures.texture(lamp.getTexture(), lamp.isOn() && lamp.getLightLevel()>0));
            TEAnimatedScreenSelector.LightGroup group = TEAnimatedScreenSelector.lightGroup(lamp,state);
            double left=group.left(tile.getPos()), right=group.right(tile.getPos());
            double top=group.top(tile.getPos()), bottom=group.bottom(tile.getPos());
            vertex(buffer,face,rotation,brightness,x,y,z,16,16,-.002,right,top);
            vertex(buffer,face,rotation,brightness,x,y,z,0,16,-.002,left,top);
            vertex(buffer,face,rotation,brightness,x,y,z,0,0,-.002,left,bottom);
            vertex(buffer,face,rotation,brightness,x,y,z,16,0,-.002,right,bottom);
        }
    }

    private static void vertex(BufferBuilder buffer, TextureAtlasSprite sprite, int rotation,
            int light, double x, double y, double z, double px, double py, double pz,
            double u, double v) {
        double a=px/16, b=py/16, c=pz/16;
        double rx=a, ry=b, rz=c;
        switch (rotation) {
            case 1: rx=c; rz=1-a; break;
            case 2: rx=1-a; rz=1-c; break;
            case 3: rx=1-c; rz=a; break;
            case 4: ry=1-c; rz=b; break;
            case 5: ry=c; rz=1-b; break;
            default: break;
        }
        buffer.pos(x+rx,y+ry,z+rz).color(255,255,255,255)
                .tex(sprite.getInterpolatedU(u),sprite.getInterpolatedV(v))
                .lightmap(light >>> 16,light & 65535).endVertex();
    }
}
