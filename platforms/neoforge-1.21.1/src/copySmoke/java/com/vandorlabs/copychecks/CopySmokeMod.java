package com.vandorlabs.copychecks;

import com.vandorlabs.neoforge.VandorLabsBootstrap;
import com.vandorlabs.neoforge.redstone.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Separate fixture mod: the standard production JAR remains unmodified and has no smoke classes. */
@Mod("vandorlabs_copy_smoke")
public final class CopySmokeMod {
    public CopySmokeMod(){
        try{
            if(!System.getProperty("fml.modFolders","").isBlank())throw new IllegalStateException("source modFolders must be empty");
            Path jar=Path.of(System.getProperty("vandorlabs.copyJar"));String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
            if(!sha.equals(System.getProperty("vandorlabs.copySha")))throw new IllegalStateException("standard JAR hash mismatch");
            for(Class<?> type:new Class<?>[]{VandorLabsBootstrap.class,NativeTransmitterItem.class,TransmitterCraftingContext.class,TransmitterCopyRecipe.class,TransmitterCopyRegistration.class,NativeRedstoneChannels.class,ManualSavedData.class,TransmitterMenu.class,TransmitterPayload.class,com.vandorlabs.redstone.ChannelList.class}){
                String source=type.getProtectionDomain().getCodeSource().getLocation().toString();if(!source.contains(jar.getFileName().toString())||source.contains("/classes/"))throw new IllegalStateException("production class source fallback: "+type.getName()+" source="+source);
            }
            System.out.println("[vandorlabs][transmitter-copy] packaged-JAR PASS sha256="+sha+" production-code-sources=10 empty-modFolders separate-fixture-mod");
            NeoForge.EVENT_BUS.register(TransmitterCopyRuntimeChecks.class);
            NeoForge.EVENT_BUS.register(CopyServerChecks.class);
            if(System.getProperty("vandorlabs.transmitterPhase","").equals("binary-ui")){
                for(String name:new String[]{"NativeBinaryControlBlockEntity","NativeMountedSwitchBlock","BinaryControlMenu","BinaryControlPayload","BinaryTransmissionPayload"}){
                    Class<?> type=Class.forName("com.vandorlabs.neoforge."+name);String source=type.getProtectionDomain().getCodeSource().getLocation().toString();if(!source.contains(jar.getFileName().toString()))throw new IllegalStateException("binary common source fallback: "+name);
                }
                NeoForge.EVENT_BUS.register(BinaryGuiServerChecks.class);
                System.out.println("[vandorlabs][binary-gui] packaged-JAR common PASS exact-hash source-classes=5");
            }
            if(net.neoforged.fml.loading.FMLEnvironment.dist==net.neoforged.api.distmarker.Dist.CLIENT){NeoForge.EVENT_BUS.register(Class.forName("com.vandorlabs.copychecks.CopyClientChecks"));if(System.getProperty("vandorlabs.transmitterPhase","").equals("binary-ui")){
                    for(String name:new String[]{"NativeBinaryControlScreen","NativeInlineTransmissionWidget"}){Class<?> type=Class.forName("com.vandorlabs.neoforge.client."+name);if(!type.getProtectionDomain().getCodeSource().getLocation().toString().contains(jar.getFileName().toString()))throw new IllegalStateException("binary client source fallback: "+name);}
                    NeoForge.EVENT_BUS.register(Class.forName("com.vandorlabs.copychecks.BinaryGuiClientChecks"));System.out.println("[vandorlabs][binary-gui] packaged-JAR client PASS exact-hash source-classes=2");
                }}
        }catch(Exception failure){throw new IllegalStateException("Native transmitter copy: packaged fixture bootstrap",failure);}
    }
}
