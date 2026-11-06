package com.vandorlabs.neoforge.door;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.Direction;

/** Replays actual current Forge outputs without duplicating its expected-value policy. */
public final class DoorOpeningFluidChecks {
    public static void main(String[] args)throws Exception{
        int cases=0,checks=0;try(var lines=Files.lines(Path.of(System.getProperty("vandorlabs.doorOpeningReference")))){for(String line:(Iterable<String>)lines::iterator){var row=JsonParser.parseString(line).getAsJsonObject();int first=row.get("first").getAsInt(),second=row.get("second").getAsInt();int a=first==3?0:first,b=second==3?0:second;var face=Direction.from3DDataValue(row.get("face").getAsInt());int ambient=0,mask=row.get("mask").getAsInt();if((mask&(1<<face.getOpposite().get3DDataValue()))!=0)ambient|=1;if((mask&(1<<face.get3DDataValue()))!=0)ambient|=2;int water=NativeDoorOpeningFluids.sources(row.get("open").getAsBoolean(),a,b,ambient);for(int side=0;side<2;side++){int own=side==0?a:b,actual=own!=0?own:(water>>(side*2)&3)!=0?1:0,expected=row.get("side"+side).getAsInt();if(actual!=expected)throw new AssertionError("Actual source mismatch "+row+" side="+side+" native="+actual);checks++;int source=water>>(side*2)&3,owner=side==0?first:second,other=side==0?second:first,selected=own!=0?owner:source==1?other:0,amount=selected==1?777:selected==2?333:source==2?1000:0,tag=selected;if(amount!=row.get("amount"+side).getAsInt()||tag!=row.get("tag"+side).getAsInt())throw new AssertionError("Source stack identity/amount "+row+" side="+side);checks+=2;}cases++;}}
        if(cases!=32768)throw new AssertionError("Actual source coverage "+cases);System.out.println("Native Door effective opening water PASS cases="+cases+" checks="+checks);
    }
}
