package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockWirelessRedstone;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.io.File;
import java.util.*;

/** Check authored arrows in baked world space, including the top and bottom UV orientations. */
final class WirelessRedstoneVisualChecks {
    static void run(Minecraft mc,File output)throws Exception{
        for(int index=1;index<=3;index++){
            int choice=com.vandorlabs.tiles.ScreenHousingTextures.screenIndex("tech/ancient_relief_"+index);
            WirelessRedstoneRuntimeChecks.require("Tech".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice)),"ancient relief Tech category");
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));
            WirelessRedstoneRuntimeChecks.require(sprite.getIconWidth()==128&&sprite.getIconHeight()==128,"ancient relief standard resolution");
        }
        for(int index=1;index<=4;index++){
            int choice=com.vandorlabs.tiles.ScreenHousingTextures.screenIndex("security_glass/security_"+index);
            WirelessRedstoneRuntimeChecks.require("Glass".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(choice)),"security glass picker category");
            HousingTextureList picker=HousingTextureList.forGlassMaterials(0,0,0,150,0,choice);
            WirelessRedstoneRuntimeChecks.require(picker.selected()==choice,"security glass selectable");
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(choice));int clear=0,visible=0;
            for(int pixel:sprite.getFrameTextureData(0)[0]){if((pixel>>>24)==0)clear++;else visible++;}
            WirelessRedstoneRuntimeChecks.require(clear>visible&&visible>500,"security glass has transparent background and visible reinforcement");
        }
        for(boolean tx:new boolean[]{true,false}){
            BlockWirelessRedstone block=(BlockWirelessRedstone)Block.getBlockFromName("vandorlabs:redstone_"+(tx?"transmitter":"receiver"));
            for(EnumFacing port:EnumFacing.values()){
                IBlockState state=block.getDefaultState().withProperty(BlockWirelessRedstone.FACING,port);IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);List<BakedQuad> quads=new ArrayList<>(model.getQuads(state,null,0));for(EnumFacing face:EnumFacing.values())quads.addAll(model.getQuads(state,face,0));
                WirelessRedstoneRuntimeChecks.require(quads.size()==6,"six textured cube faces");
                for(BakedQuad quad:quads){String name=quad.getSprite().getIconName(),leaf=name.substring(name.lastIndexOf('/')+1);
                    if(leaf.equals("port")){WirelessRedstoneRuntimeChecks.require(quad.getFace()==port,"port artwork faces physical port");continue;}
                    if(leaf.equals("type")){WirelessRedstoneRuntimeChecks.require(quad.getFace()==port.getOpposite(),"wireless type opposite port");continue;}
                    Vec3d u=basis(quad,4),v=basis(quad,5),arrow=leaf.equals("up")?v.scale(-1):leaf.equals("down")?v:leaf.equals("left")?u.scale(-1):u;
                    Vec3d expected=new Vec3d(port.getDirectionVec());WirelessRedstoneRuntimeChecks.require(arrow.normalize().dotProduct(expected)>.999,"arrow flow "+name+" port "+port+" face "+quad.getFace());
                }
            }
        }
        GuiScreen previous=mc.currentScreen;
        try{for(int kind=0;kind<2;kind++){
            TileEntityWirelessRedstone tile=(TileEntityWirelessRedstone)mc.world.getTileEntity(new BlockPos(290+kind*2,34,142));WirelessRedstoneRuntimeChecks.require(tile!=null,"wireless dialog fixture synchronized");
            GuiWirelessRedstone gui=new GuiWirelessRedstone(new com.vandorlabs.container.ContainerWirelessRedstone(tile));gui.inventorySlots.windowId=-89;mc.displayGuiScreen(gui);
            for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);GuiOptionCycleRuntimeChecks.check(mc);mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_wireless_redstone_dialog_"+kind+"_"+size[0]+".png"));}
        }}finally{mc.displayGuiScreen(previous);if(previous==null)mc.player.openContainer=mc.player.inventoryContainer;}
        System.out.println("[vandorlabs][reprolab] wireless-redstone-visual PASS twelve port/type orientations 48 arrow vectors actual-mouse reverse cycles and two dialog sizes");
    }
    private static Vec3d basis(BakedQuad quad,int coordinate){
        int[] data=quad.getVertexData();int stride=data.length/4;double mean=0;for(int vertex=0;vertex<4;vertex++)mean+=Float.intBitsToFloat(data[vertex*stride+coordinate])/4;
        Vec3d result=Vec3d.ZERO;for(int vertex=0;vertex<4;vertex++){double weight=Float.intBitsToFloat(data[vertex*stride+coordinate])-mean;result=result.addVector(Float.intBitsToFloat(data[vertex*stride])*weight,Float.intBitsToFloat(data[vertex*stride+1])*weight,Float.intBitsToFloat(data[vertex*stride+2])*weight);}return result.normalize();
    }
}
