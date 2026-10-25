package com.vandorlabs.client;

import com.vandorlabs.plate.*;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.EnumFacing;
import java.io.File;

public final class PressurePlateClientChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Pressure plate client: "+message);}
    public static void run(Minecraft mc,File output)throws Exception{
        CreativeCatalogRuntimeChecks.checkNames();
        deviceItems(mc);
        PlateBlock block=(PlateBlock)Block.getBlockFromName("vandorlabs:programmable_pressure_plate");
        for(int power=0;power<16;power++)for(int choice:new int[]{ScreenHousingTextures.screenIndex("door_inner"),ScreenHousingTextures.DEFAULT_TRAPDOOR}){
            net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(PlateBlock.POWER,power);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
            state=((net.minecraftforge.common.property.IExtendedBlockState)state).withProperty(ProgrammableHousingState.FINISH,choice);int count=0;
            for(EnumFacing face:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.EAST,EnumFacing.WEST,EnumFacing.NORTH,EnumFacing.SOUTH})for(BakedQuad q:model.getQuads(state,face,0)){count++;require(q.getSprite().getIconName().equals(ScreenHousingTextures.texture(choice)),"selected main/trapdoor material reaches actual quad");require(q.getSprite()!=mc.getTextureMapBlocks().getMissingSprite(),"native material resolves");int[] data=q.getVertexData();for(int v=0;v<4;v++){int i=v*data.length/4;double x=Float.intBitsToFloat(data[i]),y=Float.intBitsToFloat(data[i+1]),z=Float.intBitsToFloat(data[i+2]);require(x>=1/16D-1e-7&&x<=15/16D+1e-7&&z>=1/16D-1e-7&&z<=15/16D+1e-7&&y>=-1e-7&&y<=(power>0?.5:1)/16D+1e-7,"emitted native plate geometry");}}
            require(count==6,"six thin plate faces");
        }
        for(int choice:new int[]{ScreenHousingTextures.screenIndex("door_inner"),ScreenHousingTextures.DEFAULT_TRAPDOOR}){
            java.awt.image.BufferedImage up=render(mc,block,false,choice),down=render(mc,block,true,choice);int difference=0,ink=0;
            for(int y=0;y<256;y++)for(int x=0;x<256;x++){if(up.getRGB(x,y)!=down.getRGB(x,y))difference++;if((up.getRGB(x,y)&0xffffff)!=0x203040)ink++;}
            String name=choice==ScreenHousingTextures.DEFAULT_TRAPDOOR?"trapdoor":"main";
            javax.imageio.ImageIO.write(up,"png",new File(output,"shot_pressure_plate_"+name+"_up.png"));javax.imageio.ImageIO.write(down,"png",new File(output,"shot_pressure_plate_"+name+"_down.png"));
            require(ink>1000&&difference>40,"actual native pressure plate visible and press changes rendered travel ink="+ink+" changed="+difference);
        }
        net.minecraft.item.ItemStack icon=new net.minecraft.item.ItemStack(block);IBakedModel itemModel=mc.getRenderItem().getItemModelWithOverrides(icon,mc.world,mc.player);ItemTransformVec3f guiTransform=itemModel.getItemCameraTransforms().gui;
        for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:itemModel.getQuads(null,side,0)){int[] data=quad.getVertexData();for(int v=0;v<4;v++){int i=v*data.length/4;net.minecraft.util.math.Vec3d point=ControlItemPadding.project(new net.minecraft.util.math.Vec3d(Float.intBitsToFloat(data[i]),Float.intBitsToFloat(data[i+1]),Float.intBitsToFloat(data[i+2])),guiTransform).addVector(guiTransform.translation.x,guiTransform.translation.y,0);require(Math.abs(point.x)<.42&&Math.abs(point.y)<.42,"inventory icon retains padding after native GUI rotation");}}
        inventory(mc,output,icon);
        java.lang.reflect.Field picker=GuiPressurePlate.class.getDeclaredField("textures"),mode=GuiPressurePlate.class.getDeclaredField("mode"),sensitivity=GuiPressurePlate.class.getDeclaredField("sensitivity");picker.setAccessible(true);mode.setAccessible(true);sensitivity.setAccessible(true);net.minecraft.inventory.Container previous=mc.player.openContainer;
        try{for(int[] size:new int[][]{{320,240},{460,340}}){PlateTile tile=new PlateTile();GuiPressurePlate gui=new GuiPressurePlate(new PlateContainer(tile));gui.setWorldAndResolution(mc,size[0],size[1]);require(gui.getGuiTop()>=0&&gui.getGuiLeft()>=0&&gui.getGuiTop()+gui.getYSize()<=size[1],"dialog fits viewport");HousingTextureList list=(HousingTextureList)picker.get(gui);list.setSelected(ScreenHousingTextures.DEFAULT_TRAPDOOR);require(list.selected()==ScreenHousingTextures.DEFAULT_TRAPDOOR,"trapdoor category selectable");require(list.wheel(gui.getGuiLeft()+18,gui.getGuiTop()+42,-120),"wheel addresses shared picker");require(list.click(gui.getGuiLeft()+new ProgrammableDialogLayout(size[0],size[1]).listWidth+12,gui.getGuiTop()+55,0),"shared scrollbar hit region");require(list.drag(gui.getGuiTop()+gui.getYSize()-40),"shared scrollbar drag");list.release();require((Integer)mode.get(gui)==1,"default pressed output");gui.mouseClicked(new ProgrammableDialogLayout(size[0],size[1]).controlsX+3,gui.getGuiTop()+80,1);require((Integer)mode.get(gui)==0,"right click selects previous output mode");gui.mouseClicked(new ProgrammableDialogLayout(size[0],size[1]).controlsX+3,gui.getGuiTop()+80,0);require((Integer)mode.get(gui)==1,"left click selects next output mode");require((Integer)sensitivity.get(gui)==0,"default wooden sensitivity");gui.mouseClicked(new ProgrammableDialogLayout(size[0],size[1]).controlsX+3,gui.getGuiTop()+104,1);require((Integer)sensitivity.get(gui)==2,"right click selects previous sensitivity Iron");gui.mouseClicked(new ProgrammableDialogLayout(size[0],size[1]).controlsX+3,gui.getGuiTop()+104,0);require((Integer)sensitivity.get(gui)==0,"left click wraps sensitivity Wood");gui.mouseClicked(new ProgrammableDialogLayout(size[0],size[1]).controlsX+3,gui.getGuiTop()+104,0);require((Integer)sensitivity.get(gui)==1,"left click selects Gold");
            javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,size[0],size[1]),"png",new File(output,"shot_pressure_plate_dialog_"+size[0]+".png"));
        }}finally{mc.player.openContainer=previous;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
        System.out.println("[vandorlabs][reprolab] pressure-plate-client PASS all16 analog-state geometry main+trapdoor quad sprites compact dialog Wood/Gold/Iron previous+next shared wheel/drag picker");
    }
    private static void deviceItems(Minecraft mc){
        String dark=ScreenHousingTextures.texture(ScreenHousingTextures.screenIndex("door_inner"));
        require(dark.equals("vandorlabs:blocks/programmable_glass/door_side"),"Door Interior Dark resolves independently verified native artwork");
        for(String id:new String[]{"programmable_viewscreen","programmable_diagonal_screen","programmable_half_input","programmable_input","programmable_half_console","programmable_console","programmable_diagonal_half_console"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);require(block!=null&&block!=net.minecraft.init.Blocks.AIR,"registered device "+id);
            net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> items=net.minecraft.util.NonNullList.create();net.minecraft.item.Item.getItemFromBlock(block).getSubItems(net.minecraft.creativetab.CreativeTabs.SEARCH,items);require(items.size()==1,"one fresh creative device "+id);
            IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(items.get(0),mc.world,mc.player);int housing=0;
            for(EnumFacing face:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(null,face,0)){
                require(!quad.getSprite().getIconName().equals("vandorlabs:blocks/dark_wall_panel"),"creative device does not retain old wall material "+id);
                if(quad.getSprite().getIconName().equals(dark))housing++;
            }
            require(housing>0,"creative device uses Door Interior Dark "+id);
        }
    }
    private static java.awt.image.BufferedImage render(Minecraft mc,PlateBlock block,boolean pressed,int choice){
        net.minecraft.block.state.IBlockState listed=block.getDefaultState().withProperty(PlateBlock.POWER,pressed?15:0);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(listed);net.minecraft.block.state.IBlockState state=((net.minecraftforge.common.property.IExtendedBlockState)listed).withProperty(ProgrammableHousingState.FINISH,choice);java.util.List<BakedQuad> quads=new java.util.ArrayList<>();for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})quads.addAll(model.getQuads(state,side,0));
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(256,256,true);net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(-.75,.75,-.6,.65,-10,10);net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clearColor(32/255F,48/255F,64/255F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableBlend();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);net.minecraft.client.renderer.GlStateManager.depthFunc(515);net.minecraft.client.renderer.GlStateManager.disableCull();net.minecraft.client.renderer.GlStateManager.enableTexture2D();net.minecraft.client.renderer.GlStateManager.enableAlpha();net.minecraft.client.renderer.GlStateManager.alphaFunc(516,.1F);mc.entityRenderer.disableLightmap();mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            net.minecraft.client.renderer.GlStateManager.rotate(55,1,0,0);net.minecraft.client.renderer.GlStateManager.rotate(225,0,1,0);net.minecraft.client.renderer.GlStateManager.translate(-.5,-.03125,-.5);
            net.minecraft.client.renderer.BufferBuilder buffer=net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,quads.get(0).getFormat());for(BakedQuad quad:quads)buffer.addVertexData(quad.getVertexData());net.minecraft.client.renderer.Tessellator.getInstance().draw();org.lwjgl.opengl.GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void inventory(Minecraft mc,File output,net.minecraft.item.ItemStack stack)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(128,128,true);net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(0,32,32,0,-1000,1000);net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clearColor(32/255F,48/255F,64/255F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableTexture2D();net.minecraft.client.renderer.GlStateManager.enableAlpha();net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(stack,8,8);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_pressure_plate_inventory.png"));int count=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040){count++;require(x>=35&&x<=92&&y>=35&&y<=92,"actual rendered inventory plate padding");}require(count>100,"actual inventory plate visibly renders");
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private PressurePlateClientChecks(){}
}
