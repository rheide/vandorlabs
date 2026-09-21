package com.vandorlabs.persistence;

import com.google.common.base.Optional;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

/** Forge 1.12.2 bridge for canonical portable block-state references. */
public final class LegacyBlockStates {
    private LegacyBlockStates() { }

    public static String encode(IBlockState state) {
        ResourceLocation id=Block.REGISTRY.getNameForObject(state.getBlock());
        if (id==null) throw new IllegalArgumentException("unregistered block");
        Map<String,String> properties=new TreeMap<>();
        for (IProperty<?> property:state.getPropertyKeys())
            properties.put(property.getName(),valueName(state,property));
        return new BlockStateReference(id.toString(),properties).encode();
    }

    public static IBlockState decode(String encoded) {
        try {
            BlockStateReference reference=BlockStateReference.parse(encoded);
            Block block=Block.REGISTRY.getObject(new ResourceLocation(reference.id));
            if (block==null) return null;
            IBlockState state=block.getDefaultState();
            for (Map.Entry<String,String> entry:reference.properties.entrySet()) {
                IProperty<?> property=findProperty(state,entry.getKey());
                if (property==null) return null;
                state=withValue(state,property,entry.getValue());
                if (state==null) return null;
            }
            return state;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static IProperty<?> findProperty(IBlockState state,String name) {
        for (IProperty<?> property:state.getPropertyKeys())
            if (property.getName().equals(name)) return property;
        return null;
    }

    private static <T extends Comparable<T>> String valueName(IBlockState state,IProperty<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> IBlockState withValue(
            IBlockState state,IProperty<T> property,String value) {
        Optional<T> parsed=property.parseValue(value);
        return parsed.isPresent()?state.withProperty(property,parsed.get()):null;
    }
}
