package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Payload mining has its own target identity and a durable transfer to normal item drops. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class ArmPayloadHarvest {
    public static final class Target {
        public final BlockPos root,cell;
        public Target(BlockPos root,BlockPos cell){this.root=root.toImmutable();this.cell=cell.toImmutable();}
    }
    private static final Map<UUID,Dig> DIGS=new HashMap<>();
    static String diagnostic(EntityPlayerMP player){Dig dig=DIGS.get(player.getUniqueID());return dig==null?"no held dig":"progress="+dig.progress+" age="+(player.world.getTotalWorldTime()-dig.tick);}
    private static final class Dig {BlockPos root,cell;long tick;double progress;ItemStack tool;}
    public static RayTraceResult trace(GearTileEntity arm,Vec3d start,Vec3d end){
        ArmPayload payload=arm.payload();if(payload==null||payload.materialized()||payload.hasOwnershipJournal())return null;
        Vec3d origin=payload.transform(arm,arm.progress(),0,0,0).add(new Vec3d(arm.getPos()));
        Vec3d a=payload.transform(arm,arm.progress(),1,0,0).add(new Vec3d(arm.getPos())).subtract(origin),b=payload.transform(arm,arm.progress(),0,1,0).add(new Vec3d(arm.getPos())).subtract(origin),c=payload.transform(arm,arm.progress(),0,0,1).add(new Vec3d(arm.getPos())).subtract(origin);
        Vec3d s=start.subtract(origin),e=end.subtract(origin);
        RayTraceResult hit=payload.view(arm).rayTraceBlocks(new Vec3d(s.dotProduct(a),s.dotProduct(b),s.dotProduct(c)),new Vec3d(e.dotProduct(a),e.dotProduct(b),e.dotProduct(c)),false,true,false);
        if(hit==null)return null;Vec3d point=origin.add(a.scale(hit.hitVec.x)).add(b.scale(hit.hitVec.y)).add(c.scale(hit.hitVec.z));RayTraceResult result=new RayTraceResult(point,hit.sideHit,arm.getPos());result.hitInfo=new Target(arm.getPos(),hit.getBlockPos());return result;
    }
    public static void dig(EntityPlayerMP player,BlockPos root,BlockPos cell,boolean abort){
        if(abort){DIGS.remove(player.getUniqueID());return;}
        WorldServer world=player.getServerWorld();if(player.isDead||player.isSpectator()||!world.isBlockLoaded(root)||ArmTransactions.locked(world,root))return;
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(root);if(!(raw instanceof GearTileEntity))return;GearTileEntity arm=(GearTileEntity)raw;
        Vec3d eyes=player.getPositionEyes(1),end=eyes.add(player.getLook(1).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue()));RayTraceResult hit=GearInteractions.trace(world,eyes,end);
        if(hit==null||!(hit.hitInfo instanceof Target)||!root.equals(((Target)hit.hitInfo).root)||!cell.equals(((Target)hit.hitInfo).cell)||arm.progress()!=0&&arm.progress()!=1)return;
        VehicleStructure.Cell selected=null;for(VehicleStructure.Cell candidate:arm.payload().blocks.cells)if(candidate.pos.equals(cell)){selected=candidate;break;}if(selected==null)return;
        BlockPos logical=root.add(arm.payload().offset).add(cell);for(BlockPos at:new BlockPos[]{root,logical,new BlockPos(hit.hitVec)})com.vandorlabs.vehicle.VehicleService.permission(player,at);
        if(selected.state.getBlockHardness(arm.payload().view(arm),cell)<0)return;
        if(!player.capabilities.isCreativeMode){
            Dig dig=DIGS.get(player.getUniqueID());long tick=world.getTotalWorldTime();if(dig==null||!root.equals(dig.root)||!cell.equals(dig.cell)||tick-dig.tick>2||!ItemStack.areItemStacksEqual(dig.tool,player.getHeldItemMainhand())){dig=new Dig();dig.root=root;dig.cell=cell;dig.tick=tick-1;dig.tool=player.getHeldItemMainhand().copy();DIGS.put(player.getUniqueID(),dig);}if(dig.tick==tick)return;dig.tick=tick;dig.progress+=selected.state.getPlayerRelativeBlockHardness(player,arm.payload().view(arm),cell);if(dig.progress<1)return;
        }
        if(com.vandorlabs.redstone.TransmitterDrops.protectionProbe(new net.minecraftforge.event.world.BlockEvent.BreakEvent(world,logical,selected.state,player)))return;
        net.minecraft.util.NonNullList<ItemStack> drops=net.minecraft.util.NonNullList.create();
        if(!player.capabilities.isCreativeMode){
            if(selected.state.getBlock().canHarvestBlock(arm.payload().view(arm),cell,player)){
                int fortune=net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.FORTUNE,player.getHeldItemMainhand());boolean silk=net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.SILK_TOUCH,player.getHeldItemMainhand())>0&&selected.state.getBlock().canSilkHarvest(arm.payload().view(arm),cell,selected.state,player);
                if(silk)drops.add(selected.state.getBlock().getPickBlock(selected.state,new RayTraceResult(new Vec3d(cell),EnumFacing.UP,cell),arm.payload().view(arm),cell,player));else selected.state.getBlock().getDrops(drops,arm.payload().view(arm),cell,selected.state,fortune);
                float chance=net.minecraftforge.event.ForgeEventFactory.fireBlockHarvesting(drops,world,logical,selected.state,fortune,1,silk,player);drops.removeIf(stack->world.rand.nextFloat()>chance);
            }
        }
        net.minecraft.tileentity.TileEntity tile=arm.payload().view(arm).getTileEntity(cell);com.vandorlabs.redstone.TransmitterDrops.retainedDrops(tile,drops);if(tile instanceof net.minecraft.inventory.IInventory){net.minecraft.inventory.IInventory inventory=(net.minecraft.inventory.IInventory)tile;for(int slot=0;slot<inventory.getSizeInventory();slot++)if(!inventory.getStackInSlot(slot).isEmpty())drops.add(inventory.getStackInSlot(slot).copy());}
        if(drops.size()>256)throw new IllegalArgumentException("Too many dropped stacks; attachment retained");
        NBTTagCompound extra=new NBTTagCompound();extra.setBoolean("HarvestCreative",player.capabilities.isCreativeMode);extra.setLong("HarvestCell",cell.toLong());extra.setUniqueId("HarvestActor",player.getUniqueID());extra.setTag("HarvestPayload",arm.payload().write());extra.setTag("HarvestTool",player.getHeldItemMainhand().writeToNBT(new NBTTagCompound()));NBTTagList items=new NBTTagList();
        Vec3d center=arm.payload().transform(arm,arm.progress(),cell.getX()+.5,cell.getY()+.5,cell.getZ()+.5).add(new Vec3d(root));
        for(ItemStack stack:drops)if(!stack.isEmpty()){EntityItem item=new EntityItem(world,center.x,center.y,center.z,stack);freeze(item);items.appendTag(item.writeToNBT(new NBTTagCompound()));}extra.setTag("HarvestDrops",items);
        arm.operator(player);ArmTransactions.prepareHarvest(arm,extra);DIGS.remove(player.getUniqueID());
    }
    private static void freeze(EntityItem item){item.setInfinitePickupDelay();item.setNoGravity(true);item.setEntityInvulnerable(true);item.motionX=item.motionY=item.motionZ=0;}
    static void hold(WorldServer world,NBTTagCompound record){for(NBTTagCompound data:items(record)){net.minecraft.entity.Entity raw=world.getEntityFromUuid(data.getUniqueId("UUID"));if(raw instanceof EntityItem){freeze((EntityItem)raw);NBTTagList p=data.getTagList("Pos",6);raw.setPosition(p.getDoubleAt(0),p.getDoubleAt(1),p.getDoubleAt(2));}}}
    static java.util.List<NBTTagCompound> items(NBTTagCompound record){NBTTagList list=record.getTagList("HarvestDrops",10);if(list.tagCount()>256)throw new IllegalArgumentException("Oversized harvest journal");java.util.List<NBTTagCompound> result=new ArrayList<>();for(int i=0;i<list.tagCount();i++)result.add(list.getCompoundTagAt(i));return result;}
    static void apply(WorldServer world,BlockPos root,NBTTagCompound record){
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(root);if(!(raw instanceof GearTileEntity))throw new IllegalArgumentException("Arm changed while mining");GearTileEntity arm=(GearTileEntity)raw;EntityPlayerMP player=(EntityPlayerMP)world.getPlayerEntityByUUID(record.getUniqueId("HarvestActor"));
        if(player==null||player.isDead||player.capabilities.isCreativeMode!=record.getBoolean("HarvestCreative")||arm.payload()==null||!arm.payload().write().equals(record.getCompoundTag("HarvestPayload"))||!ItemStack.areItemStacksEqual(player.getHeldItemMainhand(),new ItemStack(record.getCompoundTag("HarvestTool"))))throw new IllegalArgumentException("Mining inputs changed; payload retained");
        BlockPos cell=BlockPos.fromLong(record.getLong("HarvestCell"));net.minecraft.block.state.IBlockState mined=arm.payload().view(arm).getBlockState(cell);BlockPos logical=root.add(arm.payload().offset).add(cell);try(ArmTransactions.Scope scope=ArmTransactions.authorizing(arm)){com.vandorlabs.vehicle.VehicleService.permission(player,root);}arm.removePayloadCell(cell);
        for(NBTTagCompound data:items(record)){EntityItem item=new EntityItem(world);item.readFromNBT(data);if(!world.spawnEntity(item))throw new IllegalArgumentException("Item drop refused; payload retained");}
        if(!player.capabilities.isCreativeMode){player.addExhaustion(.005F);if(!player.getHeldItemMainhand().isEmpty())player.getHeldItemMainhand().onBlockDestroyed(world,mined,logical,player);}
    }
    static void rollback(WorldServer world,NBTTagCompound record){for(NBTTagCompound data:items(record)){net.minecraft.entity.Entity item=world.getEntityFromUuid(data.getUniqueId("UUID"));if(item instanceof EntityItem)world.removeEntityDangerously(item);}}
    static void publish(WorldServer world,NBTTagCompound record){for(NBTTagCompound data:items(record)){net.minecraft.entity.Entity raw=world.getEntityFromUuid(data.getUniqueId("UUID"));if(raw instanceof EntityItem){EntityItem item=(EntityItem)raw;item.setDefaultPickupDelay();item.setNoGravity(false);item.setEntityInvulnerable(false);}}}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void preventWrongBlock(net.minecraftforge.event.world.BlockEvent.BreakEvent event){
        if(!(event.getPlayer() instanceof EntityPlayerMP)||!(event.getWorld().getTileEntity(event.getPos()) instanceof GearTileEntity))return;
        EntityPlayerMP player=(EntityPlayerMP)event.getPlayer();Vec3d eyes=player.getPositionEyes(1);RayTraceResult hit=GearInteractions.trace(event.getWorld(),eyes,eyes.add(player.getLook(1).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue())));
        if(hit!=null&&hit.hitInfo instanceof Target&&event.getPos().equals(((Target)hit.hitInfo).root))event.setCanceled(true);
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event){DIGS.remove(event.player.getUniqueID());}
    private ArmPayloadHarvest(){}
}
