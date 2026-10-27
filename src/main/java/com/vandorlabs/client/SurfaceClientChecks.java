package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.TurntableTextures;
import com.vandorlabs.container.ContainerSurface;
import java.io.File;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Render the actual surface TESR and exercise its compact player-facing picker. */
final class SurfaceClientChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Surface client: "+message);}
    static void run(Minecraft mc,File output)throws Exception{
        SurfaceRenderChecks.run(mc);heldItem(mc,output);damagedPads(mc,output);java.util.Set<Integer> signatures=new java.util.HashSet<>();
        for(int i=6;i<TurntableTextures.IDS.length;i++)for(int mode:new int[]{1,2}){
            BufferedImage image=render(mc,TurntableTextures.IDS[i],mode,false);if(i==6&&mode==1){BufferedImage dirty=render(mc,TurntableTextures.IDS[i],mode,true);require(java.util.Arrays.equals(image.getRGB(0,0,256,256,null,0,256),dirty.getRGB(0,0,256,256,null,0,256)),"actual TESR pixels survive incoming alpha/depth/texture/translation contamination");}javax.imageio.ImageIO.write(image,"png",new File(output,"shot_surface_"+TurntableTextures.IDS[i]+"_"+(mode==1?"tile":"fit")+".png"));
            int count=0,hash=1;for(int y=0;y<256;y++)for(int x=0;x<256;x++){int rgb=image.getRGB(x,y)&0xffffff;if(rgb!=0x203040)count++;hash=31*hash+rgb;}
            require(count>40000,"actual multi-cell selected artwork visibly fills pad "+TurntableTextures.IDS[i]+" pixels="+count);signatures.add(hash);
        }
        require(signatures.size()>=12,"native selected artwork and Tile/Fit produce distinct actual frames");
        TileEntityTurntableSurface tile=new TileEntityTurntableSurface();Field picker=GuiProgrammableSurface.class.getDeclaredField("textures"),layout=GuiProgrammableSurface.class.getDeclaredField("layout"),on=GuiProgrammableSurface.class.getDeclaredField("on"),off=GuiProgrammableSurface.class.getDeclaredField("off"),active=GuiProgrammableSurface.class.getDeclaredField("active");for(Field field:new Field[]{picker,layout,on,off,active})field.setAccessible(true);
        net.minecraft.inventory.Container previous=mc.player.openContainer;
        try{for(int[] size:new int[][]{{320,240},{460,340}}){GuiProgrammableSurface gui=new GuiProgrammableSurface(new ContainerSurface(tile));gui.inventorySlots.windowId=-91;gui.setWorldAndResolution(mc,size[0],size[1]);ProgrammableDialogLayout positions=new ProgrammableDialogLayout(size[0],size[1]);require(gui.getGuiTop()>=0&&gui.getGuiLeft()>=0&&gui.getGuiTop()+gui.getYSize()<=size[1],"dialog fits compact viewport");
            HousingTextureList list=(HousingTextureList)picker.get(gui);require(list.wheel(positions.listX+4,positions.top+65,-120),"wheel addresses surface picker");require(list.click(positions.listX+positions.listWidth+3,positions.top+76,0),"surface scrollbar hit region");require(list.drag(positions.top+positions.height-40),"surface scrollbar drag");list.release();
            gui.mouseClicked(positions.controlsX+3,positions.top+34,1);require((Integer)layout.get(gui)==1,"right click selects previous layout from default Fit");gui.mouseClicked(positions.controlsX+3,positions.top+34,0);require((Integer)layout.get(gui)==2,"left click selects next layout back to Fit");
            list.setSelected(ScreenHousingTextures.DEFAULT_TRAPDOOR);require(list.selected()==ScreenHousingTextures.DEFAULT_TRAPDOOR,"main trapdoor artwork selectable on surface");String oldOn=(String)on.get(gui);gui.choose(TurntableTextures.materialChoice("landing"));require(off.get(gui).equals(TurntableTextures.fromMaterialChoice(TurntableTextures.materialChoice("landing")))&&on.get(gui).equals(oldOn),"inactive selection preserves active artwork");String oldOff=(String)off.get(gui);gui.mouseClicked(positions.listX+3,positions.top+34,0);require((Boolean)active.get(gui),"Active artwork tab above picker selects active state");gui.choose(TurntableTextures.materialChoice("copier-output"));require(off.get(gui).equals(oldOff)&&on.get(gui).equals(TurntableTextures.fromMaterialChoice(TurntableTextures.materialChoice("copier-output"))),"active selection preserves inactive artwork");
            javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,size[0],size[1]),"png",new File(output,"shot_surface_dialog_"+size[0]+".png"));
        }}finally{mc.player.openContainer=previous;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
        System.out.println("[vandorlabs][reprolab] surface-client PASS 16 actual native-artwork multi-cell Tile/Fit FBO frames compact GUI320/460 wheel/drag previous/next independent state selection");
    }
    private static void heldItem(Minecraft mc,File output)throws Exception{
        net.minecraft.item.ItemStack surface=new net.minecraft.item.ItemStack(net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface")),stone=new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.STONE);net.minecraft.client.renderer.block.model.IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(surface,mc.world,mc.player),reference=mc.getRenderItem().getItemModelWithOverrides(stone,mc.world,mc.player);int checked=0;
        require(model.getItemCameraTransforms().getTransform(net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.GUI).scale.x==.5F,"Surface padded GUI transform is retained");
        for(net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType type:new net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType[]{net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND,net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND,net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.GROUND,net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED}){
            net.minecraft.client.renderer.block.model.ItemTransformVec3f actual=model.getItemCameraTransforms().getTransform(type),nativeBlock=reference.getItemCameraTransforms().getTransform(type);require(actual.rotation.equals(nativeBlock.rotation)&&actual.translation.equals(nativeBlock.translation)&&actual.scale.equals(nativeBlock.scale),"Surface uses native cube_all camera transform "+type);
            BufferedImage image=itemFrame(mc,surface,type),baseline=itemFrame(mc,stone,type);int[] actualBounds=inkBounds(image),nativeBounds=inkBounds(baseline);require(actualBounds[4]>20,"actual held Surface geometry is visible "+type);for(int n=0;n<4;n++)require(Math.abs(actualBounds[n]-nativeBounds[n])<=1,"actual Surface held/ground/fixed footprint matches a normal block "+type);if(type==net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND)javax.imageio.ImageIO.write(image,"png",new File(output,"shot_surface_native_hand_size.png"));checked++;
        }
        System.out.println("[vandorlabs][reprolab] surface-held-size PASS actualcameraFBO="+checked+" nativecube_all first/third bothhands ground fixed; paddedGUIunchanged");
    }
    private static int[] inkBounds(BufferedImage image){int[] bounds={64,64,-1,-1,0};for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040){bounds[0]=Math.min(bounds[0],x);bounds[1]=Math.min(bounds[1],y);bounds[2]=Math.max(bounds[2],x);bounds[3]=Math.max(bounds[3],y);bounds[4]++;}return bounds;}
    private static BufferedImage itemFrame(Minecraft mc,net.minecraft.item.ItemStack item,net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType type){
        Framebuffer frame=new Framebuffer(64,64,true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.8,.8,-.8,.8,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableDepth();GlStateManager.disableBlend();GlStateManager.enableAlpha();GlStateManager.alphaFunc(516,.1F);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItem(item,type);RenderHelper.disableStandardItemLighting();return net.minecraft.util.ScreenShotHelper.createScreenshot(64,64,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void damagedPads(Minecraft mc,File output)throws Exception{
        int cases=0;net.minecraft.block.Block surface=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface"),control=net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_controller");
        for(net.minecraft.util.EnumFacing normal:net.minecraft.util.EnumFacing.values())for(boolean removeRoot:new boolean[]{false,true}){
            com.vandorlabs.turntable.TurntableGeometry.Face basis=com.vandorlabs.turntable.TurntableGeometry.Face.valueOf(normal.name());BlockPos seed=new BlockPos(3,3,3),owner=seed.offset(normal.getOpposite(),2);java.util.List<BlockPos> cells=new java.util.ArrayList<>();for(int u=0;u<2;u++)for(int v=0;v<2;v++){double[] delta=basis.point(u,v,0);cells.add(seed.add(delta[0],delta[1],delta[2]));}
            com.vandorlabs.surface.SquareLayout area=com.vandorlabs.surface.SquareLayout.of(cells,normal);BlockPos removed=removeRoot?area.root:cells.stream().filter(p->!p.equals(area.root)).findFirst().get();
            TileEntityTurntableController controller=new TileEntityTurntableController();controller.setPos(owner);controller.enabled=false;controller.captureId=java.util.UUID.randomUUID();controller.layout=com.vandorlabs.turntable.TurntableSurfaceLayout.read(area.write());java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> snapshot=new java.util.ArrayList<>();snapshot.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(owner,control.getDefaultState(),controller.writeToNBT(new NBTTagCompound())));
            for(BlockPos cell:cells)if(!cell.equals(removed)){TileEntityTurntableSurface tile=new TileEntityTurntableSurface();tile.setPos(cell);tile.owner=owner;tile.ownerKey=controller.key;tile.surfaceOff="landing";tile.setGroup(area);snapshot.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(cell,surface.getDefaultState().withProperty(com.vandorlabs.blocks.BlockTurntableSurface.FACING,normal),tile.writeToNBT(new NBTTagCompound())));}
            com.vandorlabs.vehicle.VehicleWorld view=new com.vandorlabs.vehicle.VehicleWorld(mc.world,com.vandorlabs.vehicle.VehicleStructure.display(snapshot));
            for(BlockPos cell:cells)if(!cell.equals(removed)){TileEntityTurntableSurface tile=(TileEntityTurntableSurface)view.getTileEntity(cell);require(tile.locked()&&tile.controller()==null&&tile.appearanceLayout().size==1&&tile.appearanceLayout().root.equals(cell),"damaged recovery claim keeps each remaining cell protected and self-rendered "+normal+removeRoot);BufferedImage frame=damagedFace(mc,tile,normal);int visible=0;for(int color:frame.getRGB(0,0,64,64,null,0,64))if((color&0xffffff)!=0x203040)visible++;require(visible>1800,"actual damaged pad TESR remains opaque and visible after "+(removeRoot?"root":"member")+" removal "+normal+" pixels="+visible);cases++;if(removeRoot&&cell.equals(cells.stream().filter(p->!p.equals(removed)).findFirst().get()))javax.imageio.ImageIO.write(frame,"png",new File(output,"shot_surface_damaged_"+normal.getName()+".png"));}
        }
        System.out.println("[vandorlabs][reprolab] surface-damaged-client PASS actual-TESR-FBO-cases="+cases+" six normals root/member removal retained protection self-root opaque faces");
    }
    static BufferedImage damagedFace(Minecraft mc,TileEntityTurntableSurface tile,net.minecraft.util.EnumFacing normal){
        Framebuffer frame=new Framebuffer(64,64,true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.6,.6,-.6,.6,-4,4);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);mc.entityRenderer.disableLightmap();if(normal.getAxis()==net.minecraft.util.EnumFacing.Axis.Y)GlStateManager.rotate(90,1,0,0);else if(normal.getAxis()==net.minecraft.util.EnumFacing.Axis.X)GlStateManager.rotate(90,0,1,0);
            TETurntableSurface renderer=new TETurntableSurface();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);renderer.render(tile,-.5,-.5,-.5,0,-1,1);return net.minecraft.util.ScreenShotHelper.createScreenshot(64,64,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static BufferedImage render(Minecraft mc,String texture,int mode,boolean dirty){
        TileEntityTurntableSurface tile=new TileEntityTurntableSurface();tile.setWorld(mc.world);tile.setPos(new BlockPos(540,110,180));NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());tag.setString("SurfaceOff",texture);tag.setInteger("SurfaceLayout",mode);tag.setInteger("SurfaceGroupSize",4);tile.readFromNBT(tag);require(tile.appearanceLayout().size==4&&tile.appearanceLayout().root.equals(tile.getPos()),"FBO fixture invokes actual grouped root renderer");
        require(mc.getTextureMapBlocks().getAtlasSprite(TurntableTextures.texture(texture,true)).getIconWidth()==512,"actual native Surface atlas retains512 master "+texture);
        Framebuffer frame=new Framebuffer(256,256,true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-2.2,2.2,-2.2,2.2,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,.1F);GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);GlStateManager.colorMask(true,true,true,true);mc.entityRenderer.disableLightmap();GlStateManager.rotate(90,1,0,0);
            if(dirty){GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,1);GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_GREATER);GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.disableTexture2D();Tessellator.getInstance().getBuffer().setTranslation(99,99,99);}
            TETurntableSurface renderer=new TETurntableSurface();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);renderer.render(tile,-2,-1,-2,0,-1,1);org.lwjgl.opengl.GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private SurfaceClientChecks(){}
}
