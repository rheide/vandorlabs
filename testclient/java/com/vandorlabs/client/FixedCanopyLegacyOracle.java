package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Actual placed regular-canopy chunk model, independently of the moving-shell TESR emitter. */
public final class FixedCanopyLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static TileEntityCanopy install(CanopyPlacementLegacyOracle.World world,BlockCanopy block,BlockPos pos,EnumFacing facing,int mount,BlockPos anchor){world.setBlockState(pos,block.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);TileEntityCanopy tile=(TileEntityCanopy)world.getTileEntity(pos);tile.anchor=anchor;tile.large.mount=mount;return tile;}
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();BlockCanopy fixed=null,opening=null;
        for(BlockCanopy block:blocks){if(block.kind.equals("regular_canopy_glass"))fixed=block;if(block.kind.equals("canopy"))opening=block;}
        if(!(fixed instanceof BlockFixedCanopyGlass)||opening==null)throw new AssertionError("Actual Source placed canopy families");
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)f.get(null);Minecraft mc=(Minecraft)unsafe.allocateInstance(Minecraft.class);
        for(Field field:Minecraft.class.getDeclaredFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==Minecraft.class){field.setAccessible(true);field.set(null,mc);}
        TextureAtlasSprite sprite=new TextureAtlasSprite("canopy-source-reference"){};sprite.setIconWidth(64);sprite.setIconHeight(64);sprite.initSprite(256,256,64,128,false);
        TextureMap atlas=new TextureMap("textures"){@Override public TextureAtlasSprite getAtlasSprite(String id){return sprite;}};
        for(Field field:Minecraft.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==TextureMap.class){field.setAccessible(true);field.set(mc,atlas);}
        File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;long quads=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8");PrintWriter inputs=args.length>1?new PrintWriter(args[1],"UTF-8"):null) {
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mask=0;mask<16;mask++)for(int roof=0;roof<16;roof++)if((roof&~mask)==0)for(int profile=0;profile<6;profile++) {
                final double aspect=profile==4?2:profile==5?.5:1;com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public double materialAspect(int ignored){return aspect;}};
                CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();TileEntityCanopy tile=install(world,fixed,ROOT,facing,profile%4,ROOT);NBTTagCompound input=CanopyMeshLegacyOracle.input(profile);tile.large.read(input);
                for(int bit=1;bit<=8;bit<<=1)if((mask&bit)!=0){EnumFacing dir=bit==1?facing.rotateYCCW():bit==2?facing.rotateY():bit==4?facing:facing.getOpposite();if((roof&bit)!=0)install(world,fixed,ROOT.offset(dir),facing,tile.large.mount,ROOT.offset(dir));else{BlockPos anchor=ROOT.offset(dir,2);install(world,opening,anchor,dir,tile.large.mount,anchor);install(world,opening,ROOT.offset(dir),dir,tile.large.mount,anchor);}}
                if(tile.mask()!=mask||tile.roofMask()!=roof)throw new AssertionError("Source actual fixed contact masks "+mask+"/"+roof+" actual="+tile.mask()+"/"+tile.roofMask());
                if(!tile.chunkRendered())throw new AssertionError("Source placed fixed canopy must use chunk model");
                if(facing==EnumFacing.SOUTH)CanopyMeshInputsLegacy.write(inputs,fixed.kind+":"+mask+":"+roof+":"+profile,tile,sprite);
                FixedCanopyModel.Mesh mesh=new FixedCanopyModel.Mesh(tile);JsonObject row=new JsonObject();row.addProperty("facing",facing.getIndex());row.addProperty("mask",mask);row.addProperty("roof",roof);row.addProperty("profile",profile);row.addProperty("aspect",aspect);row.add("settings",CanopySettingsLegacyOracle.tags(input));JsonArray layers=new JsonArray();
                for(List<BakedQuad> layer:Arrays.asList(mesh.solid,mesh.glass)){JsonArray records=new JsonArray();for(BakedQuad quad:layer){JsonObject record=new JsonObject();record.addProperty("face",quad.getFace().getIndex());record.addProperty("tint",quad.getTintIndex());record.addProperty("diffuse",quad.shouldApplyDiffuseLighting());JsonArray words=new JsonArray();for(int word:quad.getVertexData())words.add(word);record.add("words",words);records.add(record);quads++;}layers.add(records);}row.add("layers",layers);out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"f95a29d28420daef7033e757bd0102fe157ab73994650bc3a4d9df80ae280904");
        if(args.length>1)SegmentedReferenceGolden.check(new File(args[1]),"81aac455d493f8ac1cf04b70ebb969ad319474e7c22585c00046b2c362075341");
        System.out.println("Actual placed fixed-canopy chunk model PASS profiles="+profiles+" quads="+quads+" file="+file);
    }
}
