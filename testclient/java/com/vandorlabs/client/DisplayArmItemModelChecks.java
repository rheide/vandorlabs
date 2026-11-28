package com.vandorlabs.client;

import com.vandorlabs.display.DisplayArmGeometry;
import com.vandorlabs.display.DisplayArmGeometry.Vec;
import com.vandorlabs.display.DisplayArmSettings;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityDisplayArm;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.util.vector.Vector3f;

/** Actual arm mesh, configured item silhouettes and atlas UVs without a GL context. */
public final class DisplayArmItemModelChecks {
    private static int checks;
    private static void need(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError("Display arm item: " + message);
    }
    private static void near(double actual, double expected, String message) {
        need(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-5, message);
    }

    private static final class Atlas implements Function<String, TextureAtlasSprite> {
        final Map<String, TextureAtlasSprite> sprites = new HashMap<>();
        @Override public TextureAtlasSprite apply(String name) {
            return sprites.computeIfAbsent(name, key -> {
                TextureAtlasSprite sprite = new TextureAtlasSprite(key) { };
                sprite.setIconWidth(16); sprite.setIconHeight(16);
                int cell = sprites.size();
                sprite.initSprite(512, 512, cell % 32 * 16, cell / 32 * 16, false);
                return sprite;
            });
        }
    }

