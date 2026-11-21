package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Shared server fixture for the live and cold Core ownership paths. */
public final class ArmCoreFixture {
    static final BlockPos ROOT=new BlockPos(2735,110,240);
    static final ResourceLocation CAPS=new ResourceLocation("vandorlabs","core_arm_probe");
    private static boolean registered;
    static void enableCaps(){if(!registered){registered=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ArmCoreFixture.class);}}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Core arm setup: "+why);}
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<TileEntity> event){if(event.getObject() instanceof TileEntityConstructor||event.getObject() instanceof TileEntityConstructorPart)event.addCapability(CAPS,new Probe(event.getObject()));}
    private static final class Probe implements ICapabilitySerializable<NBTTagCompound>{final TileEntity tile;Probe(TileEntity tile){this.tile=tile;}public boolean hasCapability(Capability<?> cap,EnumFacing side){return false;}public <T>T getCapability(Capability<T> cap,EnumFacing side){return null;}public NBTTagCompound serializeNBT(){NBTTagCompound n=new NBTTagCompound();n.setInteger("Representation",tile.getWorld() instanceof VehicleWorld?47:22);n.setByteArray("OpaqueInsertedToken",new byte[]{4,-3,17});return n;}public void deserializeNBT(NBTTagCompound n){}}
    /** Real saved-world clients need both the authoritative inventory and selected-slot packet. */
    static void emptyHands(net.minecraft.entity.player.EntityPlayerMP actor,GameType mode){
        actor.setGameType(mode);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();actor.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));actor.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-2,0,ItemStack.EMPTY));
    }
    static BlockPos setup(WorldServer w,net.minecraft.entity.player.EntityPlayerMP player,int profile){
        return setup(w,player,profile,"meridian_drive_core");
    }
    static BlockPos setup(WorldServer w,net.minecraft.entity.player.EntityPlayerMP player,int profile,String coreId){
        return setup(w,player,profile,coreId,false);
    }
    static BlockPos setup(WorldServer w,net.minecraft.entity.player.EntityPlayerMP player,int profile,String coreId,boolean inverted){
        return setup(w,player,profile,coreId,inverted,0);
    }
    static BlockPos setup(WorldServer w,net.minecraft.entity.player.EntityPlayerMP player,int profile,String coreId,boolean inverted,int widthOverride){
        enableCaps();EnumFacing mount=profile==1?EnumFacing.EAST:EnumFacing.UP;GearBlock block=profile==0?GearRegistry.GEAR_EXTENDER:GearRegistry.GEAR_SWING_ARM;
            for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-8,-8,-8),ROOT.add(8,8,8))){w.removeTileEntity(at);w.setBlockState(at,at.getY()==ROOT.getY()-7?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
            player.setGameType(GameType.CREATIVE);player.inventory.currentItem=0;player.capabilities.isFlying=true;player.sendPlayerAbilities();w.setBlockState(ROOT.offset(mount.getOpposite()),Blocks.STONE.getDefaultState(),2);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,2));for(BlockPos support:new BlockPos[]{ROOT.offset(mount.getOpposite()),ROOT})need(player.getHeldItemMainhand().onItemUse(player,w,support,EnumHand.MAIN_HAND,mount,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported arm placement");GearTileEntity arm=(GearTileEntity)w.getTileEntity(ROOT);need(arm.rebuild(),"two-section assembly");NBTTagCompound settings=arm.settings();settings.setBoolean("RotatePayload",profile==1);settings.setInteger("Direction",0);arm.configure(settings);
            BlockConstructorCore core=(BlockConstructorCore)net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",coreId));BlockPos contact=ROOT.offset(mount,2),owner=profile==1?contact.down():contact.west();int width=widthOverride==0?core.width:widthOverride,height=widthOverride==0?core.height:com.vandorlabs.navigation.ObserverScale.height(core.design,width),depth=widthOverride==0?core.depth:width;if(com.vandorlabs.navigation.ObserverScale.observer(core.design)&&profile!=1&&centreObservers){
                // Observers take RF only through the centre Power Port, so centre the base on the arm tip.
                com.vandorlabs.machines.CoreLayout centred=new com.vandorlabs.machines.CoreLayout(width,height,depth,EnumFacing.NORTH.getIndex(),0,0);
                owner=contact.add(-centred.x(width/2,depth/2),0,-centred.z(width/2,depth/2));
            }
            if(inverted)owner=owner.up(height-1);w.setBlockState(owner,core.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),2);TileEntityConstructor tile=(TileEntityConstructor)w.getTileEntity(owner);com.vandorlabs.machines.CoreLayout layout=new com.vandorlabs.machines.CoreLayout(width,height,depth,EnumFacing.NORTH.getIndex(),0,0);net.minecraft.block.Block matter=net.minecraft.block.Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_block"));for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<depth;z++){BlockPos normal=owner.add(layout.x(x,z),y,layout.z(x,z)),at=inverted?new BlockPos(normal.getX(),owner.getY()-y,normal.getZ()):normal;if(!at.equals(owner)&&layout.frame(x,y,z))w.setBlockState(at,matter.getDefaultState(),2);}
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);need(core.onBlockActivated(w,owner,w.getBlockState(owner),player,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)&&tile.formed(),"ordinary Core frame activation");if(tile.observer()){NBTTagCompound legacy=tile.writeToNBT(new NBTTagCompound());legacy.setTag("InsertedToken",new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,0).writeToNBT(new NBTTagCompound()));tile.readFromNBT(legacy);}else tile.setInventorySlotContents(0,new ItemStack(com.vandorlabs.items.ModItems.DATA_CHIP,1,0));need(!tile.getStackInSlot(0).isEmpty(),"real inserted chip");if(tile.observer())com.vandorlabs.redstone.TransmitterSlots.configure(tile,com.vandorlabs.redstone.ChannelList.of(47),256,false);need(w.getBlockState(contact).getBlock() instanceof BlockConstructorPart,"arm contacts Part rather than Core");
            BlockPos neighbour=(profile==1?owner.south(depth):owner.east(width)).up();w.setBlockState(neighbour,Blocks.COBBLESTONE.getDefaultState(),2);return neighbour;
    }
    /** False builds observers with the arm under a non-port cell, as players may. */
    static boolean centreObservers=true;
    private ArmCoreFixture(){}
}
