package com.vandorlabs.neoforge;

import com.google.gson.*;
import java.nio.file.*;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;

/** Read actual Forge adapter inputs before native deferred registration, independent of native writes. */
public final class BinaryLegacyBankChecks {
    private static int[] ints(JsonArray array){int[] result=new int[array.size()];for(int i=0;i<result.length;i++)result[i]=array.get(i).getAsInt();return result;}
    public static void run(ServerLevel world){
        String file=System.getProperty("vandorlabs.binaryLegacyReference","");if(file.isEmpty())return;
        try{
            byte[] bytes=Files.readAllBytes(Path.of(file));var root=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1||!root.get("legacyGolden").getAsString().equals("4daaa74c043e3954dfefa6eae92f4ab6baa87f2c2fa939745852515a0174b0d4")||root.getAsJsonArray("cases").size()!=1280)throw new IllegalStateException("Unexpected Forge bank reference");
            int count=0;for(JsonElement raw:root.getAsJsonArray("cases")){
                var fixture=raw.getAsJsonObject();CompoundTag input=TagParser.parseTag(fixture.get("input").getAsString()),before=input.copy();var tile=new NativeBinaryControlBlockEntity(BlockPos.ZERO,VandorLabsBootstrap.ROCKER_SWITCH.get().defaultBlockState());tile.loadWithComponents(input,world.registryAccess());
                boolean valid=input.equals(before)&&Arrays.equals(tile.getRedstoneChannels().toArray(),ints(fixture.getAsJsonArray("channels")))&&Arrays.equals(tile.latchedChannels().toArray(),ints(fixture.getAsJsonArray("latched")))&&tile.getOutputLevel()==fixture.get("output").getAsInt()&&tile.isLocalOn()==fixture.get("localOn").getAsBoolean()&&tile.configuration().equals(TagParser.parseTag(fixture.get("configuration").getAsString()));
                int[] channels=ints(fixture.getAsJsonArray("probeChannels")),levels=ints(fixture.getAsJsonArray("probeLevels"));for(int i=0;i<channels.length;i++)valid&=tile.latchedLevel(channels[i])==levels[i];
                var saved=tile.saveWithFullMetadata(world.registryAccess());for(String opaque:new String[]{"ForgeCaps","ForgeData"})if(input.contains(opaque))valid&=input.get(opaque).equals(saved.get(opaque));
                if(!valid)throw new IllegalStateException("Native binary legacy bank mismatch case="+count+" input="+input+" nativeConfiguration="+tile.configuration());count++;
            }
            System.out.println("[vandorlabs][binary-controls] Forge-bank-reference PASS cases="+count+" SHA256="+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))+" actual Forge inputs/configuration/numeric banks/opaque data before registration");
        }catch(Exception failure){throw new IllegalStateException("Native binary legacy reference failed",failure);}
    }
    private BinaryLegacyBankChecks(){}
}
