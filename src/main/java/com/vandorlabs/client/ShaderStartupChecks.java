package com.vandorlabs.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import java.io.*;
import java.lang.reflect.*;
import javax.imageio.ImageIO;

/** Real OptiFine startup, rectangular companion uploads, reload and rendered world frame. */
final class ShaderStartupChecks {
    private static int ticks,stage,sceneFrames;
    private static boolean done;
    private static final net.minecraft.util.math.BlockPos POS=new net.minecraft.util.math.BlockPos(96,80,96);
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(done)return GlassNativeSceneChecks.tick(mc,output);if(++ticks<20)return false;
        Class<?> shaders=Class.forName("net.optifine.shaders.Shaders");
        if(!shaders.getField("shaderPackLoaded").getBoolean(null))throw new IllegalStateException("Required shaderpack did not load");
        String name=(String)shaders.getMethod("getShaderPackName").invoke(null);
        if(!shaders.getField("configSpecularMap").getBoolean(null))throw new IllegalStateException("Specular maps disabled: regression would be hidden");
        if(stage==0){
            int count=rectangles(mc);System.out.println("[vandorlabs][reprolab] shader-startup initial-atlas PASS pack="+name+" rectangular-OptiFine-uploads="+count);
            mc.gameSettings.pauseOnLostFocus=false;mc.player.closeScreen();
            mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.entity.player.EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());player.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+2,POS.getZ()-4,0,38);net.minecraft.block.Block glass=net.minecraft.block.Block.getBlockFromName("vandorlabs:regular_canopy_glass");player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(glass,8));for(int x=0;x<2;x++)for(int z=0;z<2;z++){net.minecraft.util.math.BlockPos support=POS.add(x,-1,z);player.world.setBlockState(support,net.minecraft.init.Blocks.STONE.getDefaultState(),3);if(player.getHeldItemMainhand().onItemUse(player,player.world,support,net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.util.EnumFacing.UP,.5F,1,.5F)!=net.minecraft.util.EnumActionResult.SUCCESS)throw new IllegalStateException("Native shader canopy placement failed");}player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);}).get();
            mc.refreshResources();stage=1;ticks=0;return false;
        }
        if(ticks>300)throw new IllegalStateException("Shader native-canopy mesh was not published; block="+mc.world.getBlockState(POS)+" tile="+mc.world.getTileEntity(POS)+" player="+mc.player.getPosition());
        com.vandorlabs.tiles.TileEntityCanopy tile=com.vandorlabs.tiles.TileEntityCanopy.at(mc.world,POS);if(tile==null||!(tile.chunkMesh instanceof FixedCanopyModel.Mesh))return false;
        FixedCanopyModel.Mesh mesh=(FixedCanopyModel.Mesh)tile.chunkMesh;
        if(mesh.solid.isEmpty()||mesh.solid.get(0).getFormat().getNextOffset()!=net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK.getNextOffset())throw new IllegalStateException("Shader canopy mesh lost its vertex format");
        mc.player.rotationYaw=mc.player.prevRotationYaw=0;mc.player.rotationPitch=mc.player.prevRotationPitch=38;
        mc.entityRenderer.updateCameraAndRender(1,System.nanoTime());mc.renderGlobal.updateChunks(System.nanoTime()+50000000L);
        if(++sceneFrames<20)return false;
        net.minecraft.client.renderer.ViewFrustum frustum=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.renderer.RenderGlobal.class,mc.renderGlobal,"viewFrustum","field_175008_n");net.minecraft.client.renderer.chunk.RenderChunk chunk=chunk(frustum);
        if(chunk==null||chunk.getCompiledChunk().isLayerEmpty(net.minecraft.util.BlockRenderLayer.SOLID)||chunk.getCompiledChunk().isLayerEmpty(net.minecraft.util.BlockRenderLayer.TRANSLUCENT))return false;
        mc.entityRenderer.updateCameraAndRender(1,System.nanoTime());java.awt.image.BufferedImage frame=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int edges=0;
        for(int y=frame.getHeight()/2-120;y<frame.getHeight()/2+120;y++)for(int x=frame.getWidth()/2-160;x<frame.getWidth()/2+160;x++){int a=frame.getRGB(x,y),b=frame.getRGB(x+1,y);if(Math.abs((a&255)-(b&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a>>16&255)-(b>>16&255))>90)edges++;}
        if(edges<80)return false;
        int count=rectangles(mc);ImageIO.write(frame,"png",new File(output,"shader-startup-world.png"));
        done=true;System.out.println("[vandorlabs][reprolab] shader-startup PASS pack="+name+" rectangular-OptiFine-uploads="+count+" initial-atlas resource-reload native-canopy-world-frame renderer="+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));return false;
    }
    private static net.minecraft.client.renderer.chunk.RenderChunk chunk(net.minecraft.client.renderer.ViewFrustum frustum)throws Exception{
        for(Method method:net.minecraft.client.renderer.ViewFrustum.class.getDeclaredMethods())if(method.getReturnType()==net.minecraft.client.renderer.chunk.RenderChunk.class&&java.util.Arrays.equals(method.getParameterTypes(),new Class[]{net.minecraft.util.math.BlockPos.class})){method.setAccessible(true);return (net.minecraft.client.renderer.chunk.RenderChunk)method.invoke(frustum,POS);}throw new IllegalStateException("Missing native chunk lookup");
    }
    private static int rectangles(Minecraft mc)throws Exception{
        Method upload=Class.forName("net.optifine.shaders.ShadersTex").getMethod("readImageAndMipmaps",net.minecraft.client.renderer.texture.TextureMap.class,String.class,int.class,int.class,int.class,boolean.class,int.class);
        JsonObject entries;try(Reader reader=new InputStreamReader(ShaderStartupChecks.class.getResourceAsStream("/assets/vandorlabs/data/pbr_materials.json"),"UTF-8")){entries=new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("maps");}
        int count=0;boolean reported=false;
        try(ShaderAtlasResources.Scope scope=ShaderAtlasResources.open()){
        for(java.util.Map.Entry<String,JsonElement> e:entries.entrySet()){
            JsonArray size=e.getValue().getAsJsonObject().getAsJsonArray("size");if(size.get(0).getAsInt()<=size.get(1).getAsInt())continue;
            String name="vandorlabs:"+e.getKey().replaceFirst("^textures/","").replaceFirst("\\.png$","");TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(name);if(sprite==mc.getTextureMapBlocks().getMissingSprite())continue;
            try{int[][] pixels=(int[][])upload.invoke(null,mc.getTextureMapBlocks(),name+"_s",sprite.getIconWidth(),sprite.getIconHeight(),1,false,0xff0a0000);if(pixels.length!=1||pixels[0].length!=sprite.getIconWidth()*sprite.getIconHeight())throw new IllegalStateException("Invalid shader companion upload "+name);}catch(InvocationTargetException failed){throw new IllegalStateException("OptiFine rectangular shader upload failed: "+name,failed.getCause());}
            count++;if(name.equals("vandorlabs:blocks/console_inputs/large27_cool_half_viewscreen_off"))reported=true;
        }
        }finally{mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);}
        standalone(mc);override(mc,upload);
        if(count<20||!reported)throw new IllegalStateException("Shader startup omitted reported half Viewscreen fixture; count="+count+" reported="+reported);
        return count;
    }
    private static void override(Minecraft mc,Method upload)throws Exception{
        final ResourceLocation spec=new ResourceLocation("vandorlabs:textures/blocks/console_inputs/large27_cool_half_viewscreen_off_s.png");java.awt.image.BufferedImage source=new java.awt.image.BufferedImage(512,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);int[] color=new int[512*256];java.util.Arrays.fill(color,0xff450a00);source.setRGB(0,0,512,256,color,0,512);source.setRGB(511,255,0xff990a00);ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(source,"PNG",output);final byte[] png=output.toByteArray();
        net.minecraft.client.resources.IResourcePack marker=new net.minecraft.client.resources.IResourcePack(){
            public boolean resourceExists(ResourceLocation id){return spec.equals(id);}
            public InputStream getInputStream(ResourceLocation id)throws IOException{if(!resourceExists(id))throw new FileNotFoundException(id.toString());return new ByteArrayInputStream(png);}
            public java.util.Set<String> getResourceDomains(){return java.util.Collections.singleton("vandorlabs");}
            public <T extends net.minecraft.client.resources.data.IMetadataSection>T getPackMetadata(net.minecraft.client.resources.data.MetadataSerializer serializer,String section){return null;}
            public java.awt.image.BufferedImage getPackImage(){return source;}
            public String getPackName(){return "Shader override regression fixture";}
        };
        java.util.List<net.minecraft.client.resources.IResourcePack> packs=ShaderAtlasResources.packs();packs.add(marker);
        try{
            try(ShaderAtlasResources.Scope scope=ShaderAtlasResources.open()){int[][] pixels=(int[][])upload.invoke(null,mc.getTextureMapBlocks(),"vandorlabs:blocks/console_inputs/large27_cool_half_viewscreen_off_s",512,512,1,false,0xff000000);if(pixels[0][0]!=0xff450a00||pixels[0][512*512-1]!=0xff990a00)throw new IllegalStateException("Atlas adaptation lost user resource-pack priority or edge pixels");}
            try(InputStream stream=mc.getResourceManager().getResource(spec).getInputStream()){java.awt.image.BufferedImage nativeMap=ImageIO.read(stream);if(nativeMap.getHeight()!=256||nativeMap.getRGB(511,255)!=0xff990a00)throw new IllegalStateException("Atlas override leaked into native resources");}
        }finally{packs.remove(marker);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);}
    }
    private static void standalone(Minecraft mc)throws Exception{
        ResourceLocation base=new ResourceLocation("vandorlabs:textures/blocks/console_inputs/large27_cool_half_viewscreen_off.png");ResourceLocation spec=new ResourceLocation("vandorlabs:textures/blocks/console_inputs/large27_cool_half_viewscreen_off_s.png");
        java.awt.image.BufferedImage image;try(InputStream stream=mc.getResourceManager().getResource(spec).getInputStream()){image=ImageIO.read(stream);}
        if(image.getWidth()!=512||image.getHeight()!=256)throw new IllegalStateException("Atlas padding escaped into standalone maps");
        int[] maps=new int[512*256*3];Class.forName("net.optifine.shaders.ShadersTex").getMethod("loadNSMap",net.minecraft.client.resources.IResourceManager.class,ResourceLocation.class,int.class,int.class,int[].class).invoke(null,mc.getResourceManager(),base,512,256,maps);
        if(maps[512*256*2]!=image.getRGB(0,0))throw new IllegalStateException("OptiFine rejected the native standalone companion");
    }
    private ShaderStartupChecks(){}
}
