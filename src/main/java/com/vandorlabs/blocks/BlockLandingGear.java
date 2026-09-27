package com.vandorlabs.blocks;

import com.google.gson.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.*;
import java.util.*;

/** Gear collision uses the same cuboids as its rendered models. */
public class BlockLandingGear extends BlockVandorDirectional {
    protected final List<AxisAlignedBB> parts = new ArrayList<>();
    public BlockLandingGear(String name) {
        super(name);
        setLightOpacity(0);
        parts.addAll(loadParts("block/" + name + "_small_retracted"));
    }
    protected static List<AxisAlignedBB> loadParts(String model) {
        List<AxisAlignedBB> result = new ArrayList<>();
        try (Reader reader = new InputStreamReader(BlockLandingGear.class.getResourceAsStream(
                "/assets/vandorlabs/models/" + model + ".json"), java.nio.charset.StandardCharsets.UTF_8)) {
            for (JsonElement raw : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("elements")) {
                JsonObject e = raw.getAsJsonObject(); JsonArray a = e.getAsJsonArray("from"), b = e.getAsJsonArray("to");
                result.add(new AxisAlignedBB(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16,
                        b.get(0).getAsDouble()/16,b.get(1).getAsDouble()/16,b.get(2).getAsDouble()/16));
            }
        } catch (IOException e) { throw new IllegalStateException("Cannot load gear geometry " + model, e); }
        return result;
    }
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing facing = placer.getHorizontalFacing().getOpposite();
        if (getRegistryName().getResourcePath().contains("_side_") && side.getAxis().isHorizontal())
            facing = side.rotateYCCW(); // canonical attachment is west; exposed normal is east
        return getDefaultState().withProperty(FACING, facing);
    }
    public boolean isOpaqueCube(IBlockState state) { return false; }
    public boolean isFullCube(IBlockState state) { return false; }
    protected List<AxisAlignedBB> boxes(IBlockState state, IBlockAccess world, BlockPos pos) { return parts; }
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB box = null;
        for (AxisAlignedBB part : boxes(state,world,pos)) box = box == null ? part : box.union(part);
        return PanelPlacement.rotateFromNorth(box == null ? FULL_BLOCK_AABB : box, state.getValue(FACING));
    }
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
            AxisAlignedBB entityBox, List<AxisAlignedBB> boxes, Entity entity, boolean actual) {
        for (AxisAlignedBB part : boxes(state,world,pos))
            addCollisionBoxToList(pos,entityBox,boxes,PanelPlacement.rotateFromNorth(part,state.getValue(FACING)));
    }
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos, Vec3d start, Vec3d end) {
        RayTraceResult nearest = null;
        for (AxisAlignedBB part : boxes(state,world,pos)) {
            RayTraceResult hit = rayTrace(pos,start,end,PanelPlacement.rotateFromNorth(part,state.getValue(FACING)));
            if (hit != null && (nearest == null || start.squareDistanceTo(hit.hitVec) < start.squareDistanceTo(nearest.hitVec))) nearest=hit;
        }
        return nearest;
    }
}
