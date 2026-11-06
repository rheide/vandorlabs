package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.client.NativeMaterialPlayback;
import com.vandorlabs.neoforge.screen.NativeScreenMediaCatalog;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.util.*;
import net.minecraft.server.packs.resources.ResourceManager;

/** Bake-owned Screen atlas regions and builtin media; resource inspection never occurs during emission. */
final class NativeScreenMaterials {
 record Atlas(SegmentedPanelMesh.Texture pixels,double aspect){}
 record Frame(SegmentedPanelMesh.Texture pixels,NativeTrapdoorFrameBorders.Borders borders,double aspect){}
 private record Texture(float u,float v,float du,float dv) implements SegmentedPanelMesh.Texture {public float u(double pixel){return u+du*(float)pixel/16F;}public float v(double pixel){return v+dv*(float)pixel/16F;}}
 private final NativeFlatWallModel.Bake source;private final ResourceManager resources;
 private final Map<Integer,Atlas> atlas=new LinkedHashMap<>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<Integer,Atlas> entry){return size()>4096;}};
 private final Map<Integer,Frame> frames=new LinkedHashMap<>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<Integer,Frame> entry){return size()>4096;}};
 private final Map<String,NativeMaterialPlayback.Material> inputs=new HashMap<>();
 NativeScreenMaterials(NativeFlatWallModel.Bake source,ResourceManager resources){this.source=Objects.requireNonNull(source);this.resources=Objects.requireNonNull(resources);}
 private ProgrammableModel.MaterialSnapshot snapshot(int choice){var found=source.surfaces.get(choice);if(found!=null)return found;var custom=com.vandorlabs.neoforge.client.NativeBlockMaterials.material(choice);if(custom!=null)return new ProgrammableModel.MaterialSnapshot(custom.lower(),1,1);found=source.surfaces.get(ModernFinishCatalog.storedChoice(choice));return found!=null?found:source.surfaces.get(0);}
 Atlas atlas(int choice){var found=atlas.get(choice);if(found!=null)return found;var value=snapshot(choice);var sprite=value.sprite();found=new Atlas(new Texture(sprite.getU0(),sprite.getV0(),(sprite.getU1()-sprite.getU0())*value.usedU(),(sprite.getV1()-sprite.getV0())*value.usedV()),value.usedU()/value.usedV());atlas.put(choice,found);return found;}
 Frame frame(int choice){var found=frames.get(choice);if(found!=null)return found;var value=snapshot(choice);var sprite=value.sprite();var entry=ModernFinishCatalog.option(choice);double aspect=entry.frame()==null?1:entry.frame().aspect();found=new Frame(new Texture(sprite.getU0(),sprite.getV0(),sprite.getU1()-sprite.getU0(),sprite.getV1()-sprite.getV0()),NativeTrapdoorFrameBorders.read(resources,entry),aspect);frames.put(choice,found);return found;}
 boolean glowing(int choice){if(choice<0)return false;var entry=ModernFinishCatalog.option(choice);return entry!=null&&entry.frame()!=null&&entry.frame().light()>0;}
 NativeMaterialPlayback.Material display(int choice){var value=source.playback(choice);if(value==null)throw new IllegalStateException("Missing Screen display media "+choice);return value;}
 NativeMaterialPlayback.Material input(NativeScreenMediaCatalog.Input input){return inputs.computeIfAbsent(input.canonical(),ignored->new NativeMaterialPlayback.Material(input.playback()));}
}
