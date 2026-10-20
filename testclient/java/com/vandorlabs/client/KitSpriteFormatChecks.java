package com.vandorlabs.client;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Check packaged kit sprites with Minecraft's own static-sprite header loader. */
public final class KitSpriteFormatChecks {
    private static boolean retiredMachinery(String name){
        // Company armor/artwork is independent of the retired machinery kit.
        if(name.matches("assets/vandorlabs/(?:models/item/armor/|textures/items/armor/|textures/models/armor/companies/|textures/blocks/corp/)(?:[^/]+/)*vektor_[^/]+"))return false;
        return name.matches(".*(?:^|/)(?:aster_|rivet_|vektor_|kestrel_|hadron_).*");
    }
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String name) { super(name); }
    }

    public static void main(String[] args) throws Exception {
        // Confirm this check reproduces the rectangular-sprite regression.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        boolean rejected = false;
        try {
            new Sprite("invalid").loadSprite(new PngSizeInfo(new ByteArrayInputStream(bytes.toByteArray())), false);
        } catch (RuntimeException expected) {
            rejected = expected.getMessage().contains("broken aspect ratio");
        }
        if (!rejected) throw new AssertionError("Rectangular static sprite was not rejected");
        if(retiredMachinery("assets/vandorlabs/models/item/armor/vektor_boots.json")||!retiredMachinery("assets/vandorlabs/models/block/vektor_console.json"))throw new AssertionError("Machinery validation must distinguish company armor");

        Set<String> textures = new LinkedHashSet<>();
        try (ZipFile jar = new ZipFile(args[0])) {
            String[] catalogs = {"canopy_meshes.json"};
            for(java.util.Enumeration<? extends ZipEntry> entries=jar.entries();entries.hasMoreElements();) {
                String name=entries.nextElement().getName();
                if(retiredMachinery(name))throw new AssertionError("Removed machinery asset packaged: "+name);
            }
            for (String catalog : catalogs) {
                String directory = "canopy/";
                try (Reader reader = new InputStreamReader(jar.getInputStream(jar.getEntry("assets/vandorlabs/data/" + catalog)), "UTF-8")) {
                    JsonArray models=new JsonParser().parse(reader).getAsJsonArray();
                    for (JsonElement model : models)
                        for (JsonElement face : model.getAsJsonObject().getAsJsonArray("faces"))
                            textures.add("assets/vandorlabs/textures/blocks/" + directory
                                    + face.getAsJsonObject().get("material").getAsString() + ".png");
                }
            }
            for (String name : textures) {
                ZipEntry entry = jar.getEntry(name);
                if (entry == null) throw new AssertionError("Missing sprite: " + name);
                Sprite sprite = new Sprite(name);
                try (InputStream input = jar.getInputStream(entry)) {
                    sprite.loadSprite(new PngSizeInfo(input), false);
                }
                if (sprite.getIconWidth() < 16 || (sprite.getIconWidth() & (sprite.getIconWidth()-1)) != 0)
                    throw new AssertionError("Invalid mipmap dimensions: " + name);
                try (InputStream input = jar.getInputStream(entry)) {
                    if (ImageIO.read(input) == null) throw new AssertionError("Invalid PNG: " + name);
                }
            }
        }
        System.out.println("PASS: Minecraft accepted all " + textures.size() + " packaged kit sprites; rectangular regression reproduced.");
    }
}
