package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.nbt.NBTTagCompound;

/** Filesystem bootstrap and missing-peer identities, without graphics or a server. */
public final class FilesystemTextureChecks {
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_filesystem_check"));
        Path root=Files.createTempDirectory("vandorlabs-textures-");
        try {
            if(args.length>0 && args[0].equals("bootstrap")) {
                FilesystemTextures.initialize(root);
                require(Files.isRegularFile(root.resolve("Example/sample_panel.png")),"first-run sample");
                require(ImageIO.read(root.resolve("Example/sample_panel.png").toFile())!=null,"valid sample PNG");
                byte[] before=Files.readAllBytes(root.resolve("Example/sample_panel.png"));
                FilesystemTextures.initialize(root);
                require(java.util.Arrays.equals(before,Files.readAllBytes(root.resolve("Example/sample_panel.png"))),"preserved sample");
                System.out.println("PASS: first-run category and sample PNG, preserved on subsequent initialization");return;
            }
            Files.createDirectories(root.resolve("Bridge"));Files.createDirectories(root.resolve("Engineering/Panels"));
            BufferedImage image=new BufferedImage(16,32,BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(image,"PNG",root.resolve("Bridge/blue.png").toFile());
            ImageIO.write(image,"PNG",root.resolve("Engineering/Panels/red.png").toFile());
            FilesystemTextures.initialize(root);
            int blue=FilesystemTextures.identifier("Bridge/blue.png"),red=FilesystemTextures.identifier("Engineering/Panels/red.png");
            require(blue!=red && ScreenHousingTextures.validChoice(blue),"stable identifiers");
            require("Bridge".equals(ScreenHousingTextures.category(blue)),"category");
            require("Engineering/Panels".equals(ScreenHousingTextures.category(red)),"nested category");
            require(ScreenHousingTextures.choiceAt(ScreenHousingTextures.localIndex(blue))==blue,"local selection mapping");
            int absent=FilesystemTextures.identifier("DifferentClient/missing.png");
            require(ScreenHousingTextures.clamp(absent)==absent && ScreenHousingTextures.texture(absent).equals(ScreenHousingTextures.texture(0)),"missing peer fallback and identity");
            TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setHousingTexture(absent);tile.setSurfaceTexture(0,blue);
            NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityAnimatedScreenSelector restored=new TileEntityAnimatedScreenSelector();restored.readFromNBT(saved);
            require(restored.getHousingTexture()==absent && restored.getSurfaceTexture(0)==blue,"NBT retained absent peer choices");
            HousingTextureList list=new HousingTextureList(0,0,120,absent);require(list.selected()==absent,"opening a missing selection replaced its identity");list.click(10,1,0);require(list.selected()==absent,"expanding a category replaced a missing selection");
            require(new FaceTextures(true,new int[]{blue,red,absent,-1,-1,-1}).choice(2)==absent,"per-face identity");
            System.out.println("PASS: filesystem categories, stable identities, missing-peer fallback and saved choices");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(java.io.IOException e){throw new RuntimeException(e);}});}}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
