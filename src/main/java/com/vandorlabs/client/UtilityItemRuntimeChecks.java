package com.vandorlabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.item.*;
import net.minecraft.util.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Actual inventory rendering, including depth/rotation, with padding on every side. */
final class UtilityItemRuntimeChecks {
    private static final int BACK=0x203040;
    private static final String[] IDS={"redstone_transmitter","redstone_receiver","programmable_matter_ingot","programmable_matter_infuser","programmable_matter_block","infuser_core","battery_block","hexagonal_fuel_tank","meridian_drive_core","vector_fold_array_core","longburn_drive_core","oculus_drive_core","survey_dish_core","sentinel_array_core","standalone_dish_core","standalone_antenna_core","paladyne_navigation_core","seyr_navigation_core","navigation_table_flat","navigation_table_angled","assembly_press_core","synthesis_tower_core","enclosed_gantry_core","turntable_controller","turntable_surface","landing_plate","landing_ski","gear_extender","gear_swing_arm","large_gear_extender","large_gear_swing_arm","landing_wheel","large_landing_wheel","gear_mount","large_gear_mount","access_card_slot","data_chip_slot","access_card:0","access_card:1","access_card:2","access_card:3","access_card:4","access_card:5","access_card:6","data_chip:0","data_chip:1","data_chip:2","data_chip:3","data_chip:4","data_chip:5","data_chip:6"};
    static ItemStack stack(int i){String[] id=IDS[i].split(":");return new ItemStack(Item.REGISTRY.getObject(new ResourceLocation("vandorlabs:"+id[0])),1,id.length==1?0:Integer.parseInt(id[1]));}
    static void run(Minecraft mc,File output)throws Exception{
        for(Item token:new Item[]{com.vandorlabs.items.ModItems.ACCESS_CARD,com.vandorlabs.items.ModItems.DATA_CHIP}){
        net.minecraft.util.NonNullList<ItemStack> cards=net.minecraft.util.NonNullList.create();
        token.getSubItems(com.vandorlabs.VandorLabs.VANDOR_LABS_TAB,cards);
        if(cards.size()!=7||token instanceof ItemBlock)throw new IllegalStateException("Seven non-placeable variants required: "+token.getRegistryName()+"");
        for(int i=0;i<7;i++){
            ItemStack card=cards.get(i);if(card.getMetadata()!=i)throw new IllegalStateException("Card creative metadata");
            net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(card,mc.world,mc.player);
            if(model==mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel()||model.getQuads(null,null,0).size()<2)throw new IllegalStateException("Missing card/chip image model: "+i);
            if(!net.minecraft.client.resources.I18n.hasKey(card.getUnlocalizedName()+".name"))throw new IllegalStateException("Card translation missing: "+i);
        }
        }
        GuiScreen previous=mc.currentScreen;Icons icons=new Icons();try{
            mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(0,0,0);
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new ScaledResolution(mc).getScaleFactor(),size=16*scale;
            int regularWheelSpan=0;
            for(int i=0;i<IDS.length;i++){
                int x=icons.x(i)*scale,y=icons.y(i)*scale,changed=0,minX=size,maxX=0,minY=size,maxY=0;
                for(int a=0;a<size;a++)for(int b=0;b<size;b++){boolean ink=(shot.getRGB(x+a,y+b)&0xffffff)!=BACK;if(ink){changed++;minX=Math.min(minX,a);maxX=Math.max(maxX,a);minY=Math.min(minY,b);maxY=Math.max(maxY,b);}if(ink && (a==0 || b==0 || a==size-1 || b==size-1))throw new IllegalStateException("Inventory icon touches slot edge: "+IDS[i]);}
                int span=Math.max(maxX-minX+1,maxY-minY+1);
                if(IDS[i].equals("landing_wheel"))regularWheelSpan=span;
                if(IDS[i].equals("large_landing_wheel") && span<=regularWheelSpan)throw new IllegalStateException("Large wheel icon must exceed regular wheel");
                if(changed<10 || changed>size*size*.85)throw new IllegalStateException("Inventory icon coverage: "+IDS[i]+" pixels="+changed);
            }
            javax.imageio.ImageIO.write(shot,"png",new File(output,"shot_utility22_items.png"));
        }finally{GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}
        System.out.println("[vandorlabs][reprolab] utility-item-padding PASS all inventory variants including seven cards and seven chips actual rotated pixels");
    }
    static void checkTankRenderState(Minecraft mc){
        net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(146,21,120);
        com.vandorlabs.tiles.TileEntityHexTank tank=(com.vandorlabs.tiles.TileEntityHexTank)mc.world.getTileEntity(pos);
        if(tank==null)throw new IllegalStateException("Tank render fixture has not arrived");
        TEHexTank renderer=new TEHexTank();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
        float oldU=OpenGlHelper.lastBrightnessX,oldV=OpenGlHelper.lastBrightnessY;
        try{
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,80,160);GlStateManager.color(1,1,1,1);renderer.render(tank,0,0,0,0,-1,1);
            com.vandorlabs.tiles.TileEntityBattery battery=(com.vandorlabs.tiles.TileEntityBattery)mc.world.getTileEntity(new net.minecraft.util.math.BlockPos(144,21,120));
            if(battery==null)throw new IllegalStateException("Battery render fixture has not arrived");
            TEBattery batteryRenderer=new TEBattery();batteryRenderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
            boolean shape=battery.isBlockShape();
            try{for(boolean block:new boolean[]{false,true}){battery.setBlockShape(block);batteryRenderer.render(battery,0,0,0,0,-1,1);}}finally{battery.setBlockShape(shape);}
            if(OpenGlHelper.lastBrightnessX!=80 || OpenGlHelper.lastBrightnessY!=160)throw new IllegalStateException("Tank leaked lightmap state");
        java.nio.FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(16);org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_CURRENT_COLOR,color);
            for(int i=0;i<4;i++)if(Math.abs(color.get(i)-1)>.001)throw new IllegalStateException("Tank leaked vertex color");
        }finally{OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,oldU,oldV);GlStateManager.color(1,1,1,1);}
        System.out.println("[vandorlabs][reprolab] utility-render-state PASS color and lightmap restored");
    }
    private static final class Icons extends GuiScreen{
        int x(int i){return width/2+(i%7-3)*30;}int y(int i){return height/2-((IDS.length+6)/7)*26/2+(i/7)*26;}public boolean doesGuiPauseGame(){return false;}
        public void drawScreen(int mouseX,int mouseY,float partial){GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);drawCenteredString(fontRenderer,"Matter, machinery, landing gear, cards and chips",width/2,12,0xffffff);for(int i=0;i<IDS.length;i++){drawRect(x(i)-1,y(i)-1,x(i)+17,y(i)+17,0xff708090);drawRect(x(i),y(i),x(i)+16,y(i)+16,0xff000000|BACK);}RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<IDS.length;i++)itemRender.renderItemAndEffectIntoGUI(stack(i),x(i),y(i));RenderHelper.disableStandardItemLighting();}
    }
}
