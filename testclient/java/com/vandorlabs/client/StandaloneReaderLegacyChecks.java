package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.util.*;

/** Actual released reader block geometry, including the intentionally noncolliding flat pad. */
public final class StandaloneReaderLegacyChecks {
    private static JsonArray box(AxisAlignedBB b) {
        if(b==null)return null;
        JsonArray a=new JsonArray();for(double v:new double[]{b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ})a.add(v);return a;
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.init.Bootstrap.register();
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        BlockPos pos=new BlockPos(0,80,0);JsonArray rows=new JsonArray(),metadata=new JsonArray();
        for(boolean chip:new boolean[]{false,true}) {
            BlockTokenSlot block=new BlockTokenSlot(chip?"data_chip_slot":"access_card_slot",chip);
            for(int meta=0;meta<16;meta++) {
                IBlockState state=block.getStateFromMeta(meta);JsonObject row=new JsonObject();
                row.addProperty("chip",chip);row.addProperty("input",meta);row.addProperty("facing",state.getValue(BlockVandorSwitch.FACING).getIndex());row.addProperty("on",state.getValue(BlockVandorSwitch.ON));row.addProperty("encoded",block.getMetaFromState(state));metadata.add(row);
            }
            for(EnumFacing face:EnumFacing.values())for(boolean on:new boolean[]{false,true})for(int rotation=0;rotation<4;rotation++)for(int mount=0;mount<64;mount++) {
                IBlockState state=block.getDefaultState().withProperty(BlockVandorSwitch.FACING,face).withProperty(BlockVandorSwitch.ON,on);
                world.setBlockState(pos,state,2);TileEntityTokenSlot tile=(TileEntityTokenSlot)world.getTileEntity(pos);
                tile.setMountRotation(rotation);tile.configureMount(mount%4,mount/4%4,mount/16);
                JsonObject row=new JsonObject();row.addProperty("chip",chip);row.addProperty("facing",face.getIndex());row.addProperty("on",on);row.addProperty("rotation",rotation);row.addProperty("mount",mount);
                row.add("outline",box(block.getBoundingBox(state,world,pos)));row.add("collision",box(block.getCollisionBoundingBox(state,world,pos)));
                List<AxisAlignedBB> boxes=new ArrayList<>();block.addCollisionBoxToList(state,world,pos,new AxisAlignedBB(-4,76,-4,4,84,4),boxes,null,false);
                JsonArray parts=new JsonArray();for(AxisAlignedBB b:boxes)parts.add(box(b.offset(-pos.getX(),-pos.getY(),-pos.getZ())));row.add("parts",parts);rows.add(row);
            }
        }
        JsonObject root=new JsonObject();root.addProperty("schema",1);root.add("metadata",metadata);root.add("geometry",rows);
        String serialized=new Gson().toJson(root);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));
        if(!hash.toString().equals("43da29d672c9413bd453a0ca3380bb2be2bfa591297848d99e45388cb630bfe2"))throw new AssertionError("Released reader block geometry changed: "+hash);
        root.addProperty("golden",hash.toString());Path output=Paths.get(System.getProperty("vandorlabs.standaloneReaderReference","build/test-artifacts/standalone-reader-forge.json"));Files.createDirectories(output.toAbsolutePath().getParent());try(java.io.Writer writer=Files.newBufferedWriter(output)){new Gson().toJson(root,writer);}
        System.out.println("Actual standalone reader geometry PASS cases="+rows.size()+" metadata="+metadata.size()+" golden="+hash);
    }
}
