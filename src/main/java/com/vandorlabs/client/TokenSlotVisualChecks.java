package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.io.File;

final class TokenSlotVisualChecks {
    private static final BlockPos FIRST=new BlockPos(270,32,142);
    static void build(World world){WaterloggedSurfaceChecks.build(world);
        for(int kind=0;kind<2;kind++)world.setBlockState(new BlockPos(290+kind*2,34,142),Block.getBlockFromName("vandorlabs:redstone_"+(kind==0?"transmitter":"receiver")).getDefaultState(),2);
        for(int kind=0;kind<2;kind++)for(int i=0;i<8;i++){
            BlockPos pos=FIRST.add(i*2,kind*2,0);world.setBlockState(pos.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,Block.getBlockFromName("vandorlabs:data_"+(kind==0?"card":"chip")+"_slot").getDefaultState(),2);
            TileEntityTokenSlot tile=(TileEntityTokenSlot)world.getTileEntity(pos);tile.configure(i==7,i==7?127:1<<i,0);
            if(kind==1&&i<7)tile.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,i));
        }
    }
    static void gui(Minecraft mc,File output)throws Exception{
        GuiScreen previous=mc.currentScreen;TileEntityTokenSlot tile=(TileEntityTokenSlot)mc.world.getTileEntity(FIRST.add(6,0,0));if(tile==null)throw new IllegalStateException("Mount visual fixture not synchronized");
        int original=tile.behavior;
        try{
            ReaderLogoCycle cycle=new ReaderLogoCycle();int allowed=(1<<1)|(1<<3)|(1<<6);
            require(cycle.advance(1,allowed,0,true)==1&&cycle.advance(1,allowed,30,true)==1&&cycle.advance(1,allowed,59,true)==1&&cycle.advance(1,allowed,60,true)==3,"three-second allowed-logo cycle");
            require(cycle.advance(1,allowed,80,false)==3&&cycle.advance(1,allowed,200,true)==3&&cycle.advance(1,allowed,230,true)==3&&cycle.advance(1,allowed,260,true)==6,"logo pauses outside proximity");
            require(cycle.advance(3,allowed|1,261,true)==0&&cycle.advance(3,allowed|1,290,true)==0,"Any keeps unbranded background");
            require(com.vandorlabs.items.ItemDesignToken.filterName(new ItemStack(com.vandorlabs.items.ModItems.DATA_CARD)).equals("Any Card")&&com.vandorlabs.items.ItemDesignToken.filterName(new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP)).equals("Any Chip"),"wildcard selector labels");
            for(int[] size:new int[][]{{320,240},{460,340}})for(int mode=0;mode<4;mode++){
                tile.behavior=mode;GuiTokenSlot gui=new GuiTokenSlot(new com.vandorlabs.container.ContainerTokenSlot(mc.player.inventory,tile));gui.inventorySlots.windowId=-78;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
                require(gui.inventorySlots.inventorySlots.size()==7,"mount dialog contains only seven credential toggles");
                GuiOptionCycleRuntimeChecks.check(mc);mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
                javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_token_mount_dialog_"+size[0]+"_"+mode+".png"));
            }
        }finally{tile.behavior=original;mc.displayGuiScreen(previous);if(previous==null)mc.player.openContainer=mc.player.inventoryContainer;}
        animation(mc,output);WirelessRedstoneVisualChecks.run(mc,output);WaterloggedSurfaceChecks.run(mc,output);System.out.println("[vandorlabs][reprolab] token-mount-dialog PASS small and regular layouts all four behaviors inventory whitelist and channel field");
    }
    private static void animation(Minecraft mc,File output)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(320,240,true);
        net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(-.9,.9,-.675,.675,-10,10);
        net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            for(int kind=0;kind<2;kind++){
                TileEntityTokenSlot source=(TileEntityTokenSlot)mc.world.getTileEntity(FIRST.add(0,kind*2,0));net.minecraft.nbt.NBTTagCompound data=source.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
                data.setInteger("TokenBehavior",2);data.setUniqueId("TokenReturnTo",java.util.UUID.fromString("8afda66c-e56d-4c52-906b-273a2b853c3b"));data.setTag("InsertedToken",new ItemStack(kind==0?com.vandorlabs.items.ModItems.DATA_CARD:com.vandorlabs.items.ModItems.DATA_CHIP,1,3).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                int[] first=null;
                for(int age:new int[]{-1,0,4,8,12,16,20}){
                    data.setTag("InsertedToken",new ItemStack(kind==0?com.vandorlabs.items.ModItems.DATA_CARD:com.vandorlabs.items.ModItems.DATA_CHIP,1,3).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));if(age<0){data.removeTag("InsertedToken");data.setLong("TokenRejectedUntil",mc.world.getTotalWorldTime()+12);}else data.removeTag("TokenRejectedUntil");
                    data.setLong("TokenInsertStarted",mc.world.getTotalWorldTime()-age);data.setBoolean("TokenToggled",age>=8);TileEntityTokenSlot copy=(TileEntityTokenSlot)net.minecraft.tileentity.TileEntity.create(mc.world,data);copy.setWorld(mc.world);
                    frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.rotate(15,1,0,0);net.minecraft.client.renderer.GlStateManager.rotate(155,0,1,0);net.minecraft.client.renderer.GlStateManager.translate(-.5,-.7,-.5);
                    net.minecraft.client.renderer.GlStateManager.clearColor(.08F,.12F,.16F,1);net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.disableCull();net.minecraft.client.renderer.GlStateManager.disableBlend();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
                    mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                    net.minecraft.block.state.IBlockState state=mc.world.getBlockState(source.getPos());net.minecraft.client.renderer.block.model.IBakedModel model=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
                    net.minecraft.client.renderer.BufferBuilder buffer=net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,net.minecraft.client.renderer.vertex.DefaultVertexFormats.ITEM);
                    for(EnumFacing face:EnumFacing.values())for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state,face,0))buffer.addVertexData(quad.getVertexData());for(net.minecraft.client.renderer.block.model.BakedQuad quad:model.getQuads(state,null,0))buffer.addVertexData(quad.getVertexData());net.minecraft.client.renderer.Tessellator.getInstance().draw();
                    new TETokenSlot().render(copy,0,0,0,0,-1,1);
                    java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(320,240,frame);int[] pixels=image.getRGB(0,0,320,240,null,0,320);
                    if(age==0)first=pixels;else if(age==8&&java.util.Arrays.equals(first,pixels))throw new IllegalStateException("Token insertion did not animate");
                    javax.imageio.ImageIO.write(image,"png",new File(output,"token-"+(kind==0?"card":"chip")+(age<0?"-rejected":"-insert-"+age)+".png"));
                }
            }
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
        System.out.println("[vandorlabs][reprolab] token-insert-animation PASS card and chip six phases approach scan and return");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
