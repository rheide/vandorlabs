package com.vandorlabs.persistence;

import java.util.HashMap;
import java.util.Map;

public final class BlockStateReferenceTest {
    private interface CheckedRunnable { void run(); }
    private static void rejects(CheckedRunnable action,String message) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Map<String,String> properties=new HashMap<>();
        properties.put("variant","smooth_stone");
        properties.put("half","top");
        BlockStateReference reference=new BlockStateReference("minecraft:stone_slab",properties);
        String encoded=reference.encode();
        if (!"minecraft:stone_slab[half=top,variant=smooth_stone]".equals(encoded))
            throw new AssertionError("block properties are not canonical");
        BlockStateReference decoded=BlockStateReference.parse(encoded);
        if (!decoded.id.equals(reference.id)||!decoded.properties.equals(reference.properties))
            throw new AssertionError("block-state reference did not round trip");
        rejects(()->BlockStateReference.parse("stone"),"unqualified block id accepted");
        rejects(()->BlockStateReference.parse("minecraft:stone[a=b,a=c]"),
                "duplicate property accepted");
        rejects(()->BlockStateReference.parse("minecraft:stone[a=bad value]"),
                "unsafe property value accepted");
        System.out.println("Block-state reference PASS");
    }
}
