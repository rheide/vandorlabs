package com.vandorlabs.client;

import com.vandorlabs.render.GlassPanePixels;
import com.vandorlabs.tiles.GlassTextures;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Resource-pack-aware sprites for the stationary pane renderer. Smooth mode is
 * an explicit material approximation for the glass palette: it removes
 * the repeated shimmer so a joined interior can really be one render rectangle.
 * the per-block pattern and animation, including resource-pack replacements.
 * Authored mode retains UV cells and source animation as a compatibility choice.
 */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class GlassPaneTextures {
    private static final String PREFIX="blocks/programmable_glass/static_panes/";
    private static final Map<String,Variant> VARIANTS=new HashMap<>();
    private static volatile Map<String,Selection> selections=Collections.emptyMap();
    private static Scope loading;

    public static final class Selection {
        public final TextureAtlasSprite sprite;
        public final boolean singleSurface,smooth,mergeable;
        public final String source;
        private Selection(TextureAtlasSprite sprite,Variant variant){
            this.sprite=sprite;singleSurface=variant.single;smooth=variant.smooth;
            mergeable=variant.mergeable;source=variant.source.toString();
        }
    }
    private static final class ImageInfo {
        final int width,height,dominant;final boolean uniform;
        ImageInfo(BufferedImage image){
            width=image.getWidth();height=image.getHeight();int[] pixels=image.getRGB(0,0,width,height,null,0,width);
            uniform=GlassPanePixels.uniform(pixels);
            // Atlas animations are vertical square frames. Smooth mode freezes
            // the first frame's base tint instead of carrying animation state.
            dominant=GlassPanePixels.dominant(height>width?image.getRGB(0,0,width,width,null,0,width):pixels);
        }
    }
    private static final class Variant {
        final ResourceLocation source;final String sprite;
        final boolean single,smooth,mergeable;final ImageInfo diffuse,normal,specular;
        Variant(ResourceLocation source,String sprite,boolean single,boolean smooth,boolean mergeable,ImageInfo diffuse,ImageInfo normal,ImageInfo specular){
            this.source=source;this.sprite=sprite;this.single=single;this.smooth=smooth;this.mergeable=mergeable;
            this.diffuse=diffuse;this.normal=normal;this.specular=specular;
        }
        ImageInfo channel(String suffix){return "_n".equals(suffix)?normal:"_s".equals(suffix)?specular:diffuse;}
    }

    public static Selection select(int shade,int tint){return selections.get(GlassTextures.texture(shade,1,tint));}
    public static TextureAtlasSprite sprite(int shade,int tint,boolean single){
        Selection selection=select(shade,tint);
        if(selection!=null&&selection.singleSurface==single)return selection.sprite;
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassTextures.texture(shade,1,tint));
    }
    public static boolean smooth(int shade,int tint){Selection selected=select(shade,tint);return selected!=null&&selected.smooth;}
    public static String diagnostics(){
        int smooth=0,authored=0;for(Selection selected:selections.values())if(selected.smooth)smooth++;else authored++;
        return "pane-textures="+selections.size()+" smooth="+smooth+" authored="+authored;
    }

    @SubscribeEvent(priority=EventPriority.HIGH) public static void before(TextureStitchEvent.Pre event){
        if(event.getMap()!=Minecraft.getMinecraft().getTextureMapBlocks())return;
        // Same vertex formats can still acquire entirely new atlas coordinates.
        // Restore tile admissions before the old sprite publication is retired.
        GlassPaneChunks.reload();
        close();selections=Collections.emptyMap();VARIANTS.clear();
        if(!GlassPanePolicy.staticEnabled())return;
        loading=new Scope(ShaderAtlasResources.packs());
        Set<String> seen=new HashSet<>();
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<GlassTextures.OPAQUE;tint++){
            String name=GlassTextures.texture(shade,1,tint);if(!seen.add(name))continue;
            try{
                ResourceLocation source=new ResourceLocation(GlassTextures.fullTexture(shade,1,tint));
                ImageInfo diffuse=loading.pack.inspect(source),normal=loading.pack.inspect(companion(source,"_n")),specular=loading.pack.inspect(companion(source,"_s"));
                if(diffuse==null)continue;
                boolean animated=loading.pack.animated(source)||loading.pack.animated(companion(source,"_n"))
                        ||loading.pack.animated(companion(source,"_s"));
                boolean smooth=GlassPanePolicy.smoothTextures();
                boolean mergeable=smooth||!animated&&diffuse.uniform&&(normal==null||normal.uniform)&&(specular==null||specular.uniform);
                boolean single=GlassPanePolicy.singleSurface();
                String id=smooth||single?"vandorlabs:"+PREFIX+(smooth?"smooth/":"authored/")+(single?"single/":"double/")
                        +source.getResourcePath().substring("textures/blocks/programmable_glass/low/".length(),source.getResourcePath().length()-4):name;
                Variant variant=new Variant(source,id,single,smooth,mergeable,diffuse,normal,specular);
                VARIANTS.put(name,variant);if(smooth||single)loading.pack.add(variant);
                event.getMap().registerSprite(new ResourceLocation(id));
            }catch(IOException|RuntimeException invalid){
                // Keep the ordinary tile renderer for an unavailable material;
                // never publish a retained mesh that points at Missingno.
                if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn("Stationary glass texture unavailable: "+name,invalid);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(TextureStitchEvent.Post event){
        if(event.getMap()!=Minecraft.getMinecraft().getTextureMapBlocks())return;
        Map<String,Selection> ready=new HashMap<>();
        for(Map.Entry<String,Variant> entry:VARIANTS.entrySet()){
            Variant variant=entry.getValue();TextureAtlasSprite sprite=event.getMap().getAtlasSprite(variant.sprite);
            if(sprite!=event.getMap().getMissingSprite()&&variant.sprite.equals(sprite.getIconName()))ready.put(entry.getKey(),new Selection(sprite,variant));
        }
        selections=Collections.unmodifiableMap(ready);close();
    }
    @SubscribeEvent public static void cleanup(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END&&loading!=null)close();}
    private static void close(){if(loading!=null){loading.close();loading=null;}}
    static ResourceLocation companion(ResourceLocation source,String suffix){String path=source.getResourcePath();return new ResourceLocation(source.getResourceDomain(),path.substring(0,path.length()-4)+suffix+".png");}

    private static final class Scope implements AutoCloseable {
        final List<IResourcePack> active;final DerivedPack pack;
        Scope(List<IResourcePack> active){this.active=active;pack=new DerivedPack(new ArrayList<>(active));active.add(pack);}
        @Override public void close(){active.remove(pack);}
    }
    /** A bounded stitch scope also exposes normal/specular companions under the derived name. */
    static final class DerivedPack implements IResourcePack {
        private final List<IResourcePack> sources;
        private final Map<String,Variant> names=new HashMap<>();
        private final Map<String,byte[]> generated=new HashMap<>();
        DerivedPack(List<IResourcePack> sources){this.sources=sources;}
        void add(Variant variant){names.put("textures/"+new ResourceLocation(variant.sprite).getResourcePath()+".png",variant);}
        private IResourcePack source(ResourceLocation id){for(int i=sources.size()-1;i>=0;i--)if(sources.get(i).resourceExists(id))return sources.get(i);return null;}
        ImageInfo inspect(ResourceLocation id)throws IOException{
            IResourcePack pack=source(id);if(pack==null)return null;
            BufferedImage image;try(InputStream input=pack.getInputStream(id)){image=ImageIO.read(input);}
            if(image==null)throw new IOException("Invalid glass PNG: "+id);
            return new ImageInfo(image);
        }
        boolean animated(ResourceLocation id)throws IOException{
            ResourceLocation meta=new ResourceLocation(id.getResourceDomain(),id.getResourcePath()+".mcmeta");IResourcePack pack=source(meta);if(pack==null)return false;
            try(Reader reader=new InputStreamReader(pack.getInputStream(meta),"UTF-8")){
                com.google.gson.JsonElement json=new com.google.gson.JsonParser().parse(reader);return json.isJsonObject()&&json.getAsJsonObject().has("animation");
            }
        }
        private Request request(ResourceLocation id){
            if(!"vandorlabs".equals(id.getResourceDomain()))return null;
            String path=id.getResourcePath();boolean meta=path.endsWith(".mcmeta");if(meta)path=path.substring(0,path.length()-7);
            String suffix="";if(path.endsWith("_n.png")||path.endsWith("_s.png")){suffix=path.substring(path.length()-6,path.length()-4);path=path.substring(0,path.length()-6)+".png";}
            Variant variant=names.get(path);if(variant==null)return null;
            ResourceLocation source=suffix.isEmpty()?variant.source:companion(variant.source,suffix);
            if(meta)source=new ResourceLocation(source.getResourceDomain(),source.getResourcePath()+".mcmeta");
            return new Request(variant,source,suffix,meta);
        }
        @Override public boolean resourceExists(ResourceLocation id){Request request=request(id);return request!=null&&!(request.meta&&request.variant.smooth)&&source(request.source)!=null;}
        @Override public InputStream getInputStream(ResourceLocation id)throws IOException{
            Request request=request(id);IResourcePack source=request==null?null:source(request.source);if(source==null||request.meta&&request.variant.smooth)throw new FileNotFoundException(id.toString());
            if(request.meta||!request.variant.smooth&&(!request.suffix.isEmpty()||!request.variant.single))return source.getInputStream(request.source);
            byte[] cached=generated.get(id.getResourcePath());if(cached!=null)return new ByteArrayInputStream(cached);
            BufferedImage image;
            if(request.variant.smooth){
                ImageInfo info=request.variant.channel(request.suffix);if(info==null)throw new FileNotFoundException(id.toString());
                int pixel=info.dominant;if(request.suffix.isEmpty()&&request.variant.single)pixel=GlassPanePixels.singlePixel(pixel);
                image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);int[] pixels=new int[16*16];Arrays.fill(pixels,pixel);image.setRGB(0,0,16,16,pixels,0,16);
            }else{
                try(InputStream input=source.getInputStream(request.source)){image=ImageIO.read(input);}
                if(image==null)throw new IOException("Invalid glass PNG: "+request.source);
                int width=image.getWidth(),height=image.getHeight();int[] pixels=image.getRGB(0,0,width,height,null,0,width);
                for(int i=0;i<pixels.length;i++)pixels[i]=GlassPanePixels.singlePixel(pixels[i]);
                BufferedImage corrected=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);corrected.setRGB(0,0,width,height,pixels,0,width);image=corrected;
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!ImageIO.write(image,"PNG",bytes))throw new IOException("No PNG writer");
            cached=bytes.toByteArray();generated.put(id.getResourcePath(),cached);return new ByteArrayInputStream(cached);
        }
        @Override public Set<String> getResourceDomains(){return Collections.singleton("vandorlabs");}
        @Override public <T extends IMetadataSection>T getPackMetadata(MetadataSerializer serializer,String section){return null;}
        @Override public BufferedImage getPackImage(){return new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);}
        @Override public String getPackName(){return "Vandor Labs stationary glass materials";}
    }
    private static final class Request {
        final Variant variant;final ResourceLocation source;final String suffix;final boolean meta;
        Request(Variant variant,ResourceLocation source,String suffix,boolean meta){this.variant=variant;this.source=source;this.suffix=suffix;this.meta=meta;}
    }
    private GlassPaneTextures(){}
}
