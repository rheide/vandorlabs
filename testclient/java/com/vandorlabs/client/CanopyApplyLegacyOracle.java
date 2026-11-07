package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapStorage;

/** Actual apply/reshape callbacks: ownership, open-pose restrictions, released cells and rollback. */
public final class CanopyApplyLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    static class World extends CanopyPlacementLegacyOracle.World {
        final MapStorage storage=new MapStorage(null);BlockPos denied;boolean applying,triggered;int fault,writes,removals;
        @Override public MapStorage getPerWorldStorage(){return storage;}
        @Override public boolean isBlockModifiable(EntityPlayer player,BlockPos pos){return !pos.equals(denied);}
        @Override public boolean setBlockState(BlockPos pos,IBlockState state,int flags){
            if(applying&&state.getBlock() instanceof BlockCanopy&&getBlockState(pos).getBlock()!=state.getBlock()){
                writes++;if(writes==2&&fault==1){triggered=true;return false;}boolean changed=super.setBlockState(pos,state,flags);if(writes==2&&fault==2){triggered=true;tiles.remove(pos);}return changed;
            }
            if(applying&&state.getBlock()==Blocks.AIR&&getBlockState(pos).getBlock() instanceof BlockCanopy&&++removals==2&&fault==3){triggered=true;return false;}return super.setBlockState(pos,state,flags);
        }
    }
    private static JsonArray pos(BlockPos p){JsonArray out=new JsonArray();out.add(p.getX());out.add(p.getY());out.add(p.getZ());return out;}
    private static LargeCanopySettings copy(LargeCanopySettings source){NBTTagCompound tag=new NBTTagCompound();source.write(tag);LargeCanopySettings next=new LargeCanopySettings();next.read(tag);return next;}
    static TileEntityCanopy setup(World world,BlockCanopy block,EnumFacing facing,int mount,boolean fresh,int columns,float progress,boolean release){
        LargeCanopySettings initial=new LargeCanopySettings();initial.mount=mount;initial.openable=block.opening()||LargeCanopySettings.supports(block.kind);
        for(int column=0;column<columns;column++){
            BlockPos anchor=ROOT.offset(facing.rotateY(),column);IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);world.setBlockState(anchor,state,2);TileEntityCanopy root=TileEntityCanopy.at(world,anchor);root.anchor=anchor;root.centeredPlacement=fresh&&CanopyVariants.centered(block.kind);root.hollowPlacement=fresh&&root.resizable();initial.shade=column%3;NBTTagCompound settings=new NBTTagCompound();initial.write(settings);root.large.read(settings);List<BlockPos> cells=new ArrayList<>(root.cells());
            for(BlockPos cell:cells){if(!cell.equals(anchor))world.setBlockState(cell,state,2);TileEntityCanopy member=TileEntityCanopy.at(world,cell);member.anchor=anchor;member.centeredPlacement=root.centeredPlacement;member.hollowPlacement=root.hollowPlacement;member.large.read(settings);}if(fresh)root.setFootprint(cells);root.tryPair();
        }
        TileEntityCanopy root=TileEntityCanopy.at(world,ROOT).owner();root.progress=root.previous=progress;root.targetOpen=progress>0;if(release)CanopyReservations.releaseOpen(root);return root;
    }
    private static LargeCanopySettings request(TileEntityCanopy root,int profile){LargeCanopySettings next=copy(root.large);switch(profile){
        case 0:break;case 1:case 22:next.depth=0;next.scale=10;break;case 2:case 19:case 20:case 21:next.depth=80;next.scale=100;break;case 3:next.depth=17;next.scale=90;break;
        case 4:next.shade=2;next.tint=3;next.material=64;next.textureLayout(1);break;case 5:next.shade=0;next.tint=0;next.material=-1;break;
        case 6:next.motion=1-next.motion;break;case 7:next.hinge=(next.hinge+1)%4;break;case 8:next.openable=!next.openable;break;case 9:next.slideForward=!next.slideForward;break;
        case 10:next.angle=3;break;case 11:next.mount=(next.mount+1)%4;break;case 12:next.redstone=true;next.channels=com.vandorlabs.redstone.ChannelList.of(2,7);next.activateOnPower=false;break;
        case 13:next.redstone=false;next.channels=com.vandorlabs.redstone.ChannelList.of(3);break;case 14:next.depth=81;break;case 15:next.scale=9;break;case 16:next.shade=999;break;case 17:next.tint=999;break;case 18:next.textureLayout(2);break;case 23:next.shade=2;break;default:throw new AssertionError(profile);
        }return next;}
    private static JsonObject snapshot(World world,TileEntityCanopy root){
        JsonObject result=new JsonObject();NBTTagCompound saved=new NBTTagCompound();root.writeToNBT(saved);result.add("saved",CanopySettingsLegacyOracle.tags(saved));JsonArray footprint=new JsonArray();for(BlockPos cell:root.cells())footprint.add(pos(cell));result.add("footprint",footprint);List<BlockPos> cells=new ArrayList<>();for(Map.Entry<BlockPos,IBlockState> entry:world.states.entrySet())if(entry.getValue().getBlock()!=Blocks.AIR)cells.add(entry.getKey());cells.sort(Comparator.comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));JsonArray members=new JsonArray();
        for(BlockPos cell:cells){JsonObject member=new JsonObject();member.add("pos",pos(cell));member.addProperty("id",world.getBlockState(cell).getBlock().getRegistryName().toString());TileEntity tile=world.tiles.get(cell);if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=(TileEntityCanopy)tile;member.add("anchor",canopy.anchor==null?JsonNull.INSTANCE:pos(canopy.anchor));member.add("owner",pos(canopy.owner().getPos()));member.addProperty("settings",canopy.large.key());member.addProperty("centered",canopy.centeredPlacement);member.addProperty("hollow",canopy.hollowPlacement);member.addProperty("draws",canopy.draws());member.addProperty("removing",canopy.removing);}members.add(member);}result.add("members",members);return result;
    }
    private static int profiles,applied,failedWrites;
    private static void capture(PrintWriter out,Gson gson,BlockCanopy block,EnumFacing facing,int mount,boolean fresh,int columns,float progress,boolean release,int profile){
        World world=new World();world.time=1;TileEntityCanopy root=setup(world,block,facing,mount,fresh,columns,progress,release);EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.fromString("60dac722-4f92-435b-89d4-393e2ce86894"),"CanopyApplyOracle")){public boolean isCreative(){return false;}public boolean isSpectator(){return false;}};LargeCanopySettings next=request(root,profile);BlockPos obstruction=null;
        if(profile==19){if(root.resizable()){Set<BlockPos> old=new HashSet<>(root.cells());for(BlockPos cell:LargeCanopyOwnership.shellCells(block,root.getPos(),facing,next,root.centeredPlacement))if(!old.contains(cell)){obstruction=cell;world.setBlockState(cell,Blocks.STONE.getDefaultState(),2);break;}}else world.denied=ROOT;}
        if(profile==20)world.fault=1;if(profile==21)world.fault=2;if(profile==22)world.fault=3;if(profile==23)world.denied=ROOT;
        JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("facing",facing.getIndex());row.addProperty("mount",mount);row.addProperty("fresh",fresh);row.addProperty("columns",columns);row.addProperty("progress",progress);row.addProperty("release",release);row.addProperty("profile",profile);NBTTagCompound requested=new NBTTagCompound();next.write(requested);row.add("request",CanopySettingsLegacyOracle.tags(requested));if(obstruction!=null)row.add("obstruction",pos(obstruction));row.add("before",snapshot(world,root));world.applying=true;boolean accepted=LargeCanopyOwnership.apply(root,player,next);world.applying=false;row.addProperty("applied",accepted);row.addProperty("faultTriggered",world.triggered);row.addProperty("writes",world.writes);row.addProperty("removals",world.removals);row.add("after",snapshot(world,root));out.println(gson.toJson(row));profiles++;if(accepted)applied++;if(world.triggered)failedWrites++;
        net.minecraftforge.event.world.WorldEvent.Unload unload=new net.minecraftforge.event.world.WorldEvent.Unload(world);CanopyReservations.unload(unload);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(unload);com.vandorlabs.world.LoadedTileIndex.Lifecycle.unload(unload);
    }
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();com.vandorlabs.VandorLabs.logger=org.apache.logging.log4j.LogManager.getLogger("CanopyApplyReference");org.apache.logging.log4j.core.config.Configurator.setLevel("CanopyApplyReference",org.apache.logging.log4j.Level.ERROR);File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();
        try(PrintWriter out=new PrintWriter(file,"UTF-8")){for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS){if(LargeCanopySettings.supports(block.kind)){for(int mount=0;mount<4;mount++)for(boolean fresh:new boolean[]{false,true})for(float progress:new float[]{0,.5F,1})for(boolean release:progress==1?new boolean[]{false,true}:new boolean[]{false})for(int profile=0;profile<24;profile++)capture(out,gson,block,facing,mount,fresh,1,progress,release,profile);}else{for(int profile=0;profile<24;profile++)capture(out,gson,block,facing,0,true,1,0,false,profile);if(block.opening())for(float progress:new float[]{0,.5F,1})for(int profile=0;profile<24;profile++)capture(out,gson,block,facing,0,true,3,progress,false,profile);}}}
        SegmentedReferenceGolden.check(file,"0f70cd2b6ed3baa89a65c859da6bd980b642716aa01ab9e90debf04e9538d14f");
        System.out.println("Actual canopy apply callbacks PASS profiles="+profiles+" applied="+applied+" writeFaults="+failedWrites+" file="+file);
    }
}