    private static IBakedModel base(Atlas atlas) {
        Map<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.values()) faces.put(side, Collections.emptyList());
        ItemTransformVec3f gui = new ItemTransformVec3f(new Vector3f(30, 225, 0),
                new Vector3f(0, 0, 0), new Vector3f(.625F, .625F, .625F));
        ItemCameraTransforms transforms = new ItemCameraTransforms(ItemTransformVec3f.DEFAULT,
                ItemTransformVec3f.DEFAULT, ItemTransformVec3f.DEFAULT, ItemTransformVec3f.DEFAULT,
                ItemTransformVec3f.DEFAULT, gui, ItemTransformVec3f.DEFAULT, ItemTransformVec3f.DEFAULT);
        return new SimpleBakedModel(Collections.emptyList(), faces, false, true,
                atlas.apply("default-particle"), transforms, ItemOverrideList.NONE);
    }

    private static NBTTagCompound tag(DisplayArmSettings settings, int mount, int facing) {
        NBTTagCompound root = new NBTTagCompound(), arm = new NBTTagCompound();
        arm.setBoolean("Half", settings.half); arm.setBoolean("Input", settings.input);
        arm.setInteger("Right", settings.right); arm.setInteger("Height", settings.height);
        arm.setInteger("Forward", settings.forward); arm.setInteger("Yaw", settings.yaw);
        arm.setInteger("Pitch", settings.pitch); arm.setInteger("Roll", settings.roll);
        arm.setByte("Mount", (byte) mount);
        root.setTag(TileEntityDisplayArm.SETTINGS, arm);
        root.setInteger(TileEntityDisplayArm.ITEM_FACING, facing);
        return root;
    }

    private static Vec point(int[] data, int stride, int vertex) {
        return new Vec(Float.intBitsToFloat(data[vertex * stride]),
                Float.intBitsToFloat(data[vertex * stride + 1]),
                Float.intBitsToFloat(data[vertex * stride + 2]));
    }

    private static boolean inside(DisplayArmGeometry.Bounds bounds, Vec point) {
        // Emission quantizes portable doubles to floats; permit that precision loss only.
        double epsilon = 1e-4;
        return point.x >= bounds.minX - epsilon && point.x <= bounds.maxX + epsilon
                && point.y >= bounds.minY - epsilon && point.y <= bounds.maxY + epsilon
                && point.z >= bounds.minZ - epsilon && point.z <= bounds.maxZ + epsilon;
    }

    private static void verify(IBakedModel model, DisplayArmGeometry geometry) {
        List<BakedQuad> quads = model.getQuads(null, null, 0);
        need(quads.size() == geometry.boxes.size() * 6 + (geometry.settings.input ? 2 : 1),
                "same component faces and enabled artwork as world geometry");
        for (EnumFacing face : EnumFacing.values()) need(model.getQuads(null, face, 0).isEmpty(),
                "all arbitrary-angle faces are uncullable item quads");
        ItemTransformVec3f gui = model.getItemCameraTransforms().gui;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertexData(); int stride = data.length / 4;
            int uv = quad.getFormat().getUvOffsetById(0) / 4;
            Vec a = point(data, stride, 0), b = point(data, stride, 1), c = point(data, stride, 2);
            Vec normal = b.subtract(a).cross(c.subtract(b)).unit();
            int n = data[quad.getFormat().getNormalOffset() / 4];
            Vec recorded = new Vec((byte) n / 127D, (byte) (n >> 8) / 127D, (byte) (n >> 16) / 127D);
            need(normal.dot(recorded) > .98, "normal matches outward vertex winding");
            for (int vertex = 0; vertex < 4; vertex++) {
                Vec position = point(data, stride, vertex);
                need(inside(geometry.bounds, position.scale(16)), "item retains posed world bounds");
                float u = Float.intBitsToFloat(data[vertex * stride + uv]);
                float v = Float.intBitsToFloat(data[vertex * stride + uv + 1]);
                TextureAtlasSprite sprite = quad.getSprite();
                need(u >= sprite.getMinU() - 1e-6 && u <= sprite.getMaxU() + 1e-6
                        && v >= sprite.getMinV() - 1e-6 && v <= sprite.getMaxV() + 1e-6,
                        "item UV remains inside its selected atlas sprite");
                Vec3d projected = ControlItemPadding.project(new Vec3d(position.x, position.y, position.z), gui);
                double x = projected.x + gui.translation.x, y = projected.y + gui.translation.y;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                need(Math.abs(x) <= .37501 && Math.abs(y) <= .37501,
                        "configured icon has padding on all four slot edges");
            }
        }
        near(minX + maxX, 0, "configured icon horizontally centered");
        near(minY + maxY, 0, "configured icon vertically centered");
        int artworkStart = quads.size() - (geometry.settings.input ? 2 : 1);
        for (int slot = 0; slot < (geometry.settings.input ? 2 : 1); slot++) {
            BakedQuad artwork = quads.get(artworkStart + slot);
            int[] data = artwork.getVertexData(); int stride = data.length / 4;
            int uv = artwork.getFormat().getUvOffsetById(0) / 4;
            double first = artwork.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[uv]));
            double second = artwork.getSprite().getUnInterpolatedU(Float.intBitsToFloat(data[stride + uv]));
            need(slot == 0 ? first > second : first < second,
                    "screen/input match their established authored U direction");
        }
    }

    private static void worldMesh(Atlas atlas) {
        TextureAtlasSprite housing = atlas.apply("housing"), white = atlas.apply("white");
        DisplayArmGeometry geometry = DisplayArmGeometry.create(5, 4,
                new DisplayArmSettings(true, true, 7, -5, 9, 35, -40, 55));
        VertexFormat format = BlockSurfaceFormat.get();
        for (int pass = 0; pass < 2; pass++) {
            BufferBuilder buffer = new BufferBuilder(65536);
            buffer.begin(7, format);
            DisplayArmMesh.draw(buffer, geometry, housing, white, 0x00a00050);
            buffer.finishDrawing();
            need(buffer.getVertexCount() == geometry.boxes.size() * 24,
                    "warm/cold world draw emits complete component geometry");
            ByteBuffer bytes = buffer.getByteBuffer();
            int stride = format.getNextOffset(), light = format.getUvOffsetById(1);
            for (int vertex = 0; vertex < buffer.getVertexCount(); vertex++) {
                int at = vertex * stride;
                need(bytes.getShort(at + light) == 80 && bytes.getShort(at + light + 2) == 160,
                        "standard packed block/sky light order");
                need(inside(geometry.bounds, new Vec(bytes.getFloat(at), bytes.getFloat(at + 4),
                        bytes.getFloat(at + 8))), "world mesh follows portable pixel coordinates");
            }
        }
    }

    public static void main(String[] ignored) {
        net.minecraft.init.Bootstrap.register();
        Atlas atlas = new Atlas();
        DisplayArmItemModel model = new DisplayArmItemModel(base(atlas), atlas);
        int cases = 0;
        for (boolean half : new boolean[]{false, true}) for (boolean input : new boolean[]{false, true})
            for (int mount = 0; mount < 6; mount++) for (int facing = 2; facing < 6; facing++)
                for (int preset = 0; preset < 3; preset++) {
                    DisplayArmSettings settings = new DisplayArmSettings(half, input,
                            preset == 2 ? -32 : 0, preset == 2 ? 32 : 0, preset == 2 ? -32 : 4,
                            preset == 0 ? 0 : 135, preset == 0 ? 0 : -90, preset == 0 ? 0 : 47);
                    NBTTagCompound tag = tag(settings, mount, facing);
                    IBakedModel prepared = model.configured(tag);
                    verify(prepared, DisplayArmGeometry.create(mount, facing, settings));
                    need(model.configured(tag) == prepared, "warm configured icon retained");
                    tag.setString("Unrelated", "does not change appearance");
                    need(model.configured(tag) == prepared, "unrelated NBT excluded from cache key");
                    cases++;
                }
        NBTTagCompound material = tag(new DisplayArmSettings(false, true, 0, 0, 4, 0, 0, 0), 1, 2);
        int screen = ScreenHousingTextures.screenIndex("matter/matter");
        int input = ScreenHousingTextures.screenIndex("door_inner");
        material.setInteger("PrimarySurfaceTexture", screen); material.setInteger("SecondarySurfaceTexture", input);
        List<BakedQuad> selected = model.configured(material).getQuads(null, null, 0);
        need(selected.get(selected.size() - 2).getSprite() == atlas.apply(ScreenHousingTextures.fullTexture(screen)),
                "selected screen artwork retained");
        need(selected.get(selected.size() - 1).getSprite() == atlas.apply(ScreenHousingTextures.fullTexture(input)),
                "selected input artwork retained");
        IBakedModel first = model.configured(tag(new DisplayArmSettings(false, false, -32, 0, 4, 0, 0, 0), 1, 2));
        for (int offset = -31; offset <= 32; offset++)
            model.configured(tag(new DisplayArmSettings(false, false, offset, 0, 4, 0, 0, 0), 1, 2));
        need(model.configured(tag(new DisplayArmSettings(false, false, -32, 0, 4, 0, 0, 0), 1, 2)) != first,
                "many edited poses evict old variants instead of growing forever");
        IBakedModel beforeReload = model.configured(null);
        ItemVariantModels.clear();
        need(model.configured(null) != beforeReload, "resource/format generation discards prepared variants");
        worldMesh(atlas);
        System.out.println("Display arm items PASS cases=" + cases + " checks=" + checks
                + " complete-mesh/normal/UV/size/pose/mount/padding/materials/cache/lightmap");
    }
}
