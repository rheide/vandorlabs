package com.vandorlabs.client;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Optional component-renderer artwork; no dependency on Malisis classes or world access. */
final class ComponentBlockTextures {
    private static final Map<Class<?>, Optional<Method>> COMPONENTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> ICONS = new java.util.concurrent.ConcurrentHashMap<>();

    static TextureAtlasSprite sprite(IBlockState state, boolean upper) {
        Block block = state.getBlock();
        Optional<Method> accessor = COMPONENTS.computeIfAbsent(block.getClass(),
                type -> method(type, "getComponents"));
        if (!accessor.isPresent()) return null;
        try {
            Object components = accessor.get().invoke(block);
            if (components instanceof Iterable<?>)
                for (Object component : (Iterable<?>) components) {
                    TextureAtlasSprite sprite = icon(component, state, upper);
                    if (CustomBlockTextures.usable(sprite)) return sprite;
                }
        } catch (ReflectiveOperationException | RuntimeException incompatibleProvider) {
            // Keep sampling the ordinary model when an optional provider is unavailable.
        }
        return null;
    }

    static TextureAtlasSprite icon(Object provider, IBlockState state, boolean upper) {
        if (provider == null) return null;
        Optional<Method> accessor = ICONS.computeIfAbsent(provider.getClass(), type -> {
            // Big doors expose face artwork separately from their inventory icon.
            Optional<Method> result = iconMethod(type, "getDoorIcon");
            if (!result.isPresent()) result = iconMethod(type, "getIcon", boolean.class, boolean.class, EnumFacing.class);
            if (!result.isPresent()) result = iconMethod(type, "getIcon", IBlockState.class, EnumFacing.class);
            if (!result.isPresent()) result = iconMethod(type, "getIcon");
            return result;
        });
        if (!accessor.isPresent()) return null;
        Method method = accessor.get();
        try {
            Object[] arguments = method.getParameterCount() == 3
                    ? new Object[]{upper, false, EnumFacing.NORTH}
                    : method.getParameterCount() == 2 ? new Object[]{state, EnumFacing.NORTH} : new Object[0];
            return (TextureAtlasSprite) method.invoke(provider, arguments);
        } catch (ReflectiveOperationException | RuntimeException incompatibleProvider) {
            return null;
        }
    }

    private static Optional<Method> iconMethod(Class<?> type, String name, Class<?>... parameters) {
        Optional<Method> method = method(type, name, parameters);
        return method.filter(candidate -> TextureAtlasSprite.class.isAssignableFrom(candidate.getReturnType()));
    }

    private static Optional<Method> method(Class<?> type, String name, Class<?>... parameters) {
        try { return Optional.of(type.getMethod(name, parameters)); }
        catch (NoSuchMethodException missing) { return Optional.empty(); }
    }
}
