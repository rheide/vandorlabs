package com.vandorlabs.neoforge;

import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.util.*;

/** Atlas-generation Light materials reuse the catalog's actual square-content regions. */
final class NativeLightMaterials implements NativeLightRenderer.Materials {
    private final NativeFlatWallModel.Bake source;
    private final Map<Integer,NativeLightRenderer.Surface> cache=new LinkedHashMap<>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<Integer,NativeLightRenderer.Surface> entry){return size()>4096;}};
    NativeLightMaterials(NativeFlatWallModel.Bake source){this.source=Objects.requireNonNull(source);}
    public NativeLightRenderer.Surface surface(int choice){var found=cache.get(choice);if(found!=null)return found;var value=source.surfaces.get(choice);double aspect;
        if(value==null){var custom=com.vandorlabs.neoforge.client.NativeBlockMaterials.material(choice);if(custom!=null){value=new ProgrammableModel.MaterialSnapshot(custom.lower(),1,1);aspect=(float)custom.lower().contents().width()/custom.lower().contents().height();}else{value=source.surfaces.get(ModernFinishCatalog.storedChoice(choice));if(value==null)value=source.surfaces.get(0);aspect=value.usedU()/value.usedV();}}
        else aspect=value.usedU()/value.usedV();
        var sprite=value.sprite();float u0=sprite.getU0(),u1=u0+(sprite.getU1()-u0)*value.usedU(),v0=sprite.getV0(),v1=v0+(sprite.getV1()-v0)*value.usedV();
        found=new NativeLightRenderer.Surface(new SegmentedPanelMesh.Texture(){public float u(double pixel){return u0+(u1-u0)*(float)pixel/16F;}public float v(double pixel){return v0+(v1-v0)*(float)pixel/16F;}},aspect);cache.put(choice,found);return found;
    }
}
