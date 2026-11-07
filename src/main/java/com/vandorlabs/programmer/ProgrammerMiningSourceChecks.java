package com.vandorlabs.programmer;
import com.google.gson.*;
import com.vandorlabs.items.ModItems;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
/** Actual released harvest engine, including member teardown under Creative and wrong tools. */
public final class ProgrammerMiningSourceChecks {
 private final List<EntityItem> spawned=new ArrayList<>();private boolean watching;
 @SubscribeEvent public void joined(EntityJoinWorldEvent event){if(watching&&!event.getWorld().isRemote&&event.getEntity() instanceof EntityItem)spawned.add((EntityItem)event.getEntity());}
 public static void run(WorldServer world,BlockPos base,File output)throws Exception{new ProgrammerMiningSourceChecks().record(world,base,output);}
 private void record(WorldServer world,BlockPos base,File output)throws Exception{
  Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:data_chip_programmer"));if(!(block instanceof ProgrammerBlock))throw new IllegalStateException("Missing canonical Source Programmer");
  FakePlayer actor=new FakePlayer(world,new com.mojang.authlib.GameProfile(new UUID(0,0x51A1),"ProgrammerMining"));actor.connection=new net.minecraft.network.NetHandlerPlayServer(world.getMinecraftServer(),new net.minecraft.network.NetworkManager(net.minecraft.network.EnumPacketDirection.SERVERBOUND),actor);JsonArray rows=new JsonArray();MinecraftForge.EVENT_BUS.register(this);
  try{int profile=0;for(EnumFacing face:EnumFacing.HORIZONTALS)for(int part=0;part<4;part++)for(int mode=0;mode<4;mode++){
   BlockPos root=base.east(profile%8*6).south(profile/8*6);for(int member=0;member<4;member++){BlockPos cell=ProgrammerBlock.cell(root,face,member);world.getChunkFromBlockCoords(cell);world.setBlockState(cell,block.getDefaultState().withProperty(ProgrammerBlock.FACING,face).withProperty(ProgrammerBlock.PART,member),3);}
   ProgrammerTile owner=(ProgrammerTile)world.getTileEntity(root);for(int slot=0;slot<3;slot++){ItemStack token=new ItemStack(slot==1?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,1,slot+1);NBTTagCompound tag=new NBTTagCompound();tag.setString("MiningOwner","slot"+slot);token.setTagCompound(tag);owner.setInventorySlotContents(slot,token);}
   actor.inventory.clear();actor.setPosition(root.getX()+.5,root.getY(),root.getZ()-2);actor.interactionManager.setGameType(mode==3?GameType.CREATIVE:GameType.SURVIVAL);actor.setHeldItem(EnumHand.MAIN_HAND,mode==0?new ItemStack(Items.WOODEN_PICKAXE):mode==2?new ItemStack(Items.WOODEN_SHOVEL):ItemStack.EMPTY);
   spawned.clear();watching=true;boolean harvested;try{harvested=actor.interactionManager.tryHarvestBlock(ProgrammerBlock.cell(root,face,part));}finally{watching=false;}
   JsonObject row=new JsonObject();row.addProperty("facing",face.getIndex());row.addProperty("part",part);row.addProperty("mode",mode);row.addProperty("harvested",harvested);int plain=0;List<String> credentials=new ArrayList<>();for(EntityItem entity:spawned){ItemStack item=entity.getItem();if(item.getItem()==Item.getItemFromBlock(block))plain+=item.getCount();else if(item.getItem()==ModItems.ACCESS_CARD||item.getItem()==ModItems.DATA_CHIP)credentials.add(item.writeToNBT(new NBTTagCompound()).toString());else throw new IllegalStateException("Unexpected Source harvest item "+item);entity.setDead();}Collections.sort(credentials);row.addProperty("plain",plain);JsonArray items=new JsonArray();for(String item:credentials)items.add(item);row.add("credentials",items);JsonArray cells=new JsonArray();for(int member=0;member<4;member++){BlockPos cell=ProgrammerBlock.cell(root,face,member);cells.add(world.isAirBlock(cell));world.setBlockToAir(cell);}row.add("removed",cells);rows.add(row);profile++;
  }}finally{watching=false;MinecraftForge.EVENT_BUS.unregister(this);}
  String json=new Gson().toJson(rows);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));JsonObject result=new JsonObject();result.addProperty("golden",hash.toString());result.add("cases",rows);Files.write(new File(output,"programmer-mining-forge.json").toPath(),new Gson().toJson(result).getBytes(StandardCharsets.UTF_8));if(rows.size()!=64||!hash.toString().equals("cc2bd5a3955dd868fda4292bd02e2464bbc47111d4941baa5efb64dbecfb5884"))throw new IllegalStateException("Incomplete actual Source mining matrix");System.out.println("[vandorlabs][reprolab] programmer-mining-source PASS actual-harvest64 golden="+hash);
 }
 private ProgrammerMiningSourceChecks(){}
}
