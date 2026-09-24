package com.vandorlabs.persistence;

import com.vandorlabs.animation.ScreenBehavior;

public final class ScreenDataTest {
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MemoryPrimitiveData legacy=new MemoryPrimitiveData();
        ScreenData defaults=ScreenData.read(legacy,"engineering_screen","keyboard",
                id->"keyboard".equals(id)||"pilot".equals(id));
        check("engineering_screen".equals(defaults.selectedScreen),"legacy screen default");
        check(defaults.framed&&defaults.wallPosition==-1,"legacy layout defaults");
        check("keyboard".equals(defaults.inputPanel)
                &&"keyboard".equals(defaults.secondaryInputPanel),"legacy input defaults");

        ScreenData source=new ScreenData("science",true,99,false,99,"pilot","keyboard",
                9,true,-4,true,5);
        MemoryPrimitiveData encoded=new MemoryPrimitiveData();
        source.write(encoded);
        ScreenData decoded=ScreenData.read(encoded,"engineering_screen","keyboard",
                id->"keyboard".equals(id)||"pilot".equals(id));
        check(decoded.displayMode==ScreenBehavior.ANIMATED
                &&decoded.animationSpeedIndex==2,"untrusted modes clamp");
        check(decoded.wallPosition==2&&decoded.redstoneChannel==0,"numeric fields clamp");
        check("science".equals(decoded.selectedScreen)&&"pilot".equals(decoded.inputPanel)
                &&decoded.channelSignal&&decoded.housingTexture==5,"screen save round trip");
        check(encoded.getInt(SaveSchema.DATA_VERSION)==SaveSchema.Screen.VERSION,
                "screen schema version written");
        System.out.println("Screen save codec PASS");
    }
}
