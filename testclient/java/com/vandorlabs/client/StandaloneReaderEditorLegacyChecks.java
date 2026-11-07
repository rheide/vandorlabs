package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.network.MessageTokenSlot;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.*;
import java.nio.file.*;

/** Released MessageTokenSlot wire corpus; validity is the unchanged Handler scalar preflight. */
public final class StandaloneReaderEditorLegacyChecks {
    private static String hex(ByteBuf bytes){StringBuilder result=new StringBuilder();for(int i=bytes.readerIndex();i<bytes.writerIndex();i++)result.append(String.format("%02x",bytes.getUnsignedByte(i)));return result.toString();}
    public static void main(String[] args)throws Exception {
        JsonArray rows=new JsonArray();
        for(int mode:new int[]{0,1,2,3,4,255})for(int mask:new int[]{0,1,2,3,4,8,16,32,64,127,128})for(boolean any:new boolean[]{false,true})for(String channels:new String[]{"","27, 31","bad","1,"})for(int mount:new int[]{-1,0,27,63}){
            MessageTokenSlot original=new MessageTokenSlot(1947,mode,any,mask,channels).withMount(mount>=0,mount%4,mount/4%4,mount/16);
            ByteBuf wire=Unpooled.buffer();original.toBytes(wire);MessageTokenSlot decoded=new MessageTokenSlot();decoded.fromBytes(wire.duplicate());ByteBuf round=Unpooled.buffer();decoded.toBytes(round);
            if(!hex(wire).equals(hex(round)))throw new AssertionError("Released reader codec changed bytes");
            boolean valid=mode<=3&&(mask==0||mask==127||mask>=2&&mask<=64&&Integer.bitCount(mask)==1)&&channels.length()<=256&&ChannelList.parse(channels)!=null;
            JsonObject row=new JsonObject();row.addProperty("mode",mode);row.addProperty("any",any);row.addProperty("mask",mask);row.addProperty("channels",channels);row.addProperty("mount",mount);row.addProperty("wire",hex(wire));row.addProperty("valid",valid);rows.add(row);wire.release();round.release();
        }
        String serialized=rows.toString();java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(serialized.getBytes("UTF-8")))hash.append(String.format("%02x",b&255));if(!hash.toString().equals("0c02d57ddba0456757e1c22aff3816f4367b2511abb0ad8a86172fb5690cf19f"))throw new AssertionError("Released reader editor wire changed "+hash);JsonObject root=new JsonObject();root.addProperty("golden",hash.toString());root.add("cases",rows);Path output=Paths.get(System.getProperty("vandorlabs.standaloneReaderEditorReference","build/test-artifacts/standalone-reader-editor-forge.json"));Files.createDirectories(output.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(output)){new Gson().toJson(root,writer);}System.out.println("Actual standalone reader editor wire PASS cases="+rows.size()+" golden="+hash);
    }
}
