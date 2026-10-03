package com.vandorlabs.tiles;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Common-side discovery; texture identifiers depend on relative paths, never list order. */
public final class FilesystemTextures {
    public static final int ID_BASE=0x40000000;
    private static final List<JsonObject> entries=new ArrayList<>();
    private static Path root;
    private FilesystemTextures(){ }
    public static synchronized void initialize(Path defaultRoot) {
        if(root!=null)return;
        root=Paths.get(System.getProperty("vandorlabs.textureDirectory",defaultRoot.toString())).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            boolean categoryExists;
            try(java.util.stream.Stream<Path> children=Files.list(root)){categoryExists=children.anyMatch(Files::isDirectory);}
            if(!categoryExists) {
                Path category=root.resolve("Example");Files.createDirectories(category);
                Path sample=category.resolve("sample_panel.png");
                if(!Files.exists(sample)) {
                    BufferedImage image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,x==0 || y==0?0xff637389:x==15 || y==15?0xff151a22:0xff323e50);
                    ImageIO.write(image,"PNG",sample.toFile());
                }
            }
            List<Path> files=new ArrayList<>();
            try(java.util.stream.Stream<Path> walk=Files.walk(root)) {
                walk.filter(p->Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .filter(p->p.toAbsolutePath().normalize().startsWith(root)).forEach(files::add);
            }
            files.sort(Comparator.comparing(p->root.relativize(p).toString()));
            Set<Integer> used=new HashSet<>();
            for(Path file:files)try {
                BufferedImage image=ImageIO.read(file.toFile());
                if(image==null || image.getWidth()>2048 || image.getHeight()>2048)throw new IOException("Expected a PNG no larger than 2048 pixels per dimension");
                String relative=root.relativize(file).toString().replace(File.separatorChar,'/');
                int id=identifier(relative);
                if(!used.add(id))throw new IOException("Texture identifier collision; rename this file");
                JsonObject e=new JsonObject();e.addProperty("id","file_"+Integer.toHexString(id));
                e.addProperty("key",id);e.addProperty("source",relative);e.addProperty("file",relative);
                e.addProperty("rectangular",true);
                int slash=relative.lastIndexOf('/');e.addProperty("category",slash<0?"Filesystem":relative.substring(0,slash));
                String name=file.getFileName().toString();e.addProperty("label",name.substring(0,name.length()-4).replace('_',' '));entries.add(e);
            }catch(IOException e){warn("Skipping "+file+": "+e.getMessage());}
            // Keep the sample's identifier resolvable in old saves, but omit its
            // category when no other Example artwork has been added.
            long examples=entries.stream().filter(e->"Example".equals(e.get("category").getAsString())).count();
            if(examples==1)for(JsonObject e:entries)
                if("Example/sample_panel.png".equals(e.get("source").getAsString()))e.addProperty("hidden",true);
        }catch(IOException e){warn("Could not initialize filesystem textures at "+root+": "+e.getMessage());}
    }
    public static int identifier(String relative){CRC32 crc=new CRC32();try{crc.update(relative.getBytes("UTF-8"));}catch(UnsupportedEncodingException impossible){throw new AssertionError(impossible);}return ID_BASE | ((int)crc.getValue() & 0x3fffffff);}
    public static List<JsonObject> entries(){return Collections.unmodifiableList(entries);}
    public static Path file(String relative){return root.resolve(relative).normalize();}
    private static void warn(String message){if(com.vandorlabs.VandorLabs.logger!=null)com.vandorlabs.VandorLabs.logger.warn(message);else System.err.println("[Vandor Labs] "+message);}
}
