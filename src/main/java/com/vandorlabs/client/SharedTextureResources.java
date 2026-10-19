package com.vandorlabs.client;

import com.google.gson.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.*;
import net.minecraft.util.ResourceLocation;

/** Default-pack fallback preserves old PNG paths without storing identical pixels twice. */
public final class SharedTextureResources implements IResourcePack {
    private final Map<String,String> aliases=new HashMap<>();
    SharedTextureResources(){
        try(InputStream stream=getClass().getResourceAsStream("/assets/vandorlabs/data/texture_aliases.json")){
            if(stream!=null)for(Map.Entry<String,JsonElement> e:new JsonParser().parse(new InputStreamReader(stream,"UTF-8")).getAsJsonObject().entrySet())aliases.put(e.getKey(),e.getValue().getAsString());
        }catch(IOException error){throw new IllegalStateException("Cannot load shared texture paths",error);}
    }
    public static void install(){
        List<IResourcePack> defaults=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(Minecraft.class,Minecraft.getMinecraft(),"defaultResourcePacks","field_110449_ao");
        for(IResourcePack pack:defaults)if(pack instanceof SharedTextureResources)return;
        SharedTextureResources pack=new SharedTextureResources();defaults.add(pack);
        // Forge invokes preInit during the first resource reload, after the
        // active pack list has already been copied. Install into that manager
        // too, beneath every ordinary/user pack. Future reloads use defaults.
        net.minecraft.client.resources.SimpleReloadableResourceManager manager=(net.minecraft.client.resources.SimpleReloadableResourceManager)Minecraft.getMinecraft().getResourceManager();
        manager.reloadResourcePack(pack);
        Map<String,net.minecraft.client.resources.FallbackResourceManager> domains=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.resources.SimpleReloadableResourceManager.class,manager,"domainResourceManagers","field_110548_a");
        List<IResourcePack> active=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.resources.FallbackResourceManager.class,domains.get("vandorlabs"),"resourcePacks","field_110540_a");
        active.remove(pack);active.add(0,pack);
    }
    @Override public boolean resourceExists(ResourceLocation resource){return "vandorlabs".equals(resource.getResourceDomain())&&aliases.containsKey(resource.getResourcePath());}
    @Override public InputStream getInputStream(ResourceLocation resource)throws IOException{
        if(!resourceExists(resource))throw new FileNotFoundException(resource.toString());
        InputStream stream=getClass().getResourceAsStream("/assets/vandorlabs/"+aliases.get(resource.getResourcePath()));
        if(stream==null)throw new FileNotFoundException("Shared texture target missing: "+resource);return stream;
    }
    @Override public Set<String> getResourceDomains(){return Collections.singleton("vandorlabs");}
    @Override public <T extends IMetadataSection>T getPackMetadata(MetadataSerializer serializer,String section){return null;}
    @Override public BufferedImage getPackImage(){return new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);}
    @Override public String getPackName(){return "Vandor Labs shared textures";}
    static void check(Minecraft mc)throws IOException{
        SharedTextureResources pack=new SharedTextureResources();
        if(pack.aliases.isEmpty())return;
        // Exercise both the initial Forge reload and a subsequent normal reload.
        ResourceLocation first=new ResourceLocation("vandorlabs",pack.aliases.keySet().iterator().next());
        try(InputStream stream=mc.getResourceManager().getResource(first).getInputStream()){if(stream.read()<0)throw new IOException("Empty shared PNG");}
        mc.refreshResources();
        for(Map.Entry<String,String> e:pack.aliases.entrySet())try(InputStream actual=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs",e.getKey())).getInputStream();InputStream source=pack.getInputStream(new ResourceLocation("vandorlabs",e.getKey()))){
            BufferedImage a=javax.imageio.ImageIO.read(actual),b=javax.imageio.ImageIO.read(source);
            if(a==null||b==null||a.getWidth()!=b.getWidth()||a.getHeight()!=b.getHeight()||!Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth())))throw new IllegalStateException("Shared PNG pixels changed: "+e.getKey());
        }
        final ResourceLocation replaced=new ResourceLocation("vandorlabs",pack.aliases.keySet().iterator().next());
        final byte[] marker=new byte[]{3,1,4,1,5,9};
        IResourcePack override=new IResourcePack(){
            public boolean resourceExists(ResourceLocation id){return replaced.equals(id);}
            public InputStream getInputStream(ResourceLocation id)throws IOException{if(!resourceExists(id))throw new FileNotFoundException();return new ByteArrayInputStream(marker);}
            public Set<String> getResourceDomains(){return pack.getResourceDomains();}
            public <T extends IMetadataSection>T getPackMetadata(MetadataSerializer serializer,String section){return null;}
            public BufferedImage getPackImage(){return pack.getPackImage();}
            public String getPackName(){return "Shared texture override check";}
        };
        net.minecraft.client.resources.SimpleReloadableResourceManager manager=new net.minecraft.client.resources.SimpleReloadableResourceManager(new MetadataSerializer());
        manager.reloadResources(Arrays.asList(pack,override));
        try(InputStream actual=manager.getResource(replaced).getInputStream()){
            for(byte value:marker)if(actual.read()!=(value&255))throw new IllegalStateException("Shared pack overrides a user texture pack");
            if(actual.read()!=-1)throw new IllegalStateException("Override stream length");
        }
        System.out.println("[vandorlabs][reprolab] shared-textures PASS "+pack.aliases.size()+" legacy PNG paths preserve pixels and user resource-pack priority");
    }
}
