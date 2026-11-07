package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.PrintWriter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;

/** Independent pre-extraction base polygons let native geometry replay the frozen final emitters. */
final class CanopyMeshInputsLegacy {
    static void write(PrintWriter out,String key,TileEntityCanopy tile,TextureAtlasSprite sprite) {
        if(out==null)return;
        JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("id",tile.block().kind);row.addProperty("width",tile.width());row.addProperty("centered",tile.centeredPlacement);
        NBTTagCompound settings=new NBTTagCompound();tile.large.write(settings);row.add("settings",CanopySettingsLegacyOracle.tags(settings));
        JsonArray atlas=new JsonArray();for(float value:new float[]{sprite.getMinU(),sprite.getMaxU(),sprite.getMinV(),sprite.getMaxV()})atlas.add(Float.floatToRawIntBits(value));row.add("atlas",atlas);
        JsonArray faces=new JsonArray();for(CanopyMesh.Face face:tile.baseFaces()) {
            JsonObject record=new JsonObject();record.addProperty("material",face.material);record.addProperty("base",face.baseMaterial);record.addProperty("group",face.group);record.addProperty("double",face.doubleSided);record.addProperty("glazing",face.glazing);record.addProperty("tiled",face.tiled);
            JsonArray points=new JsonArray();for(int i=0;i<face.vertices.length;i++){Vec3d p=face.vertices[i];for(double value:new double[]{p.x,p.y,p.z,face.uv[i][0],face.uv[i][1]})points.add(Double.doubleToRawLongBits(value));}record.add("points",points);faces.add(record);
        }
        row.add("faces",faces);out.println(new Gson().toJson(row));
    }
}
