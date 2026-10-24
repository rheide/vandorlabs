package com.vandorlabs.neoforge;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.vandorlabs.render.TextureImagePadding;
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
        FilesystemRuntimeChecks.prepare();
        catalog=FilesystemTextureDiscovery.discover(FMLPaths.CONFIGDIR.get().resolve("vandorlabs/textures"),LOGGER::warn);
        for(var entry:catalog.entries)register(entry,entry.width,entry.height);
    }
    public static FilesystemTextureDiscovery.Catalog catalog(){initialize();return catalog;}
    private static String texture(FilesystemTextureDiscovery.Entry entry){return "vandorlabs:filesystem/"+entry.id;}
    private static void register(FilesystemTextureDiscovery.Entry entry,int width,int height){
        int canvas=1;while(canvas<Math.max(width,height))canvas<<=1;
        ModernFinishCatalog.registerExternal(entry.key,new ModernFinishCatalog.Entry(entry.id,entry.label,entry.category,!entry.hidden,true,entry.key,texture(entry),(float)width/canvas,(float)height/canvas));
    }
    private static byte[] png(FilesystemTextureDiscovery.Entry entry)throws IOException{
        BufferedImage source;
        try(InputStream stream=java.nio.file.Files.newInputStream(catalog.file(entry.source))){
            source=ImageIO.read(stream);
            if(source==null||source.getWidth()>FilesystemTextureDiscovery.MAX_DIMENSION||source.getHeight()>FilesystemTextureDiscovery.MAX_DIMENSION)throw new IOException("Expected a PNG no larger than 2048 pixels per dimension");
        }catch(IOException invalid){
            LOGGER.warn("Filesystem material unavailable: {}: {}",entry.source,invalid.getMessage());
            source=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)source.setRGB(x,y,0xff323944);
        }
        var padded=TextureImagePadding.pad(source,false,false);register(entry,source.getWidth(),source.getHeight());
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(padded.image,"PNG",output);return output.toByteArray();
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class ClientPacks {
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
        private static final byte[] METADATA="{\"pack\":{\"pack_format\":34,\"description\":\"Vandor Labs filesystem materials\"}}".getBytes(StandardCharsets.UTF_8);
        private FilePack(PackLocationInfo info){
            super(info);
            var sources=new java.util.ArrayList<Map<String,String>>();
            for(var entry:catalog.entries){
                resources.put(ResourceLocation.fromNamespaceAndPath("vandorlabs","textures/filesystem/"+entry.id+".png"),()->new ByteArrayInputStream(png(entry)));
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
