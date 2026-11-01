package com.vandorlabs.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

/** Exercise failed resource loads through the real texture manager, on the client thread. */
final class AnimationFallbackRuntimeChecks {
    static void run() {
        try {
            check();
        } catch (Exception failure) {
            throw new IllegalStateException("animation failure fallback regression", failure);
        }
    }

    private static void check() throws Exception {
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        Field managerField = null;
        for (Field field : TextureManager.class.getDeclaredFields()) {
            if (IResourceManager.class.isAssignableFrom(field.getType())) {
                require(managerField == null, "ambiguous texture resource manager");
                managerField = field;
            }
        }
        require(managerField != null, "texture resource manager field");
        managerField.setAccessible(true);
        IResourceManager original = (IResourceManager) managerField.get(textures);
        Method animation = TEAnimatedScreenSelector.class.getDeclaredMethod("animation", String.class, String.class);
        Method decode = TEAnimatedScreenSelector.class.getDeclaredMethod("decodeCompactAnimation", IResourceManager.class, String.class);
        animation.setAccessible(true);
        decode.setAccessible(true);
        try {
            for (boolean malformed : new boolean[]{false, true}) {
                Fixture fixture = new Fixture(original, malformed);
                managerField.set(textures, fixture.manager);
                // A renderer fallback must never turn strict bundled-asset decoding into a pass.
                try {
                    decode.invoke(null, fixture.manager, fixture.root);
                    throw new IllegalStateException("invalid animation passed direct decoder");
                } catch (InvocationTargetException expected) {
                    require(expected.getCause() instanceof IOException, "direct decoder reports resource I/O failure");
                }
                String key = "fallback_regression_" + malformed;
                Object fallback = animation.invoke(null, key, fixture.root);
                Field frames = fallback.getClass().getDeclaredField("frameCount");
                Field texture = fallback.getClass().getDeclaredField("texture");
                frames.setAccessible(true);
                texture.setAccessible(true);
                require(frames.getInt(fallback) == 1, "failed animation uses one static frame");
                require(new ResourceLocation("vandorlabs", fixture.root + "_static.png").equals(texture.get(fallback)),
                        "failed animation retains authored static path");
                int reads = fixture.reads;
                for (int i = 0; i < 4; i++) {
                    require(animation.invoke(null, key, fixture.root) == fallback, "one cached fallback per key");
                }
                require(fixture.reads == reads, "cached fallback does not retry failed disk reads");
                fixture.repaired = true;
                require(animation.invoke(null, key, fixture.root) == fallback && fixture.reads == reads,
                        "resource changes wait for reload invalidation");
                // This is the cache invalidation entry point used by the resource reload listener.
                TEAnimatedScreenSelector.clearCache();
                Object recovered = animation.invoke(null, key, fixture.root);
                require(recovered != fallback && frames.getInt(recovered) == 2 && fixture.reads > reads,
                        "reload retries repaired resources and restores animation");
                require(textures.getTexture((ResourceLocation) texture.get(recovered)) != null,
                        "repaired animation uploaded through texture manager");
            }
        } finally {
            TEAnimatedScreenSelector.clearCache();
            managerField.set(textures, original);
        }
        System.out.println("[vandorlabs][reprolab] animation-fallback PASS missing malformed strict-decoder static cached-no-retry reload-repair");
    }

    private static final class Fixture {
        final String root;
        final IResourceManager manager;
        boolean repaired;
        int reads;

        Fixture(IResourceManager original, boolean malformed) throws IOException {
            root = "textures/repro_animation_fallback_" + malformed;
            byte[] base = png(2, 2), strip = png(2, 4);
            manager = new IResourceManager() {
                @Override public java.util.Set<String> getResourceDomains() {
                    return original.getResourceDomains();
                }

                @Override public java.util.List<IResource> getAllResources(ResourceLocation location) throws IOException {
                    if (location.getResourcePath().startsWith(root)) {
                        return java.util.Collections.singletonList(getResource(location));
                    }
                    return original.getAllResources(location);
                }

                @Override public IResource getResource(ResourceLocation location) throws IOException {
                    if (location.getResourcePath().startsWith(root)) {
                        reads++;
                        byte[] bytes;
                        if (location.getResourcePath().endsWith("_static.png")) bytes = base;
                        else if (location.getResourcePath().endsWith("_animated.png") && repaired) bytes = strip;
                        else if (location.getResourcePath().endsWith("_animated.png") && malformed) bytes = new byte[]{1, 2, 3};
                        else throw new FileNotFoundException(location.toString());
                        return resource(location, bytes);
                    }
                    return original.getResource(location);
                }
            };
        }
    }

    private static IResource resource(ResourceLocation location, byte[] bytes) {
        return new IResource() {
            @Override public java.io.InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public ResourceLocation getResourceLocation() { return location; }
            @Override public String getResourcePackName() { return "animation fallback regression"; }
            @Override public boolean hasMetadata() { return false; }
            @Override public <T extends net.minecraft.client.resources.data.IMetadataSection> T getMetadata(String section) { return null; }
            @Override public void close() {}
        };
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, 0xff336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private AnimationFallbackRuntimeChecks() {}
}
