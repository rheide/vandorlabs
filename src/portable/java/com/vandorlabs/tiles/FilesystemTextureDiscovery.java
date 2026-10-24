package com.vandorlabs.tiles;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Immutable filesystem catalog policy shared by loader and resource adapters. */
public final class FilesystemTextureDiscovery {
    public static final int MAX_DIMENSION=2048;
    public static final String SAMPLE="Example/sample_panel.png";
    public static final class Entry {
        public final int key,width,height;
        public final String id,source,category,label;
        public final boolean hidden;
        private Entry(int key,String source,String category,String label,int width,int height,boolean hidden){
            this.key=key;this.id="file_"+Integer.toHexString(key);this.source=source;this.category=category;this.label=label;
            this.width=width;this.height=height;this.hidden=hidden;
        }
        private Entry hidden(){return new Entry(key,source,category,label,width,height,true);}
    }
    public static final class Catalog {
        public final Path root;
        public final List<Entry> entries;
        private Catalog(Path root,List<Entry> entries){this.root=root;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));}
        public Path file(String relative){return root.resolve(relative).normalize();}
    }
    public static int identifier(String relative){
        CRC32 crc=new CRC32();crc.update(relative.getBytes(StandardCharsets.UTF_8));
        return MaterialChoicePolicy.FILESYSTEM_BASE|((int)crc.getValue()&0x3fffffff);
    }
    public static Catalog discover(Path defaultRoot,Consumer<String> warning){
        Path root=Paths.get(System.getProperty("vandorlabs.textureDirectory",defaultRoot.toString())).toAbsolutePath().normalize();
        List<Entry> entries=new ArrayList<>();
        try {
            Files.createDirectories(root);boolean categoryExists;
            try(java.util.stream.Stream<Path> children=Files.list(root)){categoryExists=children.anyMatch(Files::isDirectory);}
            if(!categoryExists){
                Path category=root.resolve("Example");Files.createDirectories(category);Path sample=category.resolve("sample_panel.png");
                if(!Files.exists(sample)){
                    BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,x==0||y==0?0xff637389:x==15||y==15?0xff151a22:0xff323e50);
                    ImageIO.write(image,"PNG",sample.toFile());
                }
            }
            List<Path> files=new ArrayList<>();
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                walk.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .filter(p->p.toAbsolutePath().normalize().startsWith(root)).forEach(files::add);
            }
            files.sort(Comparator.comparing(p->root.relativize(p).toString()));Set<Integer> used=new HashSet<>();
            for(Path file:files)try{
                BufferedImage image=ImageIO.read(file.toFile());
                if(image==null||image.getWidth()>MAX_DIMENSION||image.getHeight()>MAX_DIMENSION)throw new IOException("Expected a PNG no larger than 2048 pixels per dimension");
                String relative=root.relativize(file).toString().replace(File.separatorChar,'/');int id=identifier(relative);
                if(!used.add(id))throw new IOException("Texture identifier collision; rename this file");
                int slash=relative.lastIndexOf('/');String category=slash<0?"Filesystem":relative.substring(0,slash);
                String name=file.getFileName().toString();entries.add(new Entry(id,relative,category,name.substring(0,name.length()-4).replace('_',' '),image.getWidth(),image.getHeight(),false));
            }catch(IOException error){warning.accept("Skipping "+file+": "+error.getMessage());}
            long examples=entries.stream().filter(entry->entry.category.equals("Example")).count();
            if(examples==1)for(int i=0;i<entries.size();i++)if(entries.get(i).source.equals(SAMPLE))entries.set(i,entries.get(i).hidden());
        }catch(IOException error){warning.accept("Could not initialize filesystem textures at "+root+": "+error.getMessage());}
        return new Catalog(root,entries);
    }
    private FilesystemTextureDiscovery(){}
}
