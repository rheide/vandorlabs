package com.vandorlabs.neoforge;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.vandorlabs.tiles.FilesystemTextureDiscovery;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Stable filesystem choices and resource suppliers; files are reread on each reload. */
public final class FilesystemMaterials {
    private static final org.slf4j.Logger LOGGER=LogUtils.getLogger();
    private static FilesystemTextureDiscovery.Catalog catalog;
    public static synchronized void initialize(){
        if(catalog!=null)return;
        catalog=FilesystemTextureDiscovery.discover(FMLPaths.CONFIGDIR.get().resolve("vandorlabs/textures"),LOGGER::warn);
        Map<Integer,ModernFinishCatalog.Entry> startup=new LinkedHashMap<>();
        for(var entry:catalog.entries)startup.put(entry.key,material(entry,entry.width,entry.height));
        ModernFinishCatalog.installFilesystem(startup);
    }
    public static FilesystemTextureDiscovery.Catalog catalog(){initialize();return catalog;}
    private static String texture(FilesystemTextureDiscovery.Entry entry){return "vandorlabs:filesystem/"+entry.id;}
    private static ModernFinishCatalog.Entry material(FilesystemTextureDiscovery.Entry entry,int width,int height){
        int canvas=1;while(canvas<Math.max(width,height))canvas<<=1;
        return new ModernFinishCatalog.Entry(entry.id,entry.label,entry.category,!entry.hidden,true,entry.key,texture(entry),(float)width/canvas,(float)height/canvas);
    }
    private record Shape(int width,int height){}
    private static final java.util.concurrent.atomic.AtomicLong epochs=new java.util.concurrent.atomic.AtomicLong();
    private static final class Loading {
        final long epoch=epochs.incrementAndGet();
        final Map<Integer,Shape> shapes=new java.util.concurrent.ConcurrentHashMap<>();
    }
    private static volatile Loading loading;
    private static synchronized void activate(Loading candidate){if(loading==null||candidate.epoch>loading.epoch)loading=candidate;}
    private static volatile Map<Integer,ModernFinishCatalog.Entry> baked=Map.of();
    /** Called after atlas decoding, before constructing new immutable model material snapshots. */
    static Map<Integer,ModernFinishCatalog.Entry> snapshotForBake(){
        Map<Integer,ModernFinishCatalog.Entry> next=new LinkedHashMap<>();
        Loading current=loading;
        for(var entry:catalog().entries){Shape shape=current==null?null:current.shapes.get(entry.key);next.put(entry.key,material(entry,shape==null?entry.width:shape.width,shape==null?entry.height:shape.height));}
        baked=Map.copyOf(next);return baked;
    }
    private static BufferedImage pad(BufferedImage source,Shape shape){
        int width=shape.width,height=shape.height,canvas=1;while(canvas<Math.max(width,height))canvas<<=1;
        int[] pixels=new int[canvas*canvas];for(int y=0;y<canvas;y++)for(int x=0;x<canvas;x++)pixels[y*canvas+x]=source.getRGB(Math.min(Math.min(x,width-1),source.getWidth()-1),Math.min(Math.min(y,height-1),source.getHeight()-1));
        BufferedImage padded=new BufferedImage(canvas,canvas,BufferedImage.TYPE_INT_ARGB);padded.setRGB(0,0,canvas,canvas,pixels,0,canvas);return padded;
    }
    private static byte[] png(Loading generation,FilesystemTextureDiscovery.Entry entry)throws IOException{
        BufferedImage source;
        try(InputStream stream=java.nio.file.Files.newInputStream(catalog.file(entry.source))){
            source=ImageIO.read(stream);
            if(source==null||source.getWidth()>FilesystemTextureDiscovery.MAX_DIMENSION||source.getHeight()>FilesystemTextureDiscovery.MAX_DIMENSION)throw new IOException("Expected a PNG no larger than 2048 pixels per dimension");
        }catch(IOException invalid){
            LOGGER.warn("Filesystem material unavailable: {}: {}",entry.source,invalid.getMessage());
            source=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)source.setRGB(x,y,0xff323944);
        }
        Shape observed=new Shape(source.getWidth(),source.getHeight());Shape first=generation.shapes.putIfAbsent(entry.key,observed);if(first==null)first=observed;
        activate(generation);
        var padded=pad(source,first);
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(padded,"PNG",output);return output.toByteArray();
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ClientPacks {
        @SubscribeEvent public static void completed(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event){
            if(!net.minecraft.client.Minecraft.getInstance().isSameThread())throw new IllegalStateException("Filesystem catalog publication outside client thread");
            ModernFinishCatalog.publishFilesystem(baked);
        }
        @SubscribeEvent public static void packs(AddPackFindersEvent event){
            if(event.getPackType()!=PackType.CLIENT_RESOURCES)return;
            initialize();
            var location=new PackLocationInfo("vandorlabs_filesystem",Component.literal("Vandor Labs filesystem materials"),PackSource.BUILT_IN,Optional.empty());
            var supplier=BuiltInPackSource.fromName(info->new FilePack(info));
            Pack pack=Pack.readMetaAndCreate(location,supplier,PackType.CLIENT_RESOURCES,new PackSelectionConfig(true,Pack.Position.TOP,true));
            if(pack==null)throw new IllegalStateException("Filesystem material pack metadata");
            event.addRepositorySource(consumer->consumer.accept(pack));
        }
    }
    private static final class FilePack extends AbstractPackResources {
        private final Map<ResourceLocation,IoSupplier<InputStream>> resources=new LinkedHashMap<>();
        private final Loading generation=new Loading();
        private static final byte[] METADATA="{\"pack\":{\"pack_format\":34,\"description\":\"Vandor Labs filesystem materials\"}}".getBytes(StandardCharsets.UTF_8);
        private FilePack(PackLocationInfo info){
            super(info);
            var sources=new java.util.ArrayList<Map<String,String>>();
            for(var entry:catalog.entries){
                resources.put(ResourceLocation.fromNamespaceAndPath("vandorlabs","textures/filesystem/"+entry.id+".png"),()->new ByteArrayInputStream(png(generation,entry)));
                sources.add(Map.of("type","minecraft:single","resource",texture(entry)));
            }
            byte[] atlas=new Gson().toJson(Map.of("sources",sources)).getBytes(StandardCharsets.UTF_8);
            resources.put(ResourceLocation.withDefaultNamespace("atlases/blocks.json"),()->new ByteArrayInputStream(atlas));
        }
        @Override public IoSupplier<InputStream> getRootResource(String... elements){return elements.length==1&&elements[0].equals("pack.mcmeta")?()->new ByteArrayInputStream(METADATA):null;}
        @Override public IoSupplier<InputStream> getResource(PackType type,ResourceLocation location){return type==PackType.CLIENT_RESOURCES?resources.get(location):null;}
        @Override public void listResources(PackType type,String namespace,String prefix,PackResources.ResourceOutput output){
            if(type==PackType.CLIENT_RESOURCES)resources.forEach((location,resource)->{if(location.getNamespace().equals(namespace)&&location.getPath().startsWith(prefix))output.accept(location,resource);});
        }
        @Override public Set<String> getNamespaces(PackType type){return type==PackType.CLIENT_RESOURCES?Set.of("minecraft","vandorlabs"):Set.of();}
        @Override public void close(){}
    }
    private FilesystemMaterials(){}
}
