package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.io.File;
import java.util.*;

/** Real GUI mode changes, baked placed/captured faces and configured-item slot padding. */
final class RampModeRuntimeChecks {
    private static int mode,ticks;private static final Set<String> fronts=new HashSet<>();
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("ramp-mode-front: "+message+" mode="+mode);}
    static void begin(){mode=ticks=0;fronts.clear();legacyModeState();}
    private static void legacyModeState(){
        java.util.List<VehicleStructure.Cell> cells=java.util.Arrays.asList(
                new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null),
                new VehicleStructure.Cell(BlockPos.ORIGIN.east(),ModBlocks.PROGRAMMABLE_RAMP.getDefaultState(),null));
        NBTTagCompound old=new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1).write();
        net.minecraft.nbt.NBTTagList palette=old.getTagList("Palette",10);
        for(int i=0;i<palette.tagCount();i++)if("vandorlabs:programmable_ramp".equals(palette.getCompoundTagAt(i).getString("Name")))palette.getCompoundTagAt(i).getCompoundTag("Properties").removeTag("mode");
        NBTTagCompound unchanged=old.copy();VehicleStructure restored=VehicleStructure.read(old);
        require(restored.at(BlockPos.ORIGIN.east()).state.getValue(BlockRampController.MODE)==0&&old.equals(unchanged),"old captured Ramp palettes load without mutating saved NBT");
    }
    static boolean tick(Minecraft mc,BlockPos pos,File output)throws Exception{
        require(++ticks<80,"server mode packet sync timeout");if(ticks<12)return false;
        TileEntityRampController tile=(TileEntityRampController)mc.world.getTileEntity(pos);
        if(BlockRampController.modeIndex(tile.moat,tile.escalator,tile.elevator,tile.extendSegments)!=mode)return false;
        BlockRampController block=(BlockRampController)ModBlocks.PROGRAMMABLE_RAMP;
        IBlockState actual=block.getActualState(mc.world.getBlockState(pos),mc.world,pos);require(actual.getValue(BlockRampController.MODE)==mode,"actual state follows synchronized tile");
        String expected="vandorlabs:block/ramp_mode_"+BlockRampController.MODE_TEXTURES[mode];require(fronts.add(expected),"seven distinct mode sprite names");
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean active:new boolean[]{false,true}){
            IBlockState state=actual.withProperty(BlockRampController.FACING,facing).withProperty(BlockRampController.ACTIVE,active);
            IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);java.util.List<BakedQuad> quads=model.getQuads(state,facing,0);
            require(!quads.isEmpty(),"front baked for "+facing+" active="+active);
            for(BakedQuad quad:quads)require(quad.getSprite().getIconName().equals(expected),"front uses mode sprite actual="+quad.getSprite().getIconName()+" facing="+facing+" active="+active);
            require(!model.getQuads(state,facing.getOpposite(),0).isEmpty(),"housing rear present");
            for(BakedQuad quad:model.getQuads(state,facing.getOpposite(),0))require(quad.getSprite().getIconName().equals("vandorlabs:block/ramp_elevator_controller_back_"+(active?"on":"off")),"housing power texture retained");
        }
        NBTTagCompound data=VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(pos));
        VehicleStructure captured=new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,actual,data),new VehicleStructure.Cell(BlockPos.ORIGIN.east(),ModBlocks.PILOT_SEAT.getDefaultState(),null)),BlockPos.ORIGIN.east(),EnumFacing.NORTH,1);
        VehicleWorld view=new VehicleWorld(mc.world,captured);require(block.getActualState(actual,view,BlockPos.ORIGIN).getValue(BlockRampController.MODE)==mode,"captured controller resolves same mode");
        if(mode==6){items(mc,output);System.out.println("[vandorlabs][reprolab] ramp-mode-front PASS seven-real-GUI-packet-modes four-facings powered-housing captured-models seven-configured-item-padding screenshot");return true;}
        GuiScreen gui=mc.currentScreen;java.util.List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        GuiButton button=null;for(GuiButton candidate:buttons)if(candidate.id==1)button=candidate;require(button!=null&&button.enabled,"mode cycle button available");
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class).invoke(gui,button.x+button.width/2,button.y+button.height/2,0);
        mode++;ticks=0;return false;
    }
    private static ItemStack item(int index){ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_RAMP);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("MoatMode",index==6);tag.setInteger("EscalatorMode",index>=4&&index<6?index-3:0);tag.setBoolean("Elevator",index==2||index==3);tag.setBoolean("ExtendSegments",index<4&&(index&1)!=0);stack.setTagInfo("RampSettings",tag);return stack;}
    private static void items(Minecraft mc,File output)throws Exception{
        for(int index=0;index<7;index++){
            ItemStack stack=item(index);IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);String expected="vandorlabs:block/ramp_mode_"+BlockRampController.MODE_TEXTURES[index];
            require(model.getParticleTexture().getIconName().equals(expected),"configured item model matches mode "+index+" actual="+model.getParticleTexture().getIconName());
            for(EnumFacing side:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad quad:model.getQuads(null,side,0)){
                int[] data=quad.getVertexData();int stride=data.length/4;for(int vertex=0;vertex<4;vertex++){Vec3d projected=ControlItemPadding.project(new Vec3d(Float.intBitsToFloat(data[vertex*stride]),Float.intBitsToFloat(data[vertex*stride+1]),Float.intBitsToFloat(data[vertex*stride+2])),model.getItemCameraTransforms().gui);require(Math.abs(projected.x)<.49&&Math.abs(projected.y)<.49,"transformed padding mode "+index);}
            }
        }
        GuiScreen previous=mc.currentScreen;Icons icons=new Icons();try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            java.awt.image.BufferedImage shot=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale;
            for(int index=0;index<7;index++){int x=icons.x(index)*scale,y=icons.y()*scale,changed=0;
                for(int n=0;n<size;n++){require((shot.getRGB(x+n,y)&0xffffff)==Icons.SLOT&&(shot.getRGB(x+n,y+size-1)&0xffffff)==Icons.SLOT,"top/bottom slot padding "+index);require((shot.getRGB(x,y+n)&0xffffff)==Icons.SLOT&&(shot.getRGB(x+size-1,y+n)&0xffffff)==Icons.SLOT,"left/right slot padding "+index);}
                for(int a=0;a<size;a++)for(int b=0;b<size;b++)if((shot.getRGB(x+a,y+b)&0xffffff)!=Icons.SLOT)changed++;require(changed>8*scale*scale,"actual rendered item "+index);
            }
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_ramp_mode_item_icons.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}
    }
    private static final class Icons extends GuiScreen{
        static final int SLOT=0x203040;int x(int i){return width/2+(i-3)*36-8;}int y(){return height/2-8;}
        public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int x,int y,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Ramp modes: configured 16px item slots",width/2,30,0xffffff);for(int i=0;i<7;i++){drawRect(x(i)-1,y()-1,x(i)+17,y()+17,0xff708090);drawRect(x(i),y(),x(i)+16,y()+16,0xff000000|SLOT);}RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<7;i++){GlStateManager.color(1,1,1,1);itemRender.renderItemAndEffectIntoGUI(item(i),x(i),y());}RenderHelper.disableStandardItemLighting();for(int i=0;i<7;i++)drawCenteredString(fontRenderer,Integer.toString(i+1),x(i)+8,y()+24,0xffffff);}
    }
    private RampModeRuntimeChecks(){}
}
