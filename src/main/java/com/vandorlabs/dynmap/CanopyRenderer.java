package com.vandorlabs.dynmap;

import com.vandorlabs.canopy.CanopyMesh;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

import org.dynmap.renderer.*;

import java.util.*;

/** Closed overview meshes, clipped into each occupied voxel to avoid duplicated assemblies. */
public final class CanopyRenderer extends CustomRenderer {
    @Override protected int getMaximumTextureCount() { return 4 + 19 * 3; }
    @Override
    public String[] getTileEntityFieldsNeeded() {
        return new String[] {"Anchor", "Pair", "CanopyMask", "CanopyRoofMask", "CanopyWidth", "CenteredPlacement", "CanopyDepth", "CanopyScale", "CanopyShade", "CanopyTint", "CanopyMount", "CanopyAngle", "CanopyMotion"};
    }

    private static BlockPos position(Object value, BlockPos fallback) {
        return value instanceof Number ? BlockPos.fromLong(((Number) value).longValue()) : fallback;
    }

    @Override
    public RenderPatch[] getRenderPatchList(MapDataContext context) {
        BlockPos here = new BlockPos(context.getX(), context.getY(), context.getZ());
        BlockPos anchor = position(context.getBlockTileEntityField("Anchor"), here);
        int ax = anchor.getX() - here.getX(),
                ay = anchor.getY() - here.getY(),
                az = anchor.getZ() - here.getZ();
        Object pair = context.getBlockTileEntityFieldAt("Pair", ax, ay, az);
        BlockPos owner = position(pair, anchor);
        int ox = owner.getX() - here.getX(),
                oy = owner.getY() - here.getY(),
                oz = owner.getZ() - here.getZ();
        DynmapBlockState state = context.getBlockType();
        String id = state.blockName.substring(state.blockName.indexOf(':') + 1);
        EnumFacing facing =
                state.isStateMatch("facing", "east")
                        ? EnumFacing.EAST
                        : state.isStateMatch("facing", "south")
                                ? EnumFacing.SOUTH
                                : state.isStateMatch("facing", "west")
                                        ? EnumFacing.WEST
                                        : EnumFacing.NORTH;
        Object mask = context.getBlockTileEntityFieldAt("CanopyMask", ox, oy, oz);
        int bits = mask instanceof Number ? ((Number) mask).intValue() : 0;
        if (id.equals("regular_canopy_glass"))
            id = String.format(java.util.Locale.ROOT, "regular_1x1_%02x", bits & 15);
        else if (id.startsWith("angled_") && (bits & 11) != 0)
            id = String.format(java.util.Locale.ROOT, "%s_%02x", id, bits & 11);
        Object savedWidth=context.getBlockTileEntityFieldAt("CanopyWidth",ox,oy,oz);
        int width=savedWidth instanceof Number?((Number)savedWidth).intValue():pair instanceof Number?2:1;
        List<RenderPatch> result = new ArrayList<>();
        List<CanopyMesh.Face> mesh = CanopyMesh.MODELS.get(id);
        if(id.startsWith("regular_1x1_")){
            Object roof=context.getBlockTileEntityFieldAt("CanopyRoofMask",ox,oy,oz);
            mesh=com.vandorlabs.canopy.CanopyVariants.fixedGlassFaces(bits,roof instanceof Number?((Number)roof).intValue():bits);
        }
        Object alignment=context.getBlockTileEntityFieldAt("CenteredPlacement",ox,oy,oz);
        mesh=com.vandorlabs.canopy.CanopyVariants.faces(Boolean.TRUE.equals(alignment) || alignment instanceof Number && ((Number)alignment).intValue()!=0,id,mesh);
        net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();
        for(String field:new String[]{"CanopyDepth","CanopyScale","CanopyShade","CanopyTint","CanopyMount","CanopyAngle","CanopyMotion"}){Object value=context.getBlockTileEntityFieldAt(field,ox,oy,oz);if(value instanceof Number)tag.setInteger(field,((Number)value).intValue());}
        com.vandorlabs.canopy.LargeCanopySettings settings=new com.vandorlabs.canopy.LargeCanopySettings();settings.read(tag);
        if(com.vandorlabs.canopy.LargeCanopySettings.supports(id))mesh=com.vandorlabs.canopy.LargeCanopyGeometry.faces(id.equals("large_front_canopy_glass")?5:3,settings,Boolean.TRUE.equals(alignment) || alignment instanceof Number && ((Number)alignment).intValue()!=0);
        if(id.equals("canopy") || id.equals("long_canopy"))mesh=com.vandorlabs.canopy.OpeningCanopyGeometry.faces(width,id.equals("long_canopy")?3:2,Math.min(id.equals("long_canopy")?3:2,settings.angle),(bits&8)!=0);
        mesh=com.vandorlabs.canopy.CanopyPose.faces(mesh,id,width,settings,0);
        if (mesh == null) return new RenderPatch[0];
        Object savedShade=context.getBlockTileEntityFieldAt("CanopyShade",ox,oy,oz),savedTint=context.getBlockTileEntityFieldAt("CanopyTint",ox,oy,oz);
        int shade=savedShade instanceof Number?com.vandorlabs.tiles.GlassTextures.clamp(((Number)savedShade).intValue()):0;
        int tint=savedTint instanceof Number?com.vandorlabs.tiles.GlassTextures.clampTint(((Number)savedTint).intValue()):1;
        for (CanopyMesh.Face face : mesh) {
            int texture =
                    face.baseMaterial.equals("glass")
                            ? 4+shade*3+tint
                            : face.baseMaterial.equals("amber")
                                    ? 2
                                    : face.baseMaterial.equals("light") ? 3 : 0;
            List<MapSurface.Vertex> polygon=new ArrayList<>();
            for(int i=0;i<face.vertices.length;i++)polygon.add(new MapSurface.Vertex(
                    CanopyMesh.rotate(face.vertices[i],facing).addVector(ox,oy,oz),face.uv[i][0],1-face.uv[i][1]));
            MapSurface.emit(context,result,polygon,texture);
        }
        return result.toArray(new RenderPatch[0]);
    }

}
