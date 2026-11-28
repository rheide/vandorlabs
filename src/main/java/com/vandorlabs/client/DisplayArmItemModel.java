package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.persistence.SaveSchema;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/** Configured arm icons retain the placed shape, artwork and a padded GUI silhouette. */
public final class DisplayArmItemModel implements IBakedModel {
    private static final int FULL_BRIGHT = 0x00f000f0;
    private final IBakedModel base;
    private final Function<String, TextureAtlasSprite> sprites;
    private final BoundedRenderCache<Key, ItemAppearanceMesh> variants = new BoundedRenderCache<>(32, 4L * 1024 * 1024);
    private long generation = -1;
    private final ItemOverrideList overrides = new ItemOverrideList(Collections.emptyList()) {
        @Override public IBakedModel handleItemState(IBakedModel original, ItemStack stack,
                World world, EntityLivingBase entity) {
            return configured(stack.getSubCompound("BlockEntityTag"));
        }
    };

    public DisplayArmItemModel(IBakedModel base) {
        this(base, name -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name));
    }

    /** Injectable atlas lookup also permits padding/UV checks without starting a renderer. */
    DisplayArmItemModel(IBakedModel base, Function<String, TextureAtlasSprite> sprites) {
        this.base = base;
        this.sprites = sprites;
    }

    synchronized IBakedModel configured(NBTTagCompound tag) {
        long current = ItemVariantModels.currentGeneration();
        if (generation != current) { variants.clear(); generation = current; }
        DisplayArmSettings settings = TileEntityDisplayArm.readSettings(tag);
        int mount = EnumFacing.UP.getIndex(), facing = EnumFacing.NORTH.getIndex();
        if (tag != null) {
            NBTTagCompound arm = tag.getCompoundTag(TileEntityDisplayArm.SETTINGS);
            if (arm.hasKey("Mount", 99)) {
                int stored = arm.getInteger("Mount");
                if (stored >= 0 && stored < 6) mount = stored;
            }
            if (tag.hasKey(TileEntityDisplayArm.ITEM_FACING, 99)) {
                int stored = tag.getInteger(TileEntityDisplayArm.ITEM_FACING);
                if (stored >= 2 && stored < 6) facing = stored;
            }
        }
        int housingChoice = tag != null && tag.hasKey(SaveSchema.Screen.HOUSING_TEXTURE, 3)
                ? ScreenHousingTextures.clamp(tag.getInteger(SaveSchema.Screen.HOUSING_TEXTURE))
                : ScreenHousingTextures.screenIndex("door_inner");
        TextureAtlasSprite housing = sprites.apply(ScreenHousingTextures.texture(housingChoice));
        TextureAtlasSprite white = sprites.apply("vandorlabs:blocks/screen_white");
        TextureAtlasSprite screen = artwork(tag, false);
        TextureAtlasSprite input = settings.input ? artwork(tag, true) : null;
        Key key = new Key(settings, mount, facing, housing, white, screen, input);
        ItemAppearanceMesh cached = variants.get(key);
        if (cached != null) return cached;
        DisplayArmGeometry geometry = DisplayArmGeometry.create(mount, facing, settings);
        ItemAppearanceMesh.Capture capture = new ItemAppearanceMesh.Capture(
                1D / 16, 1D / 16, 1D / 16, 0, 0, housing, white, screen, input);
        DisplayArmMesh.draw(capture, geometry, housing, white, FULL_BRIGHT);
        DisplayArmMesh.surface(capture, geometry, 0, screen, FULL_BRIGHT);
        if (settings.input) DisplayArmMesh.surface(capture, geometry, 1, input, FULL_BRIGHT);
        ItemAppearanceMesh model = new ItemAppearanceMesh(base, capture.finish(), housing, true);
        variants.put(key, model, model.bytes);
        return model;
    }

    private TextureAtlasSprite artwork(NBTTagCompound tag, boolean input) {
        String surface = input ? "SecondarySurfaceTexture" : "PrimarySurfaceTexture";
        int choice;
        if (tag != null && tag.hasKey(surface, 3) && tag.getInteger(surface) >= 0) {
            choice = ScreenHousingTextures.clamp(tag.getInteger(surface));
        } else if (input) {
            String id = tag == null ? "" : tag.getString(SaveSchema.Screen.INPUT);
            if (!TileEntityAnimatedScreenSelector.isValidInputPanel(id))
                id = TileEntityAnimatedScreenSelector.INPUT_PANELS[0];
            choice = ScreenHousingTextures.screenIndex("console_inputs/"
                    + ScreenHousingTextures.canonicalInput(id) + "_static");
        } else {
            String id = tag == null ? "" : tag.getString(SaveSchema.Screen.SELECTED);
            if (!ModBlocks.DISPLAY_SCREEN_IDS.contains(id)) id = "engineering_screen";
            choice = ScreenHousingTextures.screenIndex(ScreenHousingTextures.canonicalScreen(id) + "_static");
        }
        return sprites.apply(ScreenHousingTextures.fullTexture(choice));
    }

    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) {
        return configured(null).getQuads(state, side, seed);
    }
    @Override public boolean isAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return true; }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return configured(null).getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return configured(null).getItemCameraTransforms(); }
    @Override public ItemOverrideList getOverrides() { return overrides; }

    private static final class Key {
        final DisplayArmSettings settings;
        final int mount, facing;
        final TextureAtlasSprite housing, white, screen, input;
        Key(DisplayArmSettings settings, int mount, int facing, TextureAtlasSprite housing,
                TextureAtlasSprite white, TextureAtlasSprite screen, TextureAtlasSprite input) {
            this.settings = settings; this.mount = mount; this.facing = facing;
            this.housing = housing; this.white = white; this.screen = screen; this.input = input;
        }
        @Override public int hashCode() {
            int result = 31 * (31 * settings.hashCode() + mount) + facing;
            for (TextureAtlasSprite sprite : new TextureAtlasSprite[]{housing, white, screen, input})
                result = 31 * result + System.identityHashCode(sprite);
            return result;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key value = (Key) other;
            return mount == value.mount && facing == value.facing && settings.equals(value.settings)
                    && housing == value.housing && white == value.white
                    && screen == value.screen && input == value.input;
        }
    }
}
