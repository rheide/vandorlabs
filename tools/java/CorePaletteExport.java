import com.vandorlabs.geometry.CoreMesh;
import java.util.TreeSet;

/** Asset-generation manifest: shared flat frame colors and unchanged legacy center artwork. */
public final class CorePaletteExport {
    public static void main(String[] args) {
        TreeSet<Integer> colors=new TreeSet<Integer>();
        for(String id:CoreMesh.IDS)for(CoreMesh.Face face:CoreMesh.faces(id))if(!face.textured)colors.add(face.rgb);
        System.out.print("{\"colors\":[");boolean first=true;
        for(int color:colors){if(!first)System.out.print(",");System.out.print(color);first=false;}
        System.out.print("],\"textures\":{");first=true;
        for(String id:CoreMesh.IDS){if(!first)System.out.print(",");System.out.print("\""+id+"\":\""+CoreMesh.legacyTexture(id)+"\"");first=false;}
        System.out.println("}}");
    }
}
