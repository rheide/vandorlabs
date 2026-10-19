package com.vandorlabs.gear;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityLandingGear;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Legacy IDs remain readable; loaded integrated wheel gear becomes modular parts when space permits. */
@Mod.EventBusSubscriber(modid=VandorLabs.MODID)
public final class LegacyGearMigration {
    private LegacyGearMigration(){}
    private static boolean legacy(Block block){return block instanceof BlockTelescopicLandingGear&&!(block instanceof BlockNonWheeledLandingGear);}
    private static boolean legacy(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock&&legacy(((ItemBlock)stack.getItem()).getBlock());}
    private static NBTTagCompound settings(int mode,String channels){
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("Trigger",mode);tag.setString("Channels",channels);tag.setInteger("Direction",0);tag.setBoolean("Deployed",true);tag.setBoolean("LegacyAnyChannels",true);return tag;
    }
    /** No source block changes until the complete destination and mount have been checked. */
    public static GearTileEntity convert(TileEntityLandingGear old){
        if(!(old.getWorld() instanceof WorldServer)||old.getWorld() instanceof VehicleWorld||VehicleTransfer.isMutating(old.getWorld())||old.isInvalid())return null;
        WorldServer world=(WorldServer)old.getWorld();BlockPos source=old.getPos();
        if(!world.isBlockLoaded(source,false)||world.getTileEntity(source)!=old||!legacy(world.getBlockState(source).getBlock())||!old.isRoot())return null;
        IBlockState before=world.getBlockState(source);EnumFacing facing=before.getValue(BlockTelescopicLandingGear.FACING);
        boolean deployed=before.getValue(BlockTelescopicLandingGear.EXTENDED)&&old.getExtensionPixels()>0;
        float end=before.getValue(BlockTelescopicLandingGear.EXTENDED)?old.getExtensionPixels()/16F:0;
        if(Math.abs(old.progress-end)>.0001)return null;
        boolean large=old.getSize()>=3;int height=Math.max(1,(old.getExtensionPixels()+15)/16);
        BlockPos root=source;if(large){if(facing==EnumFacing.EAST)root=root.east();else if(facing==EnumFacing.SOUTH)root=root.east().south();else if(facing==EnumFacing.WEST)root=root.south();}
        Set<BlockPos> owned=new LinkedHashSet<>();
        for(BlockPos at:BlockPos.getAllInBox(source.add(-1,-5,-1),source.add(1,0,1))){
            if(!world.isBlockLoaded(at,false))return null;
            TileEntity tile=world.getTileEntity(at);
            if(world.getBlockState(at).getBlock()==before.getBlock()&&tile instanceof TileEntityLandingGear&&(at.equals(source)||source.equals(((TileEntityLandingGear)tile).owner())))owned.add(at.toImmutable());
        }
        GearBlock arm=large?GearRegistry.LARGE_GEAR_EXTENDER:GearRegistry.GEAR_EXTENDER,wheel=large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL;
        Map<BlockPos,IBlockState> desired=new LinkedHashMap<>();int width=large?4:1;
        for(int depth=0;depth<=height;depth++)for(int part=0;part<(depth==height?width:1);part++){
            BlockPos at=root.down(depth).add(large?LargeGearBlock.offset(facing,part&1,part>>1):BlockPos.ORIGIN);
            IBlockState state=(depth==height?wheel:arm).getDefaultState().withProperty(GearBlock.FACING,facing);
            if(large)state=state.withProperty(LargeGearBlock.PART,part);desired.put(at,state);
        }
        for(BlockPos at:desired.keySet())if(!world.isBlockLoaded(at,false)||!world.isAirBlock(at)&&!owned.contains(at))return null;
        for(int part=0;part<1;part++){
            BlockPos mount=root.add(large?LargeGearBlock.offset(facing,part&1,part>>1):BlockPos.ORIGIN).up();
            if(!world.isBlockLoaded(mount,false)||!world.getBlockState(mount).isSideSolid(world,mount,EnumFacing.DOWN))return null;
        }
        NBTTagCompound settings=settings(old.getMode(),old.getRedstoneChannels().toString());
        Set<BlockPos> neighbors=new LinkedHashSet<>();boolean directionSet=false;
        // Joining an already migrated wheel must not change its pose or automation.
        for(BlockPos at:desired.keySet())if(at.getY()==root.getY()-height)for(EnumFacing side:EnumFacing.HORIZONTALS){
            BlockPos neighbor=at.offset(side);if(desired.containsKey(neighbor)||!world.isBlockLoaded(neighbor,false))continue;
            TileEntity tile=world.getTileEntity(neighbor);
            if(tile instanceof GearTileEntity){GearTileEntity group=((GearTileEntity)tile).loadedController();
                if(group==null||!group.usesLegacyInput()||group.isSwingArmController()||group.armSize()!=(large?2:1)||group.footFacing()!=facing||group.assembly().kind()!=com.vandorlabs.gear.core.GearTopology.FootKind.WHEEL||group.assembly().columnHeight()!=height||group.target()!=deployed||Math.abs(group.progress()-(deployed?1:0))>.0001||group.triggerMode!=old.getMode()||!group.getRedstoneChannels().equals(old.getRedstoneChannels()))return null;
                if(directionSet&&settings.getInteger("Direction")!=group.direction().ordinal())return null;settings.setInteger("Direction",group.direction().ordinal());directionSet=true;
                for(BlockPos member:group.members()){if(!world.isBlockLoaded(member,false))return null;neighbors.add(member);}
            }
        }
        Set<BlockPos> affected=new LinkedHashSet<>(owned);affected.addAll(desired.keySet());affected.addAll(neighbors);BlockPos origin=minimum(affected);
        List<VehicleStructure.Cell> saved=new ArrayList<>(),replacement=new ArrayList<>();
        for(BlockPos at:affected){
            TileEntity tile=world.getTileEntity(at);saved.add(new VehicleStructure.Cell(at.subtract(origin),world.getBlockState(at),tile==null?null:VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));
            IBlockState state=desired.containsKey(at)?desired.get(at):owned.contains(at)?net.minecraft.init.Blocks.AIR.getDefaultState():world.getBlockState(at);NBTTagCompound data=!desired.containsKey(at)&&!owned.contains(at)&&tile!=null?VehicleTileData.translated(tile.writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin)):null;
            // Rebuild the entire compatible group atomically, including stowed neighbors.
            if(desired.containsKey(at)||neighbors.contains(at)){GearTileEntity fresh=new GearTileEntity();fresh.setPos(at.subtract(origin));data=fresh.writeToNBT(new NBTTagCompound());}
            replacement.add(new VehicleStructure.Cell(at.subtract(origin),state,data));
        }
        try{
            VehicleTransfer.blocks(world,VehicleStructure.display(replacement),origin,true);
            GearTileEntity result=(GearTileEntity)world.getTileEntity(root);
            if(!result.rebuild())throw new IllegalStateException(result.error());result=result.loadedController();
            result.configure(settings);result.captureProgress(deployed?1:0);result.target(deployed);result.sync();
            com.vandorlabs.tiles.LandingGearCovers.refresh(world,result.getPos());return result;
        }catch(RuntimeException failure){
            try{VehicleTransfer.blocks(world,VehicleStructure.display(saved),origin,true);}catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            VandorLabs.logger.warn("Legacy gear conversion deferred at "+source,failure);return null;
        }
    }
    private static BlockPos minimum(Set<BlockPos> positions){int x=Integer.MAX_VALUE,y=x,z=x;for(BlockPos at:positions){x=Math.min(x,at.getX());y=Math.min(y,at.getY());z=Math.min(z,at.getZ());}return new BlockPos(x,y,z);}
    public static java.util.List<ItemStack> items(ItemStack source){
        NBTTagCompound old=source.getSubCompound("BlockEntityTag");boolean large=old!=null&&old.getInteger("GearSize")>=3;
        int height=old==null?1:Math.max(1,(Math.max(0,Math.min(64,old.getInteger("ExtensionPixels")))+15)/16);
        int mode=old==null||!old.hasKey("RedstoneMode")?1:Math.max(0,Math.min(2,old.getInteger("RedstoneMode")));
        com.vandorlabs.redstone.ChannelList channels=old==null?com.vandorlabs.redstone.ChannelList.EMPTY:com.vandorlabs.redstone.ChannelData.read(old,old.getInteger("RedstoneChannel"));
        java.util.List<ItemStack> parts=new java.util.ArrayList<>();int arms=source.getCount()*height;
        while(arms>0){int count=Math.min(64,arms);ItemStack arm=new ItemStack(large?GearRegistry.LARGE_GEAR_EXTENDER:GearRegistry.GEAR_EXTENDER,count);arm.setTagInfo("ModularGearSettings",settings(mode,channels.toString()));parts.add(arm);arms-=count;}
        parts.add(new ItemStack(large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL,source.getCount()));return parts;
    }
    @SubscribeEvent public static void inventory(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.world.isRemote||event.player.ticksExisted%20!=0||!(event.player instanceof EntityPlayerMP))return;
        EntityPlayerMP player=(EntityPlayerMP)event.player;
        for(int slot=0;slot<player.inventory.getSizeInventory();slot++){
            ItemStack stack=player.inventory.getStackInSlot(slot);if(!legacy(stack))continue;java.util.List<ItemStack> parts=items(stack);
            player.inventory.setInventorySlotContents(slot,parts.get(0));
            net.minecraftforge.items.IItemHandler inventory=new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.inventory);
            for(int i=1;i<parts.size();i++){ItemStack remainder=net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(inventory,parts.get(i),false);if(!remainder.isEmpty())player.dropItem(remainder,false);}
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();
        }
    }
    private static final Map<net.minecraft.world.World,Set<EntityItem>> DROPS=new IdentityHashMap<>();
    @SubscribeEvent public static void dropped(EntityJoinWorldEvent event){
        if(!event.getWorld().isRemote&&event.getEntity() instanceof EntityItem&&legacy(((EntityItem)event.getEntity()).getItem()))DROPS.computeIfAbsent(event.getWorld(),w->Collections.newSetFromMap(new IdentityHashMap<>())).add((EntityItem)event.getEntity());
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){DROPS.remove(event.getWorld());}
    @SubscribeEvent public static void finishDrops(TickEvent.WorldTickEvent event){
        if(!event.world.isRemote&&event.phase==TickEvent.Phase.END)finishDrops((WorldServer)event.world);
    }
    public static void finishDrops(WorldServer world){
        Set<EntityItem> pending=DROPS.remove(world);if(pending==null)return;
        for(EntityItem item:pending)if(!item.isDead&&legacy(item.getItem())&&world.getEntityFromUuid(item.getUniqueID())==item){
            java.util.List<ItemStack> parts=items(item.getItem());NBTTagCompound tag=item.writeToNBT(new NBTTagCompound());List<EntityItem> spawned=new ArrayList<>();boolean complete=true;
            for(int i=1;i<parts.size();i++){EntityItem part=new EntityItem(world);part.readFromNBT(tag);part.setUniqueId(UUID.randomUUID());part.setItem(parts.get(i));if(!world.spawnEntity(part)){complete=false;break;}spawned.add(part);}
            if(complete)item.setItem(parts.get(0));else for(EntityItem part:spawned)part.setDead();
        }
    }
}
