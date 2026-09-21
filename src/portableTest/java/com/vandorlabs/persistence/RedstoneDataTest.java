package com.vandorlabs.persistence;

public final class RedstoneDataTest {
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MemoryPrimitiveData memberTag=new MemoryPrimitiveData();
        new RedstoneData.Member(-2,true).write(memberTag);
        RedstoneData.Member member=RedstoneData.Member.read(memberTag);
        check(member.channel==0&&member.signal,"member round trip and clamp");

        MemoryPrimitiveData sourceTag=new MemoryPrimitiveData();
        new RedstoneData.Source(7,true,true).write(sourceTag);
        RedstoneData.Source source=RedstoneData.Source.read(sourceTag);
        check(source.channel==7&&source.localOn&&source.initialized,"source round trip");

        MemoryPrimitiveData lightTag=new MemoryPrimitiveData();
        new RedstoneData.Light(9,true,false,true,true).write(lightTag);
        RedstoneData.Light light=RedstoneData.Light.read(lightTag);
        check(light.channel==9&&light.signal&&!light.manualOn
                &&light.particleStream&&light.initialized,"light round trip");
        check(lightTag.getInt(SaveSchema.DATA_VERSION)==SaveSchema.Redstone.VERSION,
                "redstone schema version written");
        System.out.println("Redstone save codecs PASS");
    }
}
