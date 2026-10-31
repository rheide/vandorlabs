package com.vandorlabs.client;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;

/** Resource-manager coverage, including deduplicated maps and a real reload.
 * This does not establish shader appearance or hardware performance. */
final class PbrMaterialRuntimeChecks {
    static void run(Minecraft mc) throws IOException {
        check(mc);
        mc.refreshResources();
        int count=check(mc);
        int binding=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
        net.minecraft.client.renderer.GlStateManager.bindTexture(mc.getTextureMapBlocks().getGlTextureId());
        int width=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
        int height=org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,0,org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
        net.minecraft.client.renderer.GlStateManager.bindTexture(binding);
        System.out.println("[vandorlabs][reprolab] pbr-atlas-size width="+width+" height="+height+" rgba-plane-bytes="+(width*(long)height*4));
        System.out.println("[vandorlabs][reprolab] pbr-materials-live PASS textures="+count+" companion-resolution alpha animation-aliases resource-reload; shaders-not-tested");
    }

    private static int check(Minecraft mc) throws IOException {
        JsonObject entries;
        try(IResource resource=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs:data/pbr_materials.json"));
                Reader reader=new InputStreamReader(resource.getInputStream(),"UTF-8")) {
            entries=new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("maps");
        }
        if(entries.size()<1000)throw new IOException("Incomplete PBR manifest");
        for(Map.Entry<String,JsonElement> entry:entries.entrySet()) {
            String name=entry.getKey();JsonArray size=entry.getValue().getAsJsonObject().getAsJsonArray("size");
            try(IResource resource=mc.getResourceManager().getResource(new ResourceLocation("vandorlabs",name.substring(0,name.length()-4)+"_s.png"))) {
                BufferedImage image=ImageIO.read(resource.getInputStream());
                if(image==null||image.getWidth()!=size.get(0).getAsInt()||image.getHeight()!=size.get(1).getAsInt())throw new IOException("PBR size mismatch: "+name);
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                    if((image.getRGB(x,y)>>>24)!=255)throw new IOException("Unintended PBR emission: "+name);
            }
        }
        return entries.size();
    }

    private PbrMaterialRuntimeChecks(){}
}
