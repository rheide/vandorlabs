package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.door.NativeDoorAssets;
import com.vandorlabs.segmented.SegmentedPanelMesh;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Generation-owned full artwork and frame pixels, shared by every Trapdoor pose. */
public final class NativeTrapdoorMaterials implements NativeTrapdoorRenderer.Materials {
    public record Surface(SegmentedPanelMesh.Texture upper,SegmentedPanelMesh.Texture lower,double aspect,
                          boolean doorArtwork,boolean customDoor,NativeTrapdoorFrameBorders.Borders borders,double frameAspect){}
    private final NativeFlatWallModel.Bake materials;
    private final Function<Material,TextureAtlasSprite> sprites;
    private final ResourceManager resources;
    private final Set<String> fullSprites=new HashSet<>();
    private final Map<Integer,Surface> cache=new LinkedHashMap<>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<Integer,Surface> entry){return size()>4096;}};
    private final SegmentedPanelMesh.Texture metal;
    NativeTrapdoorMaterials(NativeFlatWallModel.Bake materials,Function<Material,TextureAtlasSprite> sprites,ResourceManager resources){
        this.materials=materials;this.sprites=sprites;this.resources=resources;
        for(var texture:NativeDoorAssets.textures())fullSprites.add(texture.id());
        var value=materials.originalMetal.surface();metal=texture(value.sprite(),value.usedU(),value.usedV());
    }
    public SegmentedPanelMesh.Texture metal(){return metal;}
    public Surface surface(int choice){
        var found=cache.get(choice);if(found!=null)return found;
        var custom=com.vandorlabs.neoforge.client.NativeBlockMaterials.material(choice);
        if(custom!=null){
            // Released customTexture selects the lower sprite; its physical aspect controls Fit.
            double aspect=(float)custom.lower().contents().width()/custom.lower().contents().height();
            found=new Surface(texture(custom.upper(),1,1),texture(custom.lower(),1,1),aspect,custom.door(),custom.door(),null,1);
        }else{
            var entry=ModernFinishCatalog.option(choice);var preview=NativeDoorAssets.preview(choice);
            ProgrammableModel.MaterialSnapshot value=materials.surfaces.get(choice);
            if(value==null)value=materials.surfaces.get(ModernFinishCatalog.storedChoice(choice));
            if(value==null)value=materials.surfaces.get(0);
            if(fullSprites.contains(preview.texture())){var sprite=sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(preview.texture())));if(!sprite.contents().name().getPath().equals("missingno"))value=new ProgrammableModel.MaterialSnapshot(sprite,NativeDoorArtwork.usedU(sprite),NativeDoorArtwork.usedV(sprite));}
            var image=texture(value.sprite(),value.usedU(),value.usedV());var frame=entry.frame();
            found=new Surface(image,image,value.usedU()/value.usedV(),entry.door()!=null,false,
                    frame!=null&&frame.border()?NativeTrapdoorFrameBorders.read(resources,entry):null,frame==null?1:frame.aspect());
        }
        cache.put(choice,found);return found;
    }
    private static SegmentedPanelMesh.Texture texture(TextureAtlasSprite sprite,float usedU,float usedV){
        float u0=sprite.getU0(),u1=u0+(sprite.getU1()-u0)*usedU,v0=sprite.getV0(),v1=v0+(sprite.getV1()-v0)*usedV;
        return new SegmentedPanelMesh.Texture(){public float u(double pixel){return u0+(u1-u0)*(float)pixel/16F;}public float v(double pixel){return v0+(v1-v0)*(float)pixel/16F;}};
    }
}
