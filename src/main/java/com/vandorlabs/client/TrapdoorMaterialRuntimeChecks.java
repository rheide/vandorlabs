package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import java.nio.ByteBuffer;

/** Validate the real client atlas, synchronized group and renderer buffer in every follow-up capture. */
final class TrapdoorMaterialRuntimeChecks {
    static void checkScene(Minecraft mc,String scene,int x,int y) {
        TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)mc.world.getTileEntity(new BlockPos(x-1,y+2,-18));
        if(root==null)throw new IllegalStateException("trapdoor followup fixture missing");
        boolean single=scene.contains("slide_wall") || scene.contains("rotate_neighbors") || scene.contains("next_");
        if(root.group().size()!=(single?1:4))throw new IllegalStateException("trapdoor followup client grouping failed");
        boolean custom=CustomBlockMaterials.isCustom(root.getHousingTexture());
        int upper=0,lower=0;
        for(TileEntityProgrammableTrapdoor leaf:root.group()) {
            BufferBuilder buffer=new BufferBuilder(8192);buffer.begin(7,BlockSurfaceFormat.get());
            TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(leaf.getHousingTexture()));
            TEProgrammableTrapdoor.drawConfiguredLeaf(buffer,sprite,leaf,mc.world.getBlockState(leaf.getPos()),mc.world.getBlockState(leaf.getPos()).getValue(BlockProgrammableTrapdoor.OPEN)?1:0,0xF000A0);
            buffer.finishDrawing();ByteBuffer bytes=buffer.getByteBuffer();int stride=buffer.getVertexFormat().getNextOffset(),tex=buffer.getVertexFormat().getUvOffsetById(0),count=buffer.getVertexCount();
            TextureAtlasSprite edge=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/programmable_glass/metal_side");
            for(int i=0;i<count;i++) {
                int offset=i*stride;float u=bytes.getFloat(offset+tex),v=bytes.getFloat(offset+tex+4);
                if(i>=count-16){if(!inside(edge,u,v))throw new IllegalStateException("trapdoor thin edge uses selected face artwork");}
                else if(custom){boolean top=inside(CustomBlockTextures.sprite(leaf.getHousingTexture(),true),u,v),bottom=inside(CustomBlockTextures.sprite(leaf.getHousingTexture(),false),u,v);if(!top && !bottom)throw new IllegalStateException("custom trapdoor door artwork bleeds outside upper/lower sprites");if(top)upper++;if(bottom)lower++;}
                else if(!inside(sprite,u,v))throw new IllegalStateException("built-in trapdoor artwork bleeds outside atlas sprite");
            }
        }
        if(custom && !single && (upper==0 || lower==0))throw new IllegalStateException("combined Custom door did not use both halves");
        System.out.println("[vandorlabs][reprolab] trapdoor-material-runtime PASS "+scene);
    }
    private static boolean inside(TextureAtlasSprite sprite,float u,float v){return u>=sprite.getMinU()-1e-6 && u<=sprite.getMaxU()+1e-6 && v>=sprite.getMinV()-1e-6 && v<=sprite.getMaxV()+1e-6;}
}
