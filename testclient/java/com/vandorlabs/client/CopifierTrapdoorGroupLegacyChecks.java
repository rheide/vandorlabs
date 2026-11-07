package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;

/** Actual configured item formation followed by copied callbacks on one selected physical leaf. */
public final class CopifierTrapdoorGroupLegacyChecks {
    private static final BlockPos BASE=new BlockPos(32,100,32);
    private static JsonArray captures(NonRenderingChecks.MemoryWorld world,java.util.List<TileEntityProgrammableTrapdoor> leaves){JsonArray out=new JsonArray();for(TileEntityProgrammableTrapdoor leaf:leaves){JsonObject cell=new JsonObject();cell.addProperty("capture",ProgrammableSettings.capture(world,leaf.getPos()).toString());cell.addProperty("face",world.getBlockState(leaf.getPos()).getValue(BlockProgrammableTrapdoor.FACING).getIndex());cell.addProperty("top",world.getBlockState(leaf.getPos()).getValue(BlockProgrammableTrapdoor.HALF)==net.minecraft.block.BlockTrapDoor.DoorHalf.TOP);out.add(cell);}return out;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableTrapdoor.class,new ResourceLocation("minecraft:trapdoor_group_copy"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableDiagonalTrapdoor.class,new ResourceLocation("minecraft:diagonal_trapdoor_group_copy"));
        JsonArray rows=new JsonArray();int linked=0;
        for(boolean diagonal:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean sliding:new boolean[]{false,true})for(EnumFacing face:EnumFacing.HORIZONTALS)for(int variant=0;variant<6;variant++){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR:ModBlocks.PROGRAMMABLE_TRAPDOOR);ItemProgrammableTrapdoor item=new ItemProgrammableTrapdoor(block);
            EnumFacing across=diagonal?face.rotateYCCW():EnumFacing.EAST,along=diagonal?(mode==2?face.getOpposite():EnumFacing.UP):EnumFacing.SOUTH;
            BlockPos[] cells={BASE,BASE.offset(across),BASE.offset(along),BASE.offset(across).offset(along)};java.util.List<TileEntityProgrammableTrapdoor> leaves=new java.util.ArrayList<>();
            for(BlockPos cell:cells){TileEntityProgrammableTrapdoor isolated=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();isolated.configure(7,mode,sliding,1,ChannelList.of(13,17));ItemStack stack=new ItemStack(item);stack.setTagInfo("BlockEntityTag",isolated.itemSettings());if(!item.placeBlockAt(stack,null,world,cell,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState().withProperty(BlockProgrammableTrapdoor.FACING,face)))throw new AssertionError("Actual group placement refused");leaves.add((TileEntityProgrammableTrapdoor)world.getTileEntity(cell));}
            TileEntityProgrammableTrapdoor selected=leaves.get(variant%4);JsonArray members=new JsonArray();for(TileEntityProgrammableTrapdoor member:selected.group()){int index=leaves.indexOf(member);if(index<0)throw new AssertionError("Unexpected group member");members.add(index);}if(members.size()>1)linked++;
            JsonObject row=new JsonObject();row.addProperty("diagonal",diagonal);row.addProperty("selected",variant%4);row.add("members",members);JsonArray input=new JsonArray();for(TileEntityProgrammableTrapdoor leaf:leaves){JsonObject cell=new JsonObject();cell.addProperty("data",leaf.writeToNBT(new NBTTagCompound()).toString());cell.addProperty("face",world.getBlockState(leaf.getPos()).getValue(BlockProgrammableTrapdoor.FACING).getIndex());cell.addProperty("meta",block.getMetaFromState(world.getBlockState(leaf.getPos())));input.add(cell);}row.add("input",input);row.add("before",captures(world,leaves));
            NBTTagCompound values=new NBTTagCompound();
            if(variant==0){TileEntityProgrammableTrapdoor from=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();from.configure(9,1,!sliding,0,ChannelList.of(29,31));NonRenderingChecks.MemoryWorld source=new NonRenderingChecks.MemoryWorld(false);BlockPos pos=BASE.up(8);from.setWorld(source);from.setPos(pos);source.states.put(pos,block.getDefaultState());source.tiles.put(pos,from);values=ProgrammableSettings.capture(source,pos);from.onChunkUnload();}
            else if(variant==1){values.setBoolean("trapdoor_cover",true);values.setInteger("trapdoor_cover_facing",1);values.setInteger("trapdoor_texture_layout",2);}
            else if(variant==2){values.setInteger("wall_texture",-1);values.setIntArray("redstone_channel",new int[]{29,31});values.setBoolean("trapdoor_tile_texture",false);}
            else if(variant==3){values.setBoolean("door_sliding",false);values.setInteger("trapdoor_position",2);NBTTagCompound geometry=new NBTTagCompound();geometry.setInteger("mode",2);values.setTag("diagonal_geometry",geometry);values.setInteger("trapdoor_hinge_side",2);values.setBoolean("trapdoor_rotation_flipped",true);}
            else if(variant==4)values.setIntArray("redstone_channel",new int[]{29,31});
            else{values.setBoolean("door_sliding",true);values.setBoolean("trapdoor_slide_over_surface",true);values.setBoolean("trapdoor_slide_into_wall",true);values.setInteger("trapdoor_hinge_side",1);values.setBoolean("trapdoor_rotation_flipped",true);}
            row.addProperty("values",values.toString());NBTTagCompound undo=ProgrammableSettings.capture(world,selected.getPos());boolean applied=ProgrammableSettings.apply(world,selected.getPos(),values.copy());row.addProperty("applied",applied);row.add("after",captures(world,leaves));
            if(applied){if(!ProgrammableSettings.apply(world,selected.getPos(),undo))throw new AssertionError("Group callback undo refused");row.add("undo",captures(world,leaves));}
            for(TileEntityProgrammableTrapdoor leaf:leaves){if(world.getTileEntity(leaf.getPos())!=leaf)throw new AssertionError("Group leaf replaced");leaf.onChunkUnload();}rows.add(row);
        }
        byte[] raw=new Gson().toJson(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);Path out=Paths.get(args[0]);Files.createDirectories(out.getParent());Files.write(out,raw);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(rows.size()!=288||linked!=288||!hash.toString().equals("7b3d6e4c27bb47af7c76906ad2c4f1dbc2d42d7b589303975784bbf16f995cb6"))throw new AssertionError("Released group callbacks changed: "+hash);System.out.println("Copifier Trapdoor group Source PASS cases="+rows.size()+" linked="+linked+" sha256="+hash);
    }
}
