package com.vandorlabs.tiles;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public final class FilesystemTextureDiscoveryTest {
    private static int assertions;
    private static void check(boolean value){assertions++;if(!value)throw new AssertionError("Filesystem material discovery contract");}
    private static void image(Path path,int width,int height)throws Exception{Files.createDirectories(path.getParent());ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB),"PNG",path.toFile());}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("material-discovery");List<String> warnings=new ArrayList<>();
        String previous=System.getProperty("vandorlabs.textureDirectory");System.clearProperty("vandorlabs.textureDirectory");
        try{
            FilesystemTextureDiscovery.Catalog empty=FilesystemTextureDiscovery.discover(root,warnings::add);
            check(empty.entries.size()==1&&empty.entries.get(0).hidden);check(empty.entries.get(0).source.equals("Example/sample_panel.png"));
            check(empty.entries.get(0).width==16&&empty.entries.get(0).height==16);
            BufferedImage sample=ImageIO.read(empty.file(FilesystemTextureDiscovery.SAMPLE).toFile());check(sample.getRGB(0,0)==0xff637389&&sample.getRGB(15,15)==0xff151a22&&sample.getRGB(8,8)==0xff323e50);
            image(root.resolve("Example/user_panel.png"),31,47);image(root.resolve("Hull/Nested/blue_panel.PNG"),2048,1);image(root.resolve("oversized.png"),2049,1);Files.write(root.resolve("broken.png"),new byte[]{1,2,3});
            FilesystemTextureDiscovery.Catalog found=FilesystemTextureDiscovery.discover(root,warnings::add);
            check(found.entries.size()==3&&warnings.size()==2);check(found.entries.stream().noneMatch(entry->entry.hidden));
            FilesystemTextureDiscovery.Entry nested=found.entries.stream().filter(entry->entry.source.equals("Hull/Nested/blue_panel.PNG")).findFirst().get();
            check(nested.category.equals("Hull/Nested")&&nested.label.equals("blue panel"));check(nested.width==2048&&nested.height==1);
            check(nested.key==FilesystemTextureDiscovery.identifier(nested.source));check(nested.id.equals("file_"+Integer.toHexString(nested.key)));
            check(FilesystemTextureDiscovery.identifier("Example/sample_panel.png")==0x5d14df28);
            check(FilesystemTextureDiscovery.identifier("Hull/Nested/blue_panel.PNG")!=FilesystemTextureDiscovery.identifier("Hull/Nested/blue_panel.png"));
            boolean immutable=false;try{found.entries.clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable);
            System.setProperty("vandorlabs.textureDirectory",root.toString());check(FilesystemTextureDiscovery.discover(root.resolve("ignored"),warnings::add).root.equals(root.toAbsolutePath().normalize()));
        }finally{
            if(previous==null)System.clearProperty("vandorlabs.textureDirectory");else System.setProperty("vandorlabs.textureDirectory",previous);
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){for(Path path:(Iterable<Path>)walk.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(path);}
        }
        System.out.println("FilesystemTextureDiscoveryTest PASS "+assertions+" sample, identity, nesting, visibility, dimension, PNG and immutable catalog checks");
    }
}
