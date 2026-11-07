package com.vandorlabs.gear;

import com.vandorlabs.coupler.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Actual native endpoint replacement, journaled reversal and reload on regular/Large mechanisms. */
@SideOnly(Side.CLIENT)
public final class ArmEndpointCouplerLiveChecks {
    private static final BlockPos ROOT=new BlockPos(1903,110,200);
    private static final UUID ID=new UUID(913,477);
    private static int fixture,stage,ticks,checks;private static boolean clicked;private static BlockPos edited;
    private static EnumFacing face(){return fixture==0?EnumFacing.DOWN:fixture==1?EnumFacing.EAST:fixture==2?EnumFacing.UP:EnumFacing.SOUTH;}
    private static boolean rotate(){return fixture<2;}
    private static boolean joined(){return fixture==1;}
    private static GearBlock block(){return fixture%2==0?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;}
    private static BlockPos at(int x,int y,int z){return ROOT.add(ArmOrientation.vector(face(),new BlockPos(x,y,z)));}
    private static Vec3d camera(){return new Vec3d(at(-3,-2,-4)).addVector(.5,.5,.5);}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Coupler endpoint fixture="+fixture+" stage="+stage+" ticks="+ticks+" mount="+face()+": "+why);}
    private static GearTileEntity root(WorldServer world){TileEntity raw=world.getTileEntity(ROOT);need(raw instanceof GearTileEntity,"installed controller");GearTileEntity root=((GearTileEntity)raw).loadedController();need(root!=null,"exact controller retained");return root;}
    private static void identity(NBTTagCompound tag){need(tag.hasUniqueId("CouplerControlIdentity")&&tag.getUniqueId("CouplerControlIdentity").equals(ID),"physical control identity retained");need(tag.getCompoundTag("ForgeCaps").getCompoundTag(ArmEndpointCouplerCaps.KEY.toString()).getInteger("OpaqueRepresentation")==22,"original native opaque capability preserved");}
    public static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(fixture==4){System.out.println("[vandorlabs][reprolab] utility26-focused PASS arm-endpoint-coupler checks="+checks+" regular/Large native rotated full+slab edits/level captured mode/journaled double reversal/reload/control identity/opaque caps");return true;}
        ArmEndpointCouplerCaps.enable();mc.displayGuiScreen(null);mc.playerController.setGameType(GameType.CREATIVE);
        if(stage==1&&ticks>15&&!clicked&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.inventory.currentItem==0&&mc.player.getDistanceSq(camera().x,camera().y,camera().z)<1){mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(ROOT,face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;}
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player){
        need(++ticks<500,"bounded world-tick motion");
        if(stage==0){
            for(BlockPos p:BlockPos.getAllInBox(ROOT.add(-8,-8,-8),ROOT.add(8,8,8))){world.removeTileEntity(p);world.setBlockState(p,Blocks.AIR.getDefaultState(),2);}
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)world.setBlockState(ROOT.add(x,-7,z),Blocks.STONE.getDefaultState(),2);
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;
            for(int x:new int[]{0,2}){if(!joined()&&x==2)continue;world.setBlockState(at(x,1,0),Blocks.STONE.getDefaultState(),2);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));for(int n=0;n<2;n++)need(player.getHeldItemMainhand().onItemUse(player,world,at(x,1-n,0),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"real supported placement callback");GearTileEntity arm=(GearTileEntity)world.getTileEntity(at(x,0,0));need(arm.rebuild(),"column forms");NBTTagCompound settings=arm.settings();settings.setBoolean("JoinArms",joined());settings.setInteger("JoinRange",3);settings.setInteger("Direction",0);settings.setBoolean("RotatePayload",rotate());arm.configure(settings);}
            for(int x=0;x<=(joined()?2:0);x++)world.setBlockState(at(x,-2,0),Blocks.HAY_BLOCK.getDefaultState(),2);player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.sendContainerToPlayer(player.inventoryContainer);player.connection.setPlayerLocation(camera().x,camera().y,camera().z,0,0);clicked=false;stage=1;ticks=0;return false;
        }
        GearTileEntity arm=root(world);if(ArmTransactions.locked(world,ROOT))return false;
        need(ticks<150||arm.error().isEmpty(),"motion error: "+arm.error());
        if(stage==1){if(!clicked||arm.progress()!=0||arm.payload()==null)return false;need(arm.payload().materialized()==rotate(),"actual grid/fractional endpoint policy");if(!rotate()){arm.target(true);stage=2;ticks=0;return false;}ArmPayload.Endpoint endpoint=ArmPayload.endpoint(arm,0);need(endpoint!=null,"native rotated endpoint available");edited=endpoint.placed(at(joined()?2:0,-2,0));stage=3;ticks=0;return false;}
        if(stage==2){if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;edited=at(0,-2,0);stage=3;ticks=0;return false;}
        if(stage==3){NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());NBTTagCompound payload=saved.getCompoundTag("ArmPayload");NBTTagCompound opaque=new NBTTagCompound();opaque.setByteArray("UnknownPayloadWitness",new byte[]{1,5,9});payload.setTag("FutureEndpointData",opaque);arm.readFromNBT(saved);arm.operator(player);need(player.interactionManager.tryHarvestBlock(edited),"real native endpoint mining");stage=4;ticks=0;return false;}
        if(stage==4){if(ticks<8)return false;need(world.isAirBlock(edited),"native removed before replacement");BlockPos scaffold=edited.down();boolean temporary=world.isAirBlock(scaffold);if(temporary)world.setBlockState(scaffold,Blocks.STONE.getDefaultState(),2);else need(world.getBlockState(scaffold).getBlock()==Blocks.HAY_BLOCK,"reuse ordinary existing platform support");net.minecraft.block.Block replacement=net.minecraft.block.Block.getBlockFromName(fixture%2==0?"vandorlabs:square_coupler":"vandorlabs:vehicle_coupler_slab");need(replacement instanceof BlockCoupler,"canonical replacement registry");player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(replacement));need(player.getHeldItemMainhand().onItemUse(player,world,scaffold,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==EnumActionResult.SUCCESS,"ordinary Coupler item placement");need(world.getTileEntity(edited) instanceof TileCoupler,"replacement native tile");if(temporary)need(player.interactionManager.tryHarvestBlock(scaffold),"remove temporary placement scaffold; mounting support and floor remain");TileCoupler coupler=(TileCoupler)world.getTileEntity(edited);NBTTagCompound tag=coupler.writeToNBT(new NBTTagCompound());tag.setUniqueId("CouplerControlIdentity",ID);coupler.readFromNBT(tag);identity(coupler.writeToNBT(new NBTTagCompound()));player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.sendContainerToPlayer(player.inventoryContainer);arm.target(rotate());stage=5;ticks=0;return false;}
        if(stage==5){if(arm.progress()!=(rotate()?1:0)||arm.payload()==null)return false;if(rotate()&&!arm.payload().materialized())return false;need(arm.payload().blocks.cells.size()==(joined()?3:1),"edited full payload retained");boolean found=false;for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:arm.payload().blocks.cells)if(cell.state.getBlock() instanceof BlockCoupler){identity(cell.tileData());found=true;}need(found,"replacement included in inverse capture");if(joined())need(arm.payload().write().getCompoundTag("FutureEndpointData").hasKey("UnknownPayloadWitness",7),"opaque existing payload header survives endpoint edit");arm.target(!rotate());stage=6;ticks=0;return false;}
        if(stage==6){if(arm.progress()!=(rotate()?0:1)||arm.payload()==null||!arm.payload().materialized())return false;need(world.getTileEntity(edited) instanceof TileCoupler,"next native endpoint restores typed tile");identity(world.getTileEntity(edited).writeToNBT(new NBTTagCompound()));NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());world.removeTileEntity(ROOT);GearTileEntity loaded=new GearTileEntity();loaded.readFromNBT(saved);loaded.setWorld(world);loaded.validate();world.setTileEntity(ROOT,loaded);loaded.operator(player);world.notifyBlockUpdate(ROOT,world.getBlockState(ROOT),world.getBlockState(ROOT),3);stage=7;ticks=0;return false;}
        if(stage==7){if(ticks<10)return false;arm=root(world);need(arm.payload()!=null&&arm.payload().materialized(),"native endpoint reload owns one typed snapshot");identity(world.getTileEntity(edited).writeToNBT(new NBTTagCompound()));arm.target(rotate());stage=8;ticks=0;return false;}
        if(stage==8){if(arm.progress()!=(rotate()?1:0)||arm.payload()==null)return false;need(arm.payload().materialized()==rotate(),"reload reversal uses correct native/fractional policy");fixture++;stage=0;ticks=0;return false;}
        return false;
    }
    private ArmEndpointCouplerLiveChecks(){}
}
