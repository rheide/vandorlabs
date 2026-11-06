package com.vandorlabs.neoforge.segmented;

import com.google.gson.*;
import io.netty.buffer.*;
import java.nio.file.*;

/** Actual released messages plus malformed boundaries; no gameplay bypass establishes authorization. */
public final class SegmentedEditChecks {
    public static void main(String[] args)throws Exception {
        JsonArray fixtures=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();int count=0;
        for(JsonElement value:fixtures){var row=value.getAsJsonObject();ByteBuf input=Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(row.get("input").getAsString()));ByteBuf output=Unpooled.buffer();try{
            var edit=NativeSegmentedEdit.decode(input,id->id==-1);if(input.isReadable())throw new AssertionError("Trailing fields");edit.encode(output);
            if(!ByteBufUtil.hexDump(output).equals(row.get("canonical").getAsString()))throw new AssertionError("Released wire drift case "+count);count++;
        }finally{input.release();output.release();}}
        for(int length:new int[]{0,1,7,NativeSegmentedEdit.MAX_BYTES+1,4096}){ByteBuf bad=Unpooled.buffer(length).writeZero(length);try{reject(bad);}finally{bad.release();}}
        ByteBuf valid=Unpooled.buffer();try{new NativeSegmentedEdit(4,0,10,true,true,com.vandorlabs.redstone.ChannelList.of(7),-1,null).encode(valid);
            for(int offset:new int[]{4,5,6,7}){ByteBuf bad=valid.copy();try{bad.setByte(offset,255);reject(bad);}finally{bad.release();}}
            ByteBuf negative=valid.copy();try{negative.setInt(8,-1);reject(negative);}finally{negative.release();}
            ByteBuf trailing=valid.copy().writeByte(0);try{reject(trailing);}finally{trailing.release();}
        }finally{valid.release();}
        System.out.println("Native Segmented editor wire PASS actual-source profiles="+count+" bounded lengths flags channels and legacy extension");
    }
    private static void reject(ByteBuf buffer){try{NativeSegmentedEdit.decode(buffer,id->id==-1);}catch(IllegalArgumentException expected){return;}throw new AssertionError("Malformed edit accepted");}
}
