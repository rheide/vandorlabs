package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockProgrammableSlab;
import com.vandorlabs.blocks.ProgrammableHousingState;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reuses material/facing quads from a chunk mesh until a model reload. */
public final class ProgrammableHousingModel implements IBakedModel {
    private static final EnumFacing[] FACE = {EnumFacing.UP, EnumFacing.DOWN,
            EnumFacing.WEST, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH};
    private final IBakedModel delegate;
    private final boolean slab;
    private final boolean storage;
    private final Map<Long, BakedQuad> variants = new ConcurrentHashMap<>();

    public ProgrammableHousingModel(IBakedModel delegate, boolean slab) {
        this(delegate, slab, false);
    }

    public ProgrammableHousingModel(IBakedModel delegate, boolean slab, boolean storage) {
        this.storage = storage;
        this.delegate = delegate;
        this.slab = slab;
    }

    @Override public List<BakedQuad> getQuads(@Nullable IBlockState state,
            @Nullable EnumFacing side, long rand) {
        if (state == null) return delegate.getQuads(null, side, rand);
        if (side != null) return Collections.emptyList();
        int finish = 0, sideFinish=-1, visible = 63, tileSides = 0;
        com.vandorlabs.tiles.FaceTextures faces = com.vandorlabs.tiles.FaceTextures.DEFAULT;
        if (state instanceof IExtendedBlockState) {
            IExtendedBlockState extended = (IExtendedBlockState)state;
            if (extended.getValue(ProgrammableHousingState.FACES) != null)
                faces = extended.getValue(ProgrammableHousingState.FACES);
            Integer sf=extended.getValue(ProgrammableHousingState.SIDE_FINISH);
            if(sf!=null)sideFinish=sf;
            Integer f = extended.getValue(ProgrammableHousingState.FINISH);
            Integer v = extended.getValue(ProgrammableHousingState.VISIBLE);
            Integer t = extended.getValue(ProgrammableHousingState.TILE_SIDES);
            if (f != null) finish = ScreenHousingTextures.clamp(f);
            if (v != null) visible = v;
            if (t != null) tileSides = t;
        }
        EnumFacing facing = state.getValue(BlockAnimatedScreenSelector.FACING);
        boolean upper = slab && state.getValue(BlockProgrammableSlab.HALF)
                == BlockSlab.EnumBlockHalf.TOP;
        List<BakedQuad> result = new ArrayList<>(6);
        for (int local = 0; local < 6; local++) {
            EnumFacing worldFace = worldFace(FACE[local], facing);
            if ((visible & (1 << worldFace.getIndex())) == 0) continue;
            int texture = faces.texture(FACE[local].getIndex(), slab && FACE[local].getAxis()!=EnumFacing.Axis.Y && sideFinish>=0?sideFinish:finish);
            final boolean storageSet = storage && !(faces.enabled && faces.choice(FACE[local].getIndex()) >= 0);
            long key = (((((long)texture * 6 + facing.getIndex()) * 2 + (upper ? 1 : 0)) * 2
                    + (tileSides != 0 ? 1 : 0)) * 6 + local) * 2 + (storageSet ? 1 : 0);
            final int faceIndex = local;
            final boolean tiled = tileSides != 0;
            result.add(variants.computeIfAbsent(key,
                    ignored -> build(texture, facing, upper, tiled, faceIndex, storageSet)));
        }
        return result;
    }

    private BakedQuad build(int finish, EnumFacing facing, boolean upper, boolean tileSides, int i, boolean storageSet) {
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(storageSet ? ScreenHousingTextures.storageTexture(finish, FACE[i]) : ScreenHousingTextures.texture(finish));
        ScreenHousingMesh mesh = slab ? ScreenHousingMesh.slab(upper, tileSides)
                : ScreenHousingMesh.cube();
        int rotation = ((int)(180 - facing.getHorizontalAngle()) / 90) & 3;

            int[] data = new int[28];
            ScreenHousingMesh.Vertex[] points = mesh.quads[i].vertices;
            for (int j = 0; j < 4; j++) {
                ScreenHousingMesh.Vertex v = points[(4 - j) & 3];
                double a = v.x / 16, b = v.y / 16, c = v.z / 16;
                double x = a, z = c;
                switch (rotation) {
                    case 1: x = c; z = 1 - a; break;
                    case 2: x = 1 - a; z = 1 - c; break;
                    case 3: x = 1 - c; z = a; break;
                    default: break;
                }
                int offset = j * 7;
                data[offset] = Float.floatToRawIntBits((float)x);
                data[offset + 1] = Float.floatToRawIntBits((float)b);
                data[offset + 2] = Float.floatToRawIntBits((float)z);
                data[offset + 3] = -1;
                data[offset + 4] = Float.floatToRawIntBits(sprite.getInterpolatedU(v.u));
                data[offset + 5] = Float.floatToRawIntBits(sprite.getInterpolatedV(v.v));
                data[offset + 6] = 0;
            }
            // Flat block lighting samples the declared face. Use a perpendicular
            // direction to sample the housing position, preserving the old
            // maximum-neighbor light rule for every face.
            EnumFacing sampleFace = FACE[i].getAxis() == EnumFacing.Axis.Y
                    ? EnumFacing.NORTH : EnumFacing.UP;
            return new BakedQuad(data, -1, sampleFace,
                    sprite, false, DefaultVertexFormats.BLOCK);
    }

    private static EnumFacing worldFace(EnumFacing face, EnumFacing facing) {
        int rotation = ((int)(180 - facing.getHorizontalAngle()) / 90) & 3;
        if (face.getAxis() == EnumFacing.Axis.Y) return face;
        switch (rotation) {
            case 1: return face.rotateYCCW();
            case 2: return face.getOpposite();
            case 3: return face.rotateY();
            default: return face;
        }
    }

    @Override public boolean isAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return delegate.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return delegate.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() {
        return delegate.getItemCameraTransforms();
    }
    @Override public ItemOverrideList getOverrides() { return delegate.getOverrides(); }
}
