package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.*;

/** Atlas-only adaptation: retain native standalone maps and user resource-pack priority. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ShaderAtlasResources {
    private static Scope loading;
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void before(TextureStitchEvent.Pre event){
        try{if(!Class.forName("net.optifine.shaders.Shaders").getField("shaderPackLoaded").getBoolean(null))return;}catch(ClassNotFoundException absent){return;}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot inspect optional shader state",error);}
        if(loading!=null)loading.close();loading=open();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void after(TextureStitchEvent.Post event){if(loading!=null){loading.close();loading=null;}}
    @SubscribeEvent public static void cleanup(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event){if(loading!=null&&event.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END){loading.close();loading=null;}}
    static Scope open(){return new Scope(packs());}
    static List<IResourcePack> packs(){
        SimpleReloadableResourceManager manager=(SimpleReloadableResourceManager)Minecraft.getMinecraft().getResourceManager();
        Map<String,FallbackResourceManager> domains=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(SimpleReloadableResourceManager.class,manager,"domainResourceManagers","field_110548_a");
        FallbackResourceManager domain=domains.get("vandorlabs");if(domain==null)throw new IllegalStateException("Missing mod texture domain");
        List<IResourcePack> packs=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(FallbackResourceManager.class,domain,"resourcePacks","field_110540_a");
        return packs;
    }
    static final class Scope implements AutoCloseable {
        final List<IResourcePack> active;final AtlasPack pack;
        Scope(List<IResourcePack> active){this.active=active;pack=new AtlasPack(new ArrayList<>(active));active.add(pack);}
        @Override public void close(){active.remove(pack);}
    }
    private static final class AtlasPack implements IResourcePack {
        private final List<IResourcePack> sources;
        AtlasPack(List<IResourcePack> sources){this.sources=sources;}
        private IResourcePack source(ResourceLocation id){
            String path=id.getResourcePath();if(!"vandorlabs".equals(id.getResourceDomain())||!path.startsWith("textures/")||!(path.endsWith("_n.png")||path.endsWith("_s.png")))return null;
            for(int i=sources.size()-1;i>=0;i--)if(sources.get(i).resourceExists(id))return sources.get(i);return null;
        }
        public boolean resourceExists(ResourceLocation id){return source(id)!=null;}
        public InputStream getInputStream(ResourceLocation id)throws IOException{
            IResourcePack source=source(id);if(source==null)throw new FileNotFoundException(id.toString());BufferedInputStream input=new BufferedInputStream(source.getInputStream(id));
            input.mark(32);byte[] header=new byte[24];int count=0,n;while(count<header.length&&(n=input.read(header,count,header.length-count))>0)count+=n;input.reset();
            if(count!=24||header[0]!=(byte)137||header[1]!='P'||header[2]!='N'||header[3]!='G')return input;
            int width=integer(header,16),height=integer(header,20);String path=id.getResourcePath();String spriteName=id.getResourceDomain()+":"+path.substring(9,path.length()-6);if(width<=0||height<=0||!UnifiedTextureSprites.shaderCanvasNeeded(spriteName,width,height))return input;
            try(InputStream consumed=input){BufferedImage image=ImageIO.read(consumed);if(image==null)throw new IOException("Invalid shader PNG: "+id);BufferedImage square=UnifiedTextureSprites.shaderCanvas(spriteName,image);ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(square,"PNG",bytes);return new ByteArrayInputStream(bytes.toByteArray());}
        }
        public Set<String> getResourceDomains(){return Collections.singleton("vandorlabs");}
        public <T extends IMetadataSection>T getPackMetadata(MetadataSerializer serializer,String section){return null;}
        public BufferedImage getPackImage(){return new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);}
        public String getPackName(){return "Vandor Labs atlas shader maps";}
    }
    private static int integer(byte[] bytes,int at){return (bytes[at]&255)<<24|(bytes[at+1]&255)<<16|(bytes[at+2]&255)<<8|bytes[at+3]&255;}
    private ShaderAtlasResources(){}
}
