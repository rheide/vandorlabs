package com.vandorlabs.gear.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.track.*;
import java.io.File;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;

/** Pixel padding for every arm/track item plus native part availability after model baking. */
public final class Gear26ClientChecks {
    private static final GearBlock[] PARTS={GearRegistry.GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.GEAR_MOUNT,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.LARGE_GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_MOUNT,TrackRegistry.COMPACT,TrackRegistry.ARMOURED,TrackRegistry.BOGIE};
    private static final int BACK=0x203040;
    private Gear26ClientChecks(){}
    public static void run(Minecraft mc,File output)throws Exception{
        for(GearBlock block:PARTS){ItemStack item=new ItemStack(block);if(mc.getRenderItem().getItemModelWithOverrides(item,mc.world,mc.player)==mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel())throw new IllegalStateException("gear26 missing item "+block.getRegistryName());}
        for(String style:new String[]{"compact","armoured","bogie"})for(int mask=0;mask<16;mask++){
            net.minecraftforge.client.model.IModel model=net.minecraftforge.client.model.ModelLoaderRegistry.getModel(new net.minecraft.util.ResourceLocation("vandorlabs:block/track/"+style+"_"+mask));
            net.minecraft.client.renderer.block.model.IBakedModel baked=model.bake(model.getDefaultState(),net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK,t->mc.getTextureMapBlocks().getAtlasSprite(t.toString()));
            if(baked.getQuads(null,null,0).isEmpty())throw new IllegalStateException("gear26 empty native track "+style+" "+mask);
        }
        payloadMesh(mc,output);
        GuiScreen old=mc.currentScreen;Icons icons=new Icons();
        try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale;
            for(int i=0;i<PARTS.length;i++){int x=icons.x(i)*scale,y=icons.y()*scale,ink=0;
                for(int a=0;a<size;a++)for(int b=0;b<size;b++)if((shot.getRGB(x+a,y+b)&0xffffff)!=BACK){ink++;if(a==0||b==0||a==size-1||b==size-1)throw new IllegalStateException("gear26 item touches slot edge "+PARTS[i].getRegistryName());}
                if(ink<10||ink>size*size*.85)throw new IllegalStateException("gear26 item coverage "+PARTS[i].getRegistryName()+" "+ink);
            }
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_gear26_items.png"));
        }finally{RenderHelper.disableStandardItemLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(old);}
        System.out.println("[vandorlabs][reprolab] gear26-client PASS 48 native track masks and nine padded arm/track inventory icons");
    }
    private static void payloadMesh(Minecraft mc,File output)throws Exception{
        net.minecraft.util.math.BlockPos pos=mc.player.getPosition().add(3,3,3);net.minecraft.block.state.IBlockState previous=mc.world.getBlockState(pos);net.minecraft.tileentity.TileEntity priorTile=mc.world.getTileEntity(pos);if(priorTile!=null)throw new IllegalStateException("gear26 render fixture needs empty tile cell");
        try{
            mc.world.setBlockState(pos,GearRegistry.GEAR_EXTENDER.getDefaultState().withProperty(GearBlock.ASSEMBLED,true),2);GearTileEntity tile=(GearTileEntity)mc.world.getTileEntity(pos);
            net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());tag.setBoolean("GearController",true);tag.setInteger("GearHeight",1);tag.setString("GearKind","NONE");tag.setBoolean("GearOperational",true);tag.setBoolean("GearTargetDeployed",false);tag.setDouble("GearProgress",.5);
            net.minecraft.nbt.NBTTagList feet=new net.minecraft.nbt.NBTTagList();feet.appendTag(new net.minecraft.nbt.NBTTagIntArray(new int[]{0,-1,0}));tag.setTag("GearFeet",feet);
            java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=java.util.Arrays.asList(new com.vandorlabs.vehicle.VehicleStructure.Cell(net.minecraft.util.math.BlockPos.ORIGIN,net.minecraft.init.Blocks.STONE.getDefaultState(),null),new com.vandorlabs.vehicle.VehicleStructure.Cell(new net.minecraft.util.math.BlockPos(1,0,0),net.minecraft.init.Blocks.STAINED_GLASS.getDefaultState(),null));
            net.minecraft.nbt.NBTTagCompound payload=new net.minecraft.nbt.NBTTagCompound();payload.setTag("Blocks",com.vandorlabs.vehicle.VehicleStructure.display(cells).write());payload.setLong("Offset",new net.minecraft.util.math.BlockPos(0,-1,0).toLong());payload.setLong("PlacedOffset",new net.minecraft.util.math.BlockPos(0,-1,0).toLong());tag.setTag("ArmPayload",payload);tile.readFromNBT(tag);
            ArmPayloadRenderer.verifyNativeRecords(tile);
            net.minecraft.client.gui.ScaledResolution resolution=new net.minecraft.client.gui.ScaledResolution(mc);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();net.minecraft.client.gui.Gui.drawRect(0,0,resolution.getScaledWidth(),resolution.getScaledHeight(),0xff203040);
            GlStateManager.pushMatrix();try{GlStateManager.translate(resolution.getScaledWidth()/2D,resolution.getScaledHeight()/2D,100);GlStateManager.scale(42,-42,42);GlStateManager.rotate(24,1,0,0);GlStateManager.rotate(35,0,1,0);GlStateManager.enableDepth();Minecraft.getMinecraft().getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);GearRenderer renderer=new GearRenderer();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);renderer.render(tile,-.5,.5,0,0,-1,1);}finally{GlStateManager.popMatrix();}
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_gear26_payload_layers.png"));
        }finally{mc.world.setBlockState(pos,previous,2);}
        System.out.println("[vandorlabs][reprolab] gear26-payload-vbo PASS separate-solid-translucent native-GPU-record-parity immutable-format rendered-layer-fixture");
    }
    private static final class Icons extends GuiScreen{
        int x(int i){return width/2-PARTS.length*12+i*24;}int y(){return height/2-8;}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void drawScreen(int x,int y,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Arms and three track styles",width/2,30,0xffffff);for(int i=0;i<PARTS.length;i++)drawRect(x(i),y(),x(i)+16,y()+16,0xff000000|BACK);RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<PARTS.length;i++)itemRender.renderItemAndEffectIntoGUI(new ItemStack(PARTS[i]),x(i),y());RenderHelper.disableStandardItemLighting();}
    }
}
