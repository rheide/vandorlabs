package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Actual applicability boundary: shared saved slots do not make Screen families waterloggable. */
public final class ScreenFluidBoundaryLegacyChecks {
    private static final BlockPos POS=new BlockPos(8,80,8);
    private static final class Player extends EntityPlayer {Player(NonRenderingChecks.MemoryWorld world){super(world,new com.mojang.authlib.GameProfile(new UUID(0,0),"ScreenFluidBoundary"));capabilities.allowEdit=true;}public boolean isCreative(){return capabilities.isCreativeMode;}public boolean isSpectator(){return false;}}
    private static NBTTagCompound input(int kind){NBTTagCompound root=new NBTTagCompound();if(kind==0)return root;if(kind==4||kind==6){root.setBoolean("Waterlogged",true);if(kind==6)root.setString("ContainedFluids","future:envelope");return root;}NBTTagCompound data=new NBTTagCompound(),slot=new NBTTagCompound(),extra=new NBTTagCompound();slot.setString("FluidName",kind==2?"lava":kind==3?"future:screen-fluid":"water");slot.setInteger("Amount",kind==2?333:777);extra.setString("Owner","source:screen-slot");slot.setTag("Tag",extra);data.setTag(kind==2||kind==5?"Side1":"Side0",slot);if(kind==5)data.setString("Side0","future:slot");root.setTag("ContainedFluids",data);return root;}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityAnimatedScreenSelector.class,new ResourceLocation("minecraft:screen_fluid_boundary"));List<Block> blocks=new ArrayList<>();for(String id:ScreenFamilyLegacyChecks.IDS){Block found=null;for(Block block:ModBlocks.BLOCKS)if(block.getRegistryName().getResourcePath().equals(id))found=block;if(found==null)throw new AssertionError(id);blocks.add(found);}blocks.add(new BlockDiagonalHalfConsole("programmable_diagonal_half_console"));Path file=Paths.get(args[0]);Files.createDirectories(file.toAbsolutePath().getParent());MessageDigest hash=MessageDigest.getInstance("SHA-256");int states=0,cases=0,buckets=0;
        try(java.io.PrintWriter out=new java.io.PrintWriter(Files.newBufferedWriter(file))){for(Block block:blocks)for(net.minecraft.block.state.IBlockState state:block.getBlockState().getValidStates()){states++;for(int kind=0;kind<7;kind++){
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);world.setBlockState(POS,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(POS);int baseline=block.getLightValue(state,world,POS);tile.fluids.read(input(kind));NBTTagCompound stored=new NBTTagCompound();tile.fluids.write(stored);
            boolean supports=WaterloggedParts.supports(state),wet=WaterloggedParts.wet(world,POS),render=RenderFluidPresence.wet(tile);Boolean liquid=WaterloggedParts.liquid(world,POS,new AxisAlignedBB(8,80,8,9,81,9));boolean contact=WaterloggedParts.atPoint(world,POS,new Vec3d(8.5,80.5,8.5))!=null;int light=block.getLightValue(state,world,POS);if(supports||wet||render||liquid!=null||contact||light!=baseline)throw new AssertionError("Unsupported Screen fluid gameplay "+block.getRegistryName()+" "+state+" kind="+kind);
            Player player=new Player(world);for(EnumFacing face:EnumFacing.values())for(boolean filled:new boolean[]{false,true}){ItemStack held=new ItemStack(filled?Items.WATER_BUCKET:Items.BUCKET);player.setHeldItem(EnumHand.MAIN_HAND,held);PlayerInteractEvent.RightClickBlock event=new PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,POS,face,new Vec3d(8.5,80.5,8.5));WaterloggedParts.bucket(event);if(event.isCanceled()||player.getHeldItem(EnumHand.MAIN_HAND)!=held||held.getCount()!=1)throw new AssertionError("Screen fluid bucket intercepted "+block.getRegistryName());buckets++;}
            NBTTagCompound after=new NBTTagCompound();tile.fluids.write(after);if(!stored.equals(after))throw new AssertionError("Unsupported bucket mutated retained slots");JsonObject row=new JsonObject();row.addProperty("id",block.getRegistryName().getResourcePath());row.addProperty("state",state.toString());row.addProperty("kind",kind);row.addProperty("supports",supports);row.addProperty("wet",wet);row.addProperty("render",render);row.addProperty("contact",contact);row.addProperty("light",light);row.addProperty("saved",stored.toString());String line=row.toString();out.println(line);hash.update(line.getBytes(StandardCharsets.UTF_8));cases++;
        }}}StringBuilder golden=new StringBuilder();for(byte value:hash.digest())golden.append(String.format(Locale.ROOT,"%02x",value&255));if(blocks.size()!=8||states!=82||cases!=574||buckets!=6888||!golden.toString().equals("1fb20bc7ff9cbcad2869e7c9787e930bb1e3c046965873a7e730ed03697cf9b5"))throw new AssertionError("Screen fluid applicability corpus changed "+golden);System.out.println("Actual Screen fluid boundary PASS families="+blocks.size()+" states="+states+" cases="+cases+" buckets="+buckets+" golden="+golden);
    }
}
