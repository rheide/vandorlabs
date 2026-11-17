package com.vandorlabs.client;
import com.vandorlabs.tiles.TileEntityChipAppliance;
import com.vandorlabs.blocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.MapData;
/** Exact native attachment artwork; no frame entity or duplicate inventory owner. */
public final class TEChipAppliance extends TileEntitySpecialRenderer<TileEntityChipAppliance> {
    private static final java.util.WeakHashMap<TileEntityChipAppliance,MapData> PREVIEWS=new java.util.WeakHashMap<>();
    public static void clear(){PREVIEWS.clear();}
    static MapData mapData(TileEntityChipAppliance tile){Minecraft mc=Minecraft.getMinecraft();ItemStack item=tile.getStackInSlot(1);String name="map_"+item.getMetadata();MapData source=tile.mapPreview,shared=(MapData)tile.getWorld().loadData(MapData.class,name);boolean changed=source!=null&&PREVIEWS.get(tile)!=source;if(source!=null&&(shared==null||changed)){if(shared==null){shared=new MapData(name);tile.getWorld().setData(name,shared);}shared.readFromNBT(source.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));if(PREVIEWS.size()>64)PREVIEWS.clear();PREVIEWS.put(tile,source);}if(shared!=null&&(changed||mc.entityRenderer.getMapItemRenderer().getMapInstanceIfExists(name)==null))mc.entityRenderer.getMapItemRenderer().updateMapTexture(shared);return shared;}
    @Override public void render(TileEntityChipAppliance tile,double x,double y,double z,float partial,int destroy,float alpha){
        if(tile.getWorld()==null)return;Minecraft mc=Minecraft.getMinecraft();
        try(TurntableRenderState saved=new TurntableRenderState()){
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);TETokenSlot.nativeMount(tile,BlockChipAppliance.front(tile,false),x,y,z,partial);
            GlStateManager.pushMatrix();try{
                com.vandorlabs.blocks.MachineMountSurface button=BlockChipAppliance.front(tile,true);EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockVandorDirectional.FACING);
                GlStateManager.translate(x+button.center.x,y+button.center.y,z+button.center.z);GlStateManager.rotate(facing==EnumFacing.EAST?270:facing==EnumFacing.SOUTH?180:facing==EnumFacing.WEST?90:0,0,1,0);GlStateManager.scale(.5,.5,.5);GlStateManager.translate(-.5,-.5,-1);
                net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:push_button");net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.NORTH).withProperty(BlockVandorSwitch.ON,tile.getWorld().getTotalWorldTime()<tile.pressedUntil);bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);mc.getBlockRendererDispatcher().renderBlockBrightness(state,1);
            }finally{GlStateManager.popMatrix();}
            if(tile.scanner())frame(tile,x,y,z);
        }
    }
    private void frame(TileEntityChipAppliance tile,double x,double y,double z){Minecraft mc=Minecraft.getMinecraft();ItemStack item=tile.getStackInSlot(1);boolean map=item.getItem()==Items.FILLED_MAP;
        GlStateManager.pushMatrix();try{
            GlStateManager.translate(x+.5,y+1.035,z+.5);GlStateManager.rotate(90,1,0,0);GlStateManager.scale(map?.75:1,map?.75:1,map?.75:1);GlStateManager.translate(-.5,-.5,-1);
            IBakedModel model=mc.getRenderItem().getItemModelMesher().getModelManager().getModel(new ModelResourceLocation(new ResourceLocation("minecraft:item_frame"),map?"map":"normal"));bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModelBrightnessColor(model,1,1,1,1);
            if(!item.isEmpty()){
                GlStateManager.translate(.5,.5,-.035);GlStateManager.rotate(180,0,1,0);
                if(map){MapData data=mapData(tile);if(data!=null){GlStateManager.scale(1/128F,1/128F,1/128F);GlStateManager.translate(-64,-64,0);mc.entityRenderer.getMapItemRenderer().renderMap(data,false);}}
                else{GlStateManager.scale(.65,.65,.65);RenderHelper.enableStandardItemLighting();mc.getRenderItem().renderItem(item,ItemCameraTransforms.TransformType.FIXED);RenderHelper.disableStandardItemLighting();}
            }
        }finally{GlStateManager.popMatrix();}
    }
}
