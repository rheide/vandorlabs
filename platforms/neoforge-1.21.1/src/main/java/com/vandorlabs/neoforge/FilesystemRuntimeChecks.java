package com.vandorlabs.neoforge;

import com.vandorlabs.tiles.FilesystemTextureDiscovery;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLPaths;

/** Opt-in files and native persistence fixtures inside the target development configuration. */
public final class FilesystemRuntimeChecks {
    public static final String SOURCE="Runtime checks/portrait_panel.png";
    public static final int CHOICE=FilesystemTextureDiscovery.identifier(SOURCE);
    public static final BlockPos POS=new BlockPos(-2,80,0);
    public static void prepare(){
        if(System.getProperty("vandorlabs.modernSmoke","").isEmpty()&&!Boolean.getBoolean("vandorlabs.modernClientFixture")&&!Boolean.getBoolean("vandorlabs.modernClientSmoke"))return;
        Path root=FilesystemTextureDiscovery.root(FMLPaths.CONFIGDIR.get().resolve("vandorlabs/textures"));
        try{write(root.resolve(SOURCE),32,64);}catch(IOException error){throw new IllegalStateException("Filesystem fixture setup",error);}
    }
    public static void write(Path path,int width,int height)throws IOException{
        Files.createDirectories(path.getParent());BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,y<height/2?(x<width/2?0xffe04c32:0xff3259e0):(x<width/2?0xff32c874:0xffe0b832));
        if(!ImageIO.write(image,"PNG",path.toFile()))throw new IOException("PNG writer unavailable");
    }
    public static void clientFixture(ServerLevel level){
        level.getChunkAt(POS);level.setBlock(POS,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        level.setBlock(POS,VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
        ((ProgrammableBlockEntity)level.getBlockEntity(POS)).apply(new ProgrammableBlockDialog.State(CHOICE,0,false,null,-1));
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Filesystem runtime: "+message);}
    public static void run(ServerLevel level){
        clientFixture(level);var tile=(ProgrammableBlockEntity)level.getBlockEntity(POS);
        require(FilesystemMaterials.catalog().entries.stream().anyMatch(entry->entry.key==CHOICE&&entry.width==32&&entry.height==64),"shared discovery/native catalog mismatch");
        CompoundTag saved=tile.saveWithoutMetadata(level.registryAccess());var restored=new ProgrammableBlockEntity(POS,tile.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
        require(restored.settings().mainTexture==CHOICE&&restored.settings().layout==0,"stable filesystem material ID/schema roundtrip");
        System.out.println("[vandorlabs][modern] filesystem-write PASS stable shared discovery, native material choice and NBT identity");
    }
    public static void read(ServerLevel level){
        level.getChunkAt(POS);require(level.getBlockEntity(POS) instanceof ProgrammableBlockEntity,"saved filesystem block missing");
        var tile=(ProgrammableBlockEntity)level.getBlockEntity(POS);
        require(tile.settings().mainTexture==CHOICE&&tile.settings().layout==0,"filesystem ID changed across process restart");
        System.out.println("[vandorlabs][modern] filesystem-read PASS exact stable material ID recovered from disk");
    }
    private FilesystemRuntimeChecks(){}
}
