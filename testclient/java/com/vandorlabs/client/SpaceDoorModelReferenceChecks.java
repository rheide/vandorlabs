package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntitySpaceDoor;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Freeze actual public legacy model names, detail aliases and numeric metadata. */
public final class SpaceDoorModelReferenceChecks {
    public static void main(String[] args)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int cases=0;
        for(String design:TileEntitySpaceDoor.DESIGNS)digest.update(design.getBytes(StandardCharsets.UTF_8));for(String detail:TileEntitySpaceDoor.DETAILS)digest.update(detail.getBytes(StandardCharsets.UTF_8));
        for(int design=0;design<30;design++)for(boolean sliding:new boolean[]{false,true})for(boolean framed:new boolean[]{false,true}){
            digest.update(TileEntitySpaceDoor.modelId(design,sliding,framed).getBytes(StandardCharsets.UTF_8));
            for(int detail=0;detail<3;detail++)for(boolean paired:new boolean[]{false,true})for(boolean right:new boolean[]{false,true})for(int part=0;part<3;part++){
                digest.update(ByteBuffer.allocate(8).putInt(TileEntitySpaceDoor.metadata(design,detail,framed,paired,right,part)).putInt(TileEntitySpaceDoor.metadata(design,detail,framed,paired,right,part,sliding)).array());cases++;
            }
        }
        StringBuilder text=new StringBuilder();for(byte b:digest.digest())text.append(String.format("%02x",b&255));String expected=System.getProperty("vandorlabs.spaceDoorModelGolden","");if(!expected.isEmpty()&&!expected.equals(text.toString()))throw new AssertionError("Current model/metadata projection changed "+text);
        System.out.println("Space Door actual model reference PASS cases="+cases+" SHA256="+text);
    }
}
