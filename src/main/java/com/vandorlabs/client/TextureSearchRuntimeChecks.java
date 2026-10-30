package com.vandorlabs.client;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** Real picker filtering and framebuffer checks, shared by every material dialog. */
final class TextureSearchRuntimeChecks {
    static void run(Minecraft mc,File output)throws Exception {
        DiagonalSurfaceChecks.run();
        List<HousingTextureList.Option> entries=new ArrayList<>();
        for(int i=0;i<30;i++)entries.add(new HousingTextureList.Option(i,"Cyan rail "+i,"Frame/Company","minecraft:blocks/stone"));
        entries.add(new HousingTextureList.Option(30,"Red panel","Materials","minecraft:blocks/stone"));
        HousingTextureList list=new HousingTextureList(10,10,150,30,8,entries).visibleRows(6);
        HousingTextureList tiny=new HousingTextureList(10,10,150,30,8,entries).visibleRows(2);
        require(tiny.click(15,36,0)&&tiny.picked()&&tiny.selected()==30,"single material row hidden behind pinned category");
        Set<?> expanded=new HashSet<>((Set<?>)field(list,"expanded"));
        list.searchClick(16,16,0);
        type(list,"CYAN company");
        require(((List<?>)field(list,"rows")).size()==30,"case-insensitive multiword label/category search across collapsed categories");
        require(list.selected()==30,"filter changed saved material selection");
        require(list.wheel(20,40,-120),"filtered wheel scrolling");
        require(list.click(162,45,0)&&list.drag(110),"filtered scrollbar drag");list.release();
        require((Integer)field(list,"scroll")>0,"filtered scrollbar failed to move");
        list.searchClick(16,16,0);clear(list);type(list,"zznomatchingart");
        require(((List<?>)field(list,"rows")).isEmpty(),"empty result retained unrelated material rows");
        clear(list);require(((Set<?>)field(list,"expanded")).equals(expanded),"clearing search changed category expansion");
        require(list.selected()==30,"clearing search changed selection");
        type(list,"rail");HousingTextureList rebuilt=new HousingTextureList(10,10,150,30,8,entries).visibleRows(6).keepView(list);
        require(((GuiTextField)field(rebuilt,"search")).getText().equals("rail"),"rebuild discarded search");
        require(rebuilt.searchKey('e',Keyboard.KEY_E),"inventory hotkey escaped focused search");
        require(!rebuilt.searchKey((char)27,Keyboard.KEY_ESCAPE),"Escape cannot close picker");
        int frame=com.vandorlabs.tiles.ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");
        HousingTextureList trapdoors=HousingTextureList.forTrapdoors(0,10,10,150,frame);
        require(((Map<?,?>)field(trapdoors,"options")).containsKey(com.vandorlabs.tiles.ScreenHousingTextures.localIndex(frame)),"trapdoor picker missing authored Frame artwork");
        trapdoorDialogs(mc,output,frame);
        System.out.println("[vandorlabs][reprolab] texture-search PASS collapsed-categories case-fold multiword empty clear selected-preserved rebuild inventory-hotkey wheel drag trapdoor-frames compact-framebuffers");
    }
    private static void trapdoorDialogs(Minecraft mc,File output,int frame)throws Exception {
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container old=mc.player.openContainer;
        try{for(String id:new String[]{"programmable_trapdoor","programmable_diagonal_trapdoor"}) {
            net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+id);
            net.minecraft.tileentity.TileEntity seed=block.createTileEntity(mc.world,block.getDefaultState());
            com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(mc.world,com.vandorlabs.vehicle.VehicleStructure.display(Collections.singletonList(new com.vandorlabs.vehicle.VehicleStructure.Cell(net.minecraft.util.math.BlockPos.ORIGIN,block.getDefaultState(),seedTag(seed,frame)))));
            com.vandorlabs.tiles.TileEntityProgrammableTrapdoor tile=(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)view.getTileEntity(net.minecraft.util.math.BlockPos.ORIGIN);
            trapdoorArt(mc,output,tile,block.getDefaultState(),id+"_frame");
            if(id.contains("diagonal")){
                net.minecraft.nbt.NBTTagCompound tag=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());tag.setInteger("housingTexture",0);tag.setInteger("TrapdoorPosition",1);tile.readFromNBT(tag);
                tile.setTextureLayout(0);trapdoorArt(mc,output,tile,block.getDefaultState(),id+"_square_stretch");
                tile.setTextureLayout(1);trapdoorArt(mc,output,tile,block.getDefaultState(),id+"_square_tile");
            }
            for(int[] size:new int[][]{{320,240},{460,340}}) {
                GuiProgrammableTrapdoor gui=new GuiProgrammableTrapdoor(tile);gui.inventorySlots.windowId=-246;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
                HousingTextureList picker=(HousingTextureList)field(gui,"textures");
                FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_search_"+id+"_"+size[0]+".png"),-1,-1);
                GuiTextField search=(GuiTextField)field(picker,"search");picker.searchClick(search.x+4,search.y+4,0);type(picker,"frame");
                require(search.y>=gui.getGuiTop()+54&&search.y+search.height<gui.getGuiTop()+gui.getYSize()-26,"search overlaps heading or Done");
                require((Integer)field(picker,"count")>=1,"compact search leaves no material rows");
                require((Integer)field(picker,"y")+(Integer)field(picker,"count")*HousingTextureList.ROW_HEIGHT<=gui.getGuiTop()+gui.getYSize()-26,"compact material list overlaps Done");
                FrameFramebufferChecks.renderDialog(mc,gui,new File(output,"shot_search_"+id+"_filtered_"+size[0]+".png"),-1,-1);
            }
        }}finally{mc.displayGuiScreen(previous);mc.player.openContainer=old;}
    }
    private static net.minecraft.nbt.NBTTagCompound seedTag(net.minecraft.tileentity.TileEntity seed,int frame){net.minecraft.nbt.NBTTagCompound tag=seed.writeToNBT(new net.minecraft.nbt.NBTTagCompound());tag.setInteger("housingTexture",frame);return tag;}
    private static void trapdoorArt(Minecraft mc,File output,com.vandorlabs.tiles.TileEntityProgrammableTrapdoor tile,net.minecraft.block.state.IBlockState state,String name)throws Exception {
        net.minecraft.client.shader.Framebuffer fb=new net.minecraft.client.shader.Framebuffer(256,256,true);
        net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(-.15,1.55,-.15,1.55,-16,16);
        net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()) {
            fb.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.clearColor(.125F,.1875F,.25F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.disableCull();net.minecraft.client.renderer.GlStateManager.disableLighting();mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.enableAlpha();net.minecraft.client.renderer.GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,.1F);net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
            net.minecraft.client.renderer.GlStateManager.rotate(tile instanceof com.vandorlabs.tiles.TileEntityProgrammableDiagonalTrapdoor?-45:-90,1,0,0);
            mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
            net.minecraft.client.renderer.BufferBuilder buffer=net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
            TEProgrammableTrapdoor.drawConfiguredLeaf(buffer,mc.getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.fullTexture(tile.getHousingTexture())),tile,state,0,15728880);net.minecraft.client.renderer.Tessellator.getInstance().draw();org.lwjgl.opengl.GL11.glFinish();
            java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,fb);
            int pixels=0;for(int py=0;py<256;py++)for(int px=0;px<256;px++)if((image.getRGB(px,py)&0xffffff)!=0x203040)pixels++;
            require(pixels>1500,"trapdoor artwork missing from framebuffer: "+name);
            javax.imageio.ImageIO.write(image,"png",new File(output,"shot_search_"+name+".png"));
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);fb.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void type(HousingTextureList list,String value){for(char ch:value.toCharArray())require(list.searchKey(ch,0),"search input not consumed");}
    private static void clear(HousingTextureList list)throws Exception{GuiTextField search=(GuiTextField)field(list,"search");search.setFocused(true);search.setText("");list.searchKey(' ',0);list.searchKey((char)0,Keyboard.KEY_BACK);}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Texture search: "+message);}
}
