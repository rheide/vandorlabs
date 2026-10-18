package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.CanopyMesh;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.lwjgl.util.vector.Vector3f;

import java.util.*;

public final class CanopyModels {
    @SubscribeEvent
    public void register(ModelRegistryEvent event) {
        for (net.minecraft.block.Block b : ModBlocks.BLOCKS)
            if (b instanceof BlockCanopy)
                net.minecraftforge.client.model.ModelLoader.setCustomStateMapper(
                        b,
                        new net.minecraft.client.renderer.block.statemap.StateMap.Builder()
                                .ignore(BlockCanopy.FACING)
                                .build());
    }

    @SubscribeEvent
    public void textures(TextureStitchEvent.Pre event) {
        for (String m : CanopyMesh.MATERIALS)
            event.getMap().registerSprite(new ResourceLocation("vandorlabs:blocks/canopy/" + m));
    }

    @SubscribeEvent
    public void bake(ModelBakeEvent event) {
        Icon.CONFIGURED.clear();
        TECanopy.invalidate();
        for (net.minecraft.block.Block b : ModBlocks.BLOCKS)
            if (b instanceof BlockCanopy) {
                String id = ((BlockCanopy) b).kind;
                event.getModelRegistry()
                        .putObject(
                                new ModelResourceLocation(b.getRegistryName(), "inventory"),
                                new Icon(
                                        id.equals("regular_canopy_glass") ? "regular_1x1_00" : id));
            }
    }

    public static final class Icon implements IBakedModel {
        private final String kind;
        private static final Map<String,IBakedModel> CONFIGURED=new LinkedHashMap<String,IBakedModel>(32,.75F,true){protected boolean removeEldestEntry(Map.Entry<String,IBakedModel> entry){return size()>64;}};
        private final List<BakedQuad> quads = new ArrayList<>();
        private final TextureAtlasSprite particle;
        private final ItemCameraTransforms transforms;

        Icon(String id){this(id,null);}
        Icon(String id,com.vandorlabs.canopy.LargeCanopySettings settings) {
            kind=id;List<CanopyMesh.Face> faces=settings!=null && com.vandorlabs.canopy.LargeCanopySettings.supports(id)?com.vandorlabs.canopy.LargeCanopyGeometry.faces(id.equals("large_front_canopy_glass")?5:3,settings,true):id.equals("long_canopy")?com.vandorlabs.canopy.OpeningCanopyGeometry.faces(1,3,1,false):CanopyMesh.MODELS.get(id);
            if(settings!=null && (id.equals("canopy") || id.equals("long_canopy")))faces=com.vandorlabs.canopy.OpeningCanopyGeometry.faces(1,id.equals("long_canopy")?3:2,Math.min(id.equals("long_canopy")?3:2,settings.angle),false);
            if(settings!=null)faces=com.vandorlabs.canopy.CanopyFinishes.faces(faces,settings.shade,settings.tint,settings.material,settings.textureLayout(),com.vandorlabs.VandorLabs.proxy.materialAspect(settings.material));
            faces=com.vandorlabs.canopy.CanopyMaterialUv.drawable(com.vandorlabs.canopy.CanopySurfaces.visible(faces));
            for (CanopyMesh.Face f : faces)
                for (int triangle = 1; triangle < f.vertices.length - 1; triangle++) {
                    float opacity=settings==null?1F:com.vandorlabs.canopy.CanopyFinishes.opacity(f,settings.material,settings.tint);
                    add(f, new int[] {0, triangle, triangle + 1, triangle + 1},opacity);
                    if (f.doubleSided) add(f, new int[] {triangle + 1, triangle, 0, 0},opacity);
                }
            particle =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite(spriteName(faces.get(0).material));
            ItemTransformVec3f gui =
                    new ItemTransformVec3f(
                            new Vector3f(25, 225, 0), new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
            ItemCameraTransforms source =
                    new ItemCameraTransforms(
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT,
                            gui,
                            ItemTransformVec3f.DEFAULT,
                            ItemTransformVec3f.DEFAULT);
            transforms = ControlItemPadding.fit(this, source);
        }

        private static String spriteName(String material){return material.contains(":")?material:"vandorlabs:blocks/canopy/"+material;}
        private void add(CanopyMesh.Face f, int[] index,float opacity) {
            int[] data = new int[28];
            TextureAtlasSprite sprite =
                    Minecraft.getMinecraft()
                            .getTextureMapBlocks()
                            .getAtlasSprite(spriteName(f.material));
            Vec3d normal =
                    f.vertices[index[1]]
                            .subtract(f.vertices[index[0]])
                            .crossProduct(f.vertices[index[2]].subtract(f.vertices[index[0]]))
                            .normalize();
            for (int i = 0; i < 4; i++) {
                int v = index[i], offset = i * 7;
                Vec3d p = f.vertices[v];
                data[offset] = Float.floatToRawIntBits((float) p.x);
                data[offset + 1] = Float.floatToRawIntBits((float) p.y);
                data[offset + 2] = Float.floatToRawIntBits((float) p.z);
                data[offset + 3] = (Math.round(opacity*255)<<24)|0xffffff;
                data[offset + 4] =
                        Float.floatToRawIntBits(sprite.getInterpolatedU(f.uv[v][0] * 16));
                data[offset + 5] =
                        Float.floatToRawIntBits(sprite.getInterpolatedV((1 - f.uv[v][1]) * 16));
                data[offset + 6] =
                        ((int) (normal.x * 127) & 255)
                                | (((int) (normal.y * 127) & 255) << 8)
                                | (((int) (normal.z * 127) & 255) << 16);
            }
            quads.add(
                    new BakedQuad(
                            data,
                            -1,
                            EnumFacing.getFacingFromVector(
                                    (float) normal.x, (float) normal.y, (float) normal.z),
                            sprite,
                            true,
                            DefaultVertexFormats.ITEM));
        }

        public List<BakedQuad> getQuads(IBlockState s, EnumFacing side, long seed) {
            return side == null ? quads : Collections.emptyList();
        }

        public boolean isAmbientOcclusion() {
            return false;
        }

        public boolean isGui3d() {
            return true;
        }

        public boolean isBuiltInRenderer() {
            return false;
        }

        public TextureAtlasSprite getParticleTexture() {
            return particle;
        }

        public ItemCameraTransforms getItemCameraTransforms() {
            return transforms;
        }

        public ItemOverrideList getOverrides() {
            return new ItemOverrideList(Collections.emptyList()){@Override public IBakedModel handleItemState(IBakedModel original,net.minecraft.item.ItemStack stack,net.minecraft.world.World world,net.minecraft.entity.EntityLivingBase entity){com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();if(stack.hasTagCompound())settings.read(stack.getTagCompound());String key=kind+":"+settings.key();IBakedModel model=CONFIGURED.get(key);if(model==null){model=new Icon(kind,settings);CONFIGURED.put(key,model);}return model;}};
        }
    }
}
