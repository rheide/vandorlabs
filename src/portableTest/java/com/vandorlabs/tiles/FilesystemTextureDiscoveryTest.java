package com.vandorlabs.tiles;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;

/** Real PNG discovery, portable saved identities and legacy publication/failure boundaries. */
public final class FilesystemTextureDiscoveryTest {
    private static int assertions;
    private static void check(boolean pass,String message){assertions++;if(!pass)throw new AssertionError(message);}
    private static void image(Path path,int width,int height)throws Exception{Files.createDirectories(path.getParent());ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB),"PNG",path.toFile());}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("filesystem-discovery");String previous=System.getProperty("vandorlabs.textureDirectory");System.clearProperty("vandorlabs.textureDirectory");
        try{
            List<String> warnings=new ArrayList<>();List<FilesystemTextureDiscovery.Entry> published=new ArrayList<>();
            FilesystemTextureDiscovery.Catalog initial=FilesystemTextureDiscovery.scan(root,warnings::add,(index,entry)->{if(index==published.size())published.add(entry);else published.set(index,entry);});
            check(initial.entries.size()==1&&initial.entries.get(0).hidden,"sole Example sample stays hidden");
            check(published.size()==1&&published.get(0).hidden,"hidden flag replaces the same indexed publication");
            BufferedImage sample=ImageIO.read(initial.file(FilesystemTextureDiscovery.SAMPLE).toFile());
            check(sample.getWidth()==16&&sample.getHeight()==16&&sample.getRGB(0,0)==0xff637389&&sample.getRGB(15,15)==0xff151a22&&sample.getRGB(8,8)==0xff323e50,"exact default sample dimensions and pixels");
            image(root.resolve("Example/user_panel.png"),31,47);image(root.resolve("Hull/Nested/blue_panel.PNG"),2048,1);image(root.resolve("Étage/青い_panel.PNG"),1,2048);
            image(root.resolve("oversized.png"),2049,1);Files.write(root.resolve("broken.png"),new byte[]{1,2,3});
            String first="Collision/RSrHg1FMZS9h.png",second="Collision/oPsFHYGYMOOZ.png";
            image(root.resolve(second),1,1);image(root.resolve(first),1,1);
            check(FilesystemTextureDiscovery.identifier(first)==0x576abe23&&FilesystemTextureDiscovery.identifier(second)==0x576abe23,"real truncated CRC collision fixture");
            FilesystemTextureDiscovery.Catalog found=FilesystemTextureDiscovery.discover(root,warnings::add);
            check(found.entries.size()==5&&warnings.size()==3,"invalid PNG, oversize and collision are skipped separately");
            check(found.entries.stream().noneMatch(e->e.hidden),"populated Example category is visible");
            check(found.entries.get(0).source.equals(first),"platform lexical scan order chooses first collision independently of creation order");
            check(warnings.stream().anyMatch(s->s.equals("Skipping "+root.resolve(second)+": Texture identifier collision; rename this file")),"collision diagnostic unchanged");
            check(warnings.stream().filter(s->s.endsWith("Expected a PNG no larger than 2048 pixels per dimension")).count()==2,"dimension and decode diagnostics unchanged");
            FilesystemTextureDiscovery.Entry nested=found.entries.stream().filter(e->e.source.equals("Hull/Nested/blue_panel.PNG")).findFirst().get();
            check(nested.category.equals("Hull/Nested")&&nested.label.equals("blue panel")&&nested.width==2048&&nested.height==1,"nested category, case and accepted limit");
            check(FilesystemTextureDiscovery.identifier("Example/sample_panel.png")==0x5d14df28,"legacy sample saved ID");
            check(FilesystemTextureDiscovery.identifier("Étage/青い_panel.PNG")==0x657e66a2,"Unicode UTF-8 saved ID");
            check(FilesystemTextureDiscovery.identifier("Folder/x.png")==0x4de3f368&&FilesystemTextureDiscovery.identifier("Folder\\x.png")==0x40558c07,"raw identifier input is not silently path-normalized");
            check(FilesystemTextureDiscovery.identifier("Hull/Nested/blue_panel.PNG")!=FilesystemTextureDiscovery.identifier("Hull/Nested/blue_panel.png"),"file case remains part of identity");
            boolean immutable=false;try{found.entries.clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"published catalog is immutable");
            check(found.file("Hull/../broken.png").equals(root.resolve("broken.png")),"legacy file resolution normalizes relative path");
            System.setProperty("vandorlabs.textureDirectory",root.toString());check(FilesystemTextureDiscovery.discover(root.resolve("ignored"),warnings::add).root.equals(root.toAbsolutePath().normalize()),"configured root override");System.clearProperty("vandorlabs.textureDirectory");
            Path blocked=root.resolve("not-a-directory");Files.write(blocked,new byte[]{1});List<String> failure=new ArrayList<>();
            check(FilesystemTextureDiscovery.discover(blocked,failure::add).entries.isEmpty()&&failure.size()==1&&failure.get(0).startsWith("Could not initialize filesystem textures at "+blocked+": "),"initialization IOException warning and empty result");
            List<String> prefix=new ArrayList<>();boolean failed=false;
            try{FilesystemTextureDiscovery.scan(root,s->{},(index,entry)->{if(index==1)throw new IllegalStateException("publication failure");prefix.add(entry.source);});}catch(IllegalStateException expected){failed=true;}
            check(failed&&prefix.equals(Collections.singletonList(first)),"accepted prefix published before a later unchecked failure");
        }finally{
            if(previous==null)System.clearProperty("vandorlabs.textureDirectory");else System.setProperty("vandorlabs.textureDirectory",previous);
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}
        }
        System.out.println("FilesystemTextureDiscovery contracts PASS assertions="+assertions+" PNG-limits Unicode raw-path CRC-collision order defaults categories publication failures");
    }
}
