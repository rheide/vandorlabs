package com.vandorlabs.client;

import com.vandorlabs.render.GlassPanePixels;
import com.vandorlabs.tiles.GlassTextures;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Atlas-time authored variants. No texture decoding or alpha changes happen per frame. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneTextures {
    private static volatile Selection[] selections=new Selection[0];
    private static List<Variant> prepared=Collections.emptyList();
    private static Scope loading;

    public static final class Selection {
        public final TextureAtlasSprite sprite;
        public final boolean singleSurface,terrainAlphaSafe;
        Selection(TextureAtlasSprite sprite,Variant variant){
            this.sprite=sprite;singleSurface=variant.single;
            terrainAlphaSafe=GlassPanePolicy.terrainAlphaSafe(variant.minimumAlpha,variant.single);
        }
    }
    private static final class Variant {
        final int index;
        final ResourceLocation sprite,source;
        final boolean single,smooth;
        final int minimumAlpha;
        final byte[] diffuse;
        Variant(int shade,int tint,BufferedImage image)throws IOException{
            index=shade*GlassTextures.TINT_COUNT+tint;
            sprite=new ResourceLocation("vandorlabs","blocks/native_glass/"+shade+"/"+tint);
            source=new ResourceLocation(GlassTextures.fullTexture(shade,1,tint));
            single=GlassPanePolicy.singleSurface();smooth=GlassPanePolicy.smoothTextures();
            int[] pixels=smooth?new int[]{GlassPanePixels.dominant(image.getRGB(0,0,image.getWidth(),
                    Math.min(image.getWidth(),image.getHeight()),null,0,image.getWidth()))}
                    :image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
            int minimum=256;for(int pixel:pixels){int a=pixel>>>24;if(a>0&&a<minimum)minimum=a;}
            minimumAlpha=minimum==256?0:minimum;
            diffuse=encode(transform(image,single,smooth));
        }
    }

    public static Selection select(int shade,int tint){
        Selection[] current=selections;int index=GlassTextures.clamp(shade)*GlassTextures.TINT_COUNT+GlassTextures.clampTint(tint);
        return index<current.length?current[index]:null;
    }

    @SubscribeEvent(priority=EventPriority.HIGH) public static void before(TextureStitchEvent.Pre event){
        if(event.getMap()!=Minecraft.getMinecraft().getTextureMapBlocks())return;
        GlassPaneTerrain.reload();GlassPaneRetained.reload();GlassFrameRetained.reload();PortholeRetained.reload();close();selections=new Selection[0];prepared=Collections.emptyList();
        if(!GlassPanePolicy.staticEnabled())return;
        List<IResourcePack> active=ShaderAtlasResources.packs();
        Scope scope=new Scope(active);List<Variant> next=new ArrayList<>();
        try{
            for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.TINT_COUNT;tint++){
                ResourceLocation source=new ResourceLocation(GlassTextures.fullTexture(shade,1,tint));
                BufferedImage image;
                try(InputStream input=scope.pack.openSource(source)){image=ImageIO.read(input);}
                if(image==null)throw new IOException("Invalid glass PNG: "+source);
                Variant variant=new Variant(shade,tint,image);next.add(variant);scope.pack.add(variant);
                event.getMap().registerSprite(variant.sprite);
            }
            active.add(scope.pack);loading=scope;prepared=next;
        }catch(IOException|RuntimeException failure){scope.close();throw new IllegalStateException("Native glass atlas preparation failed",failure);}
    }

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(TextureStitchEvent.Post event){
        if(event.getMap()!=Minecraft.getMinecraft().getTextureMapBlocks())return;
        Selection[] next=new Selection[GlassTextures.IDS.length*GlassTextures.TINT_COUNT];
        for(Variant variant:prepared){
            TextureAtlasSprite sprite=event.getMap().getAtlasSprite(variant.sprite.toString());
            if(sprite!=event.getMap().getMissingSprite())next[variant.index]=new Selection(sprite,variant);
        }
        selections=next;prepared=Collections.emptyList();close();GlassPaneTerrain.reload();GlassPaneRetained.reload();GlassFrameRetained.reload();PortholeRetained.reload();
    }
    @SubscribeEvent public static void cleanup(TickEvent.ClientTickEvent event){
        if(loading!=null&&event.phase==TickEvent.Phase.END){close();prepared=Collections.emptyList();}
    }
    private static void close(){if(loading!=null){loading.close();loading=null;}}

    /** Pure pixel transform used by the atlas loader and focused checks. */
    static BufferedImage transform(BufferedImage image,boolean single,boolean smooth){
        int width=image.getWidth(),height=smooth?width:image.getHeight();
        int[] pixels=smooth?new int[width*height]:image.getRGB(0,0,width,height,null,0,width);
        if(smooth)java.util.Arrays.fill(pixels,GlassPanePixels.dominant(image.getRGB(0,0,width,Math.min(width,image.getHeight()),null,0,width)));
        if(single)for(int i=0;i<pixels.length;i++)pixels[i]=GlassPanePixels.single(pixels[i]);
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,width,height,pixels,0,width);return result;
    }
    private static byte[] encode(BufferedImage image)throws IOException{
        ByteArrayOutputStream output=new ByteArrayOutputStream();if(!ImageIO.write(image,"PNG",output))throw new IOException("PNG encoder unavailable");return output.toByteArray();
    }

    private static final class Scope implements AutoCloseable {
        final List<IResourcePack> active;final VariantPack pack;
        Scope(List<IResourcePack> active){this.active=active;pack=new VariantPack(new ArrayList<>(active));}
        public void close(){active.remove(pack);}
    }
    private static final class Asset {
        final ResourceLocation source;final byte[] bytes;final boolean smooth;
        Asset(ResourceLocation source,byte[] bytes,boolean smooth){this.source=source;this.bytes=bytes;this.smooth=smooth;}
    }
    private static final class VariantPack implements IResourcePack {
        private final List<IResourcePack> sources;
        private final Map<ResourceLocation,Asset> assets=new HashMap<>();
        VariantPack(List<IResourcePack> sources){this.sources=sources;}
        private IResourcePack source(ResourceLocation id){
            for(int i=sources.size()-1;i>=0;i--)if(sources.get(i).resourceExists(id))return sources.get(i);return null;
        }
        InputStream openSource(ResourceLocation id)throws IOException{
            IResourcePack pack=source(id);if(pack==null)throw new FileNotFoundException(id.toString());return pack.getInputStream(id);
        }
        void add(Variant variant){
            String path="textures/"+variant.sprite.getResourcePath(),original=variant.source.getResourcePath();
            assets.put(new ResourceLocation("vandorlabs",path+".png"),new Asset(variant.source,variant.diffuse,false));
            if(!variant.smooth)assets.put(new ResourceLocation("vandorlabs",path+".png.mcmeta"),new Asset(new ResourceLocation("vandorlabs",original+".mcmeta"),null,false));
            for(String kind:new String[]{"_n","_s"}){
                String shader=original.substring(0,original.length()-4)+kind+".png";
                assets.put(new ResourceLocation("vandorlabs",path+kind+".png"),new Asset(new ResourceLocation("vandorlabs",shader),null,variant.smooth));
                if(!variant.smooth)assets.put(new ResourceLocation("vandorlabs",path+kind+".png.mcmeta"),new Asset(new ResourceLocation("vandorlabs",shader+".mcmeta"),null,false));
            }
        }
        public boolean resourceExists(ResourceLocation id){Asset asset=assets.get(id);return asset!=null&&(asset.bytes!=null||source(asset.source)!=null);}
        public InputStream getInputStream(ResourceLocation id)throws IOException{
            Asset asset=assets.get(id);if(asset==null)throw new FileNotFoundException(id.toString());
            if(asset.bytes!=null)return new ByteArrayInputStream(asset.bytes);
            if(!asset.smooth)return openSource(asset.source);
            try(InputStream input=openSource(asset.source)){
                BufferedImage image=ImageIO.read(input);if(image==null)throw new IOException("Invalid shader PNG: "+asset.source);
                return new ByteArrayInputStream(encode(transform(image,false,true)));
            }
        }
        public Set<String> getResourceDomains(){return Collections.singleton("vandorlabs");}
        public <T extends IMetadataSection>T getPackMetadata(MetadataSerializer serializer,String section){return null;}
        public BufferedImage getPackImage(){return new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);}
        public String getPackName(){return "Vandor Labs authored native glass";}
    }
    private GlassPaneTextures(){}
}
