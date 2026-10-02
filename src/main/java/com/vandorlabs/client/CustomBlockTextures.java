package com.vandorlabs.client;

import com.vandorlabs.tiles.CustomBlockMaterials;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;

/** Uses each resource-pack/model's own atlas artwork, including door upper and lower faces. */
public final class CustomBlockTextures {
    private static final Map<Integer,IBlockState> STATES=new HashMap<>();
    private static boolean loaded;
    private static synchronized IBlockState state(int choice) {
        if(!loaded){loaded=true;for(Block block:Block.REGISTRY)for(int meta=0;meta<16;meta++)try{STATES.putIfAbsent(CustomBlockMaterials.identifier(block,meta),block.getStateFromMeta(meta));}catch(RuntimeException invalidMetadata){ }}
        return STATES.get(choice);
    }
    public static boolean isDoor(int choice){IBlockState state=state(choice);return state!=null && state.getBlock() instanceof BlockDoor;}
    public static String label(int choice){IBlockState state=state(choice);return state==null?"Custom (missing block)":state.getBlock().getLocalizedName();}
    public static TextureAtlasSprite sprite(int choice,boolean upper) {
        Minecraft mc=Minecraft.getMinecraft();IBlockState state=state(choice);
        if(state==null)return mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        if(state.getBlock() instanceof BlockDoor && state.getProperties().containsKey(BlockDoor.HALF))state=state.withProperty(BlockDoor.HALF,upper?BlockDoor.EnumDoorHalf.UPPER:BlockDoor.EnumDoorHalf.LOWER);
        IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
        if(state.getBlock() instanceof BlockDoor)for(EnumFacing face:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST}) {
            List<BakedQuad> quads=model.getQuads(state,face,0);if(!quads.isEmpty())return quads.get(0).getSprite();
        }
        if(state.getBlock() instanceof BlockDoor)for(BakedQuad quad:model.getQuads(state,null,0))if(quad.getFace().getAxis()!=EnumFacing.Axis.Y)return quad.getSprite();
        TextureAtlasSprite result=model.getParticleTexture();return "missingno".equals(result.getIconName())?mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/dark_wall_panel"):result;
    }
    public static String texture(int choice){return sprite(choice,false).getIconName();}
}
