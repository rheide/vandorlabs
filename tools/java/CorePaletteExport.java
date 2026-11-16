import com.vandorlabs.geometry.CoreMesh;
import java.util.TreeSet;

/** Emit the exact shared vertex colors for Dynmap's flat-pixel material adapter. */
public final class CorePaletteExport {
    public static void main(String[] args) {
        TreeSet<Integer> colors=new TreeSet<Integer>();
        for(String id:CoreMesh.IDS)for(CoreMesh.Face face:CoreMesh.faces(id))colors.add(face.rgb);
        System.out.print("[");boolean first=true;
        for(int color:colors){if(!first)System.out.print(",");System.out.print(color);first=false;}
        System.out.println("]");
    }
}
