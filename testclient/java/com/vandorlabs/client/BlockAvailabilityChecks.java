package com.vandorlabs.client;

import com.vandorlabs.config.BlockAvailability;
import com.vandorlabs.config.BlockCategories;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.ResourceLocation;

/** Startup defaults, persisted switches and category boundaries. */
final class BlockAvailabilityChecks {
    static void run() {
        try {
            Path directory=Files.createTempDirectory("vandorlabs-blocks-");
            Path file=directory.resolve("blocks.cfg");
            java.io.File previousHome=(java.io.File)net.minecraftforge.fml.relauncher.FMLInjectionData.data()[6];
            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.fml.relauncher.FMLInjectionData.class,null,directory.toFile(),"minecraftHome");
            try {
                BlockAvailability.load(file.toFile());
                check(Files.isRegularFile(file),"missing config was not generated");
                String defaults=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
                for(String category:BlockCategories.IDS)check(defaults.contains("B:"+category+"=true"),"missing enabled category "+category);
                Files.write(file,defaults.replace("B:glass=true","B:glass=false").replace("B:ramps=true","B:ramps=false").getBytes(StandardCharsets.UTF_8));
                BlockAvailability.load(file.toFile());
                check(!enabled("programmable_glass")&&!enabled("clear_cockpit_glass"),"glass toggle ignored");
                check(enabled("programmable_diagonal_glass"),"diagonal category coupled to regular glass");
                check(!enabled("programmable_ramp")&&!enabled("controlled_ramp"),"ramp category split");
                check(enabled("programmable_door")&&enabled("pilot_seat"),"unrelated category disabled");
                check(BlockAvailability.enabled(new ResourceLocation("minecraft","glass")),"vanilla affected");
                check(BlockCategories.of("space_visor_5x1").equals("canopies"),"canopy classification");
                Files.write(file,defaults.getBytes(StandardCharsets.UTF_8));
                BlockAvailability.load(file.toFile());
            } finally {
                net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraftforge.fml.relauncher.FMLInjectionData.class,null,previousHome,"minecraftHome");
                Files.deleteIfExists(file);Files.deleteIfExists(directory);
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("PASS: generated category defaults, persisted acquisition switches, independent diagonals and vanilla preservation");
    }
    private static boolean enabled(String id){return BlockAvailability.enabled(new ResourceLocation("vandorlabs",id));}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException("Block availability: "+message);}
}
