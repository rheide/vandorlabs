package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Real specialized placement, pair formation and post-AIR harvest callbacks keep column ownership distinct. */
public final class CanopyJoinedLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    private static NBTTagCompound settings(int pattern,int column){NBTTagCompound tag=new NBTTagCompound();tag.setInteger("CanopyAngle",pattern==3?column%2:1);tag.setInteger("CanopyMotion",pattern==2?column%2:pattern==4?1:0);tag.setBoolean("CanopySlideForward",pattern==4&&(column&1)!=0);if(pattern==1){tag.setInteger("CanopyShade",column%19);tag.setInteger("CanopyTint",column%4);tag.setInteger("CanopyMaterial",(column&1)==0?-1:64);tag.setInteger("CanopyTextureLayout",column%3);tag.setIntArray("CanopyChannels",new int[]{column+1});}return tag;}
    private static JsonObject snapshot(CanopyPlacementLegacyOracle.World world,BlockCanopy block,EntityPlayer actor){JsonObject result=new JsonObject();JsonArray members=new JsonArray();List<BlockPos> positions=new ArrayList<>();for(Map.Entry<BlockPos,net.minecraft.block.state.IBlockState> e:world.states.entrySet())if(e.getValue().getBlock()==block)positions.add(e.getKey());positions.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));int roots=0,drops=0;
        for(BlockPos p:positions){TileEntityCanopy tile=TileEntityCanopy.at(world,p);if(tile==null)throw new AssertionError("Source joined member missing");JsonObject member=new JsonObject();member.add("pos",pos(p));member.add("anchor",tile.anchor==null?JsonNull.INSTANCE:pos(tile.anchor));member.add("owner",pos(tile.owner().getPos()));member.addProperty("width",tile.width());member.addProperty("draws",tile.draws());member.addProperty("removing",tile.removing);if(p.equals(tile.anchor)){NBTTagCompound own=new NBTTagCompound();tile.large.write(own);member.add("own",CanopySettingsLegacyOracle.tags(own));member.add("pick",CanopySettingsLegacyOracle.tags(block.getPickBlock(world.getBlockState(p),new RayTraceResult(Vec3d.ZERO,EnumFacing.UP,p),world,p,actor).serializeNBT()));}members.add(member);if(tile.draws())roots++;for(ItemStack drop:block.getDrops(world,p,world.getBlockState(p),0))drops+=drop.getCount();}
        result.add("members",members);result.addProperty("renderRoots",roots);result.addProperty("dropOwners",drops);return result;
    }
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0,placements=0,harvests=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8")) {
            for(BlockCanopy block:blocks)if(block.opening())for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int width:new int[]{1,2,3,9,10})for(int pattern=0;pattern<9;pattern++)for(int removed=0;removed<4;removed++) {
                CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();EntityPlayer actor=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("cde5f472-387e-4ae0-9d82-735058c97dc8"),"JoinedCanopyOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};actor.rotationYaw=facing.getOpposite().getHorizontalAngle();List<BlockPos> installed=new ArrayList<>();JsonArray attempts=new JsonArray();
                for(int step=0;step<width;step++){int column=pattern==7?width-1-step:step;if(pattern==8&&column==width/2)continue;BlockPos anchor=ROOT.offset(facing.rotateY(),column),clicked=pattern==5?anchor.up():anchor.down();EnumFacing side=pattern==5?EnumFacing.DOWN:EnumFacing.UP;world.setBlockState(clicked,Blocks.STONE.getDefaultState(),2);if(pattern==6&&column==2)world.setBlockState(anchor.offset(facing.getOpposite(),block.length-1),Blocks.STONE.getDefaultState(),2);ItemStack held=new ItemStack(Item.getItemFromBlock(block),2);NBTTagCompound tag=settings(pattern,column);held.setTagCompound(tag.copy());actor.setHeldItem(EnumHand.MAIN_HAND,held);EnumActionResult result=held.getItem().onItemUse(actor,world,clicked,EnumHand.MAIN_HAND,side,.25F,side==EnumFacing.UP?1:0,.625F);JsonObject attempt=new JsonObject();attempt.addProperty("column",column);attempt.add("input",CanopySettingsLegacyOracle.tags(tag));attempt.addProperty("result",result.name());attempt.addProperty("remaining",held.getCount());attempts.add(attempt);if(result==EnumActionResult.SUCCESS){if(held.getCount()!=1)throw new AssertionError("Source joined placement count");installed.add(anchor);}else if(held.getCount()!=2)throw new AssertionError("Source joined refusal count");placements++;}
                installed.sort(Comparator.comparingInt(p->p.getX()*facing.rotateY().getFrontOffsetX()+p.getZ()*facing.rotateY().getFrontOffsetZ()));JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("requestedWidth",width);row.addProperty("pattern",pattern);row.addProperty("removed",removed);row.add("attempts",attempts);row.add("before",snapshot(world,block,actor));
                if(!installed.isEmpty()){int index=removed==0?0:removed==2?installed.size()-1:installed.size()/2;BlockPos target=installed.get(index);if(removed==3)target=target.offset(facing.getOpposite(),block.length-1);net.minecraft.block.state.IBlockState state=world.getBlockState(target);TileEntityCanopy tile=(TileEntityCanopy)world.getTileEntity(target);row.add("mined",pos(target));world.states.put(target,Blocks.AIR.getDefaultState());block.breakBlock(world,target,state);block.harvestBlock(world,actor,target,state,tile,ItemStack.EMPTY);JsonArray loot=new JsonArray();for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(entity instanceof net.minecraft.entity.item.EntityItem)loot.add(CanopySettingsLegacyOracle.tags(((net.minecraft.entity.item.EntityItem)entity).getItem().serializeNBT()));if(loot.size()!=1)throw new AssertionError("Source post-AIR harvest must own one configured drop "+loot.size());row.add("loot",loot);row.add("after",snapshot(world,block,actor));harvests++;}
                out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"915cd17a455490d6500b415771677c7bd9cf2bb73722b9c6961726195df52b3d");
        System.out.println("Actual joined canopy callbacks PASS profiles="+profiles+" placements="+placements+" harvests="+harvests+" file="+file);
    }
}
