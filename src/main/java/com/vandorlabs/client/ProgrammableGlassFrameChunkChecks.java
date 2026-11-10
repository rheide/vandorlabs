package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.lwjgl.opengl.GL11;

/** Focused real-section publication and legacy/chunk image checks, entered only by ReproLab. */
final class ProgrammableGlassFrameChunkChecks {
    private static final BlockPos ORIGIN=new BlockPos(168,7,80);
    private static int tick,phase,phaseTick,bolted,plain,lit;
    private static Object previousMesh;
    private static int editedChoice;
    private static final boolean NATIVE_ONLY=Boolean.getBoolean("vandorlabs.frameChunkNativeOnly");

    static boolean tick(Minecraft mc,File output)throws Exception {
        tick++;mc.gameSettings.pauseOnLostFocus=false;
        if(tick==1){
            bolted=choice("frames/frames-screen-full/frame_bolted_1254");
            lit=choice("frames/frames-door-window/lights-cyan-1");
            plain=findPlain();
            mc.displayGuiScreen(null);mc.gameSettings.renderDistanceChunks=4;mc.gameSettings.hideGUI=true;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if(tick==40){
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
                World world=player.world;
                for(int x=-2;x<15;x++)for(int y=-2;y<5;y++)for(int z=-3;z<4;z++)world.setBlockToAir(ORIGIN.add(x,y,z));
                for(int x=-2;x<15;x++)for(int z=-3;z<4;z++)world.setBlockState(ORIGIN.add(x,-1,z),Blocks.STONE.getDefaultState(),3);
                int[] choices={bolted,plain,lit,-1};
                for(int i=0;i<choices.length;i++)place(world,ORIGIN.east(i*3),choices[i],i%3,false);
                player.capabilities.allowFlying=true;player.capabilities.isFlying=true;player.sendPlayerAbilities();
                player.connection.setPlayerLocation(172.5,9,73,0,15);
            }).get();
            mc.player.capabilities.allowFlying=true;mc.player.capabilities.isFlying=true;
            mc.player.setPositionAndRotation(172.5,9,73,0,15);phase=1;phaseTick=tick;
        }
        if(phase==0)return false;
        require(tick-phaseTick<500,"timed out in publication phase "+phase);
        if(phase==1){
            if(!tilesPresent(mc))return false;
            GlassFrameChunks.reload();
            for(int i=0;i<3;i++)require(!GlassFrameChunks.rendered(tile(mc,i)),"reload accepted a previous section upload");
            GlassFrameChunks.flush();phase=2;phaseTick=tick;return false;
        }
        if(phase==2){
            GlassFrameChunks.flush();if(!uploaded(mc))return false;
            require(tile(mc,3).frameChunkMesh==null&&!GlassFrameChunks.rendered(tile(mc,3)),"default frame stopped using the stock model");
            GlassFrameGeometryChecks.run(mc,output);
            if(!NATIVE_ONLY){
                pixelChecks(mc,output,tile(mc,0),"bolted");
                pixelChecks(mc,output,tile(mc,1),"plain");
                pixelChecks(mc,output,tile(mc,2),"emissive");
            }
            previousMesh=tile(mc,0).frameChunkMesh;editedChoice=plain;
            mc.getIntegratedServer().addScheduledTask(()->edit(mc.getIntegratedServer().getEntityWorld(),ORIGIN,editedChoice)).get();
            // Publish the authoritative setting on this client turn, before a render can upload it.
            edit(mc.world,ORIGIN,editedChoice);GlassFrameChunks.flush();
            require(tile(mc,0).frameChunkMesh!=previousMesh,"frame edit retained stale geometry");
            require(!GlassFrameChunks.rendered(tile(mc,0)),"new frame accepted the old GPU upload");
            phase=3;phaseTick=tick;return false;
        }
        if(phase==3){
            if(tile(mc,0).frameAppearance.front!=editedChoice)return false;
            GlassFrameChunks.flush();
            previousMesh=tile(mc,0).frameChunkMesh;phase=4;phaseTick=tick;return false;
        }
        if(phase==4){
            GlassFrameChunks.flush();if(!uploaded(mc))return false;
            mc.getIntegratedServer().addScheduledTask(()->place(mc.getIntegratedServer().getEntityWorld(),ORIGIN.east(),plain,0,false)).get();
            phase=5;phaseTick=tick;return false;
        }
        if(phase==5){
            if(!(mc.world.getTileEntity(ORIGIN.east()) instanceof TileEntityProgrammableGlass))return false;
            GlassFrameChunks.flush();
            require(tile(mc,0).frameChunkMesh!=previousMesh,"joined neighbour retained the exposed frame rail");
            phase=6;phaseTick=tick;return false;
        }
        if(phase==6){
            GlassFrameChunks.flush();if(!uploaded(mc))return false;
            previousMesh=tile(mc,0).frameChunkMesh;
            mc.refreshResources();
            require(!GlassFrameChunks.rendered(tile(mc,0)),"atlas reload retained a ready publication");
            GlassFrameChunks.flush();phase=7;phaseTick=tick;return false;
        }
        if(phase==7){
            GlassFrameChunks.flush();if(!uploaded(mc))return false;
            require(tile(mc,0).frameChunkMesh!=previousMesh,"atlas reload retained old sprite records");
            ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"glass-frame-chunks-live.png"));
            System.out.println("[vandorlabs][reprolab] glass-frame-chunks PASS initial pending/upload, frame edit, neighbour join, atlas reload, stock delegate, geometry"+(NATIVE_ONLY?" native shader run":" and framebuffer parity"));
            return true;
        }
        return false;
    }

    private static boolean tilesPresent(Minecraft mc){for(int i=0;i<4;i++)if(!(mc.world.getTileEntity(ORIGIN.east(i*3)) instanceof TileEntityProgrammableGlass))return false;return true;}
    private static TileEntityProgrammableGlass tile(Minecraft mc,int i){return (TileEntityProgrammableGlass)mc.world.getTileEntity(ORIGIN.east(i*3));}
    private static boolean uploaded(Minecraft mc){for(int i=0;i<3;i++)if(!GlassFrameChunks.rendered(tile(mc,i)))return false;return true;}
    private static void place(World world,BlockPos pos,int choice,int depth,boolean rotated){
        Block block=Block.getBlockFromName("vandorlabs:programmable_glass");
        world.setBlockState(pos,block.getDefaultState().withProperty(BlockGlassWall.DEPTH,depth).withProperty(BlockGlassWall.ROTATED,rotated),3);
        edit(world,pos,choice);
    }
    private static void edit(World world,BlockPos pos,int choice){
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)world.getTileEntity(pos);
        tile.frameAppearance.front=choice;tile.setAppearance(1,true);tile.markDirty();
        IBlockState state=world.getBlockState(pos);world.notifyBlockUpdate(pos,state,state,3);world.checkLight(pos);
    }
    private static int choice(String source){int result=ScreenHousingTextures.screenIndex(source);require(result>0,"missing frame fixture "+source);return result;}
    private static int findPlain(){
        for(int i=ScreenHousingTextures.LEGACY_COUNT;i<ScreenHousingTextures.BUILTIN_COUNT;i++){
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(i);
            if(entry!=null&&entry.has("source")&&entry.get("source").getAsString().startsWith("frames/")&&entry.get("source").getAsString().contains("plain"))return i;
        }
        throw new IllegalStateException("No plain frame fixture");
    }

    private static void pixelChecks(Minecraft mc,File output,TileEntityProgrammableGlass tile,String name)throws Exception {
        int cases=0;
        for(int block:new int[]{2,15}){
            mc.world.setLightFor(EnumSkyBlock.BLOCK,tile.getPos(),block);mc.world.setLightFor(EnumSkyBlock.SKY,tile.getPos(),0);
            for(int overlay:new int[]{0,1,2}){
                BufferedImage legacy=capture(mc,tile,false,overlay),chunk=capture(mc,tile,true,overlay);
                int changed=0,max=0;long difference=0;
                for(int y=0;y<legacy.getHeight();y++)for(int x=0;x<legacy.getWidth();x++){
                    int a=legacy.getRGB(x,y),b=chunk.getRGB(x,y),delta=0;
                    for(int shift:new int[]{0,8,16}){int d=Math.abs((a>>shift&255)-(b>>shift&255));difference+=d;delta=Math.max(delta,d);}
                    if(delta>2)changed++;max=Math.max(max,delta);
                }
                String label="glass-frame-"+name+"-light"+block+"-pane"+overlay;
                ImageIO.write(legacy,"png",new File(output,label+"-legacy.png"));ImageIO.write(chunk,"png",new File(output,label+"-chunk.png"));
                double mean=difference/(3D*legacy.getWidth()*legacy.getHeight());
                System.out.println("[vandorlabs][reprolab] glass-frame-pixels "+label+" changed="+changed+" max="+max+" mean="+mean);
                require(changed<=64&&mean<=.35,"frame image changed: "+label+" pixels="+changed+" max="+max+" mean="+mean);cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] glass-frame-pixels PASS "+name+" cases="+cases+" bright/dark near/far transparent pane");
    }

    private static BufferedImage capture(Minecraft mc,TileEntityProgrammableGlass tile,boolean nativeFrame,int overlay)throws Exception {
        final int pixels=192;Framebuffer target=new Framebuffer(pixels,pixels,true);
        int previousPass=MinecraftForgeClient.getRenderPass();BlockRenderLayer previousLayer=MinecraftForgeClient.getRenderLayer();
        DynamicTexture lightmap=new DynamicTexture(16,16);int[] values=lightmap.getTextureData();
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)values[y*16+x]=0xff000000|Math.min(255,x*15+y*2)*0x010101;
        lightmap.updateDynamicTexture();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.05,1.05,-.05,1.05,-32,32);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            target.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.13F,.19F,.25F,1);GlStateManager.clear(16640);
            GlStateManager.enableDepth();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.color(1,1,1,1);
            mc.entityRenderer.enableLightmap();GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);GlStateManager.bindTexture(lightmap.getGlTextureId());GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);ForgeHooksClient.setRenderPass(0);
            IBlockState state=mc.world.getBlockState(tile.getPos());
            if(nativeFrame){
                int light=tile.getWorld().getCombinedLight(tile.getPos(),tile.frameAppearance.light());if(tile.frameAppearance.light()>0)light=(light&0xffff0000)|240;
                IBlockState actual=state.getActualState(mc.world,tile.getPos());
                IBlockState extended=((IExtendedBlockState)actual).withProperty(BlockProgrammableGlass.FRAME_MESH,tile.frameChunkMesh).withProperty(BlockProgrammableGlass.FRAME_LIGHT,light);
                IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);
                for(BlockRenderLayer layer:new BlockRenderLayer[]{BlockRenderLayer.CUTOUT_MIPPED,BlockRenderLayer.TRANSLUCENT}){
                    ForgeHooksClient.setRenderLayer(layer);GlStateManager.enableCull();GlStateManager.depthMask(true);
                    if(layer==BlockRenderLayer.TRANSLUCENT){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);}else GlStateManager.disableBlend();
                    BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);b.setTranslation(-tile.getPos().getX(),-tile.getPos().getY(),-tile.getPos().getZ());
                    mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(mc.world,model,extended,tile.getPos(),b,false);
                    if(layer==BlockRenderLayer.TRANSLUCENT)b.sortVertexData(.5F,.5F,10);
                    Tessellator.getInstance().draw();b.setTranslation(0,0,0);
                }
            }else{
                TEProgrammableGlass renderer=new TEProgrammableGlass();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
                Method method=TEProgrammableGlass.class.getDeclaredMethod("renderSelectedFrame",TileEntityProgrammableGlass.class,IBlockState.class,double.class,double.class,double.class);method.setAccessible(true);method.invoke(renderer,tile,state,0D,0D,0D);
            }
            if(overlay!=0){
                mc.entityRenderer.disableLightmap();GlStateManager.disableLighting();GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);
                BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_COLOR);
                double z=overlay==1?1.2:-.2;
                b.pos(0,0,z).color(80,170,230,100).endVertex();b.pos(1,0,z).color(80,170,230,100).endVertex();b.pos(1,1,z).color(80,170,230,100).endVertex();b.pos(0,1,z).color(80,170,230,100).endVertex();Tessellator.getInstance().draw();
            }
            GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(pixels,pixels,target);
        }finally{
            ForgeHooksClient.setRenderPass(previousPass);ForgeHooksClient.setRenderLayer(previousLayer);Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
            mc.entityRenderer.disableLightmap();lightmap.deleteGlTexture();GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Glass frame chunks: "+message);}
}
