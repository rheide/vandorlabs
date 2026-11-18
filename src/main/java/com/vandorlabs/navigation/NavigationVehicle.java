package com.vandorlabs.navigation;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.tiles.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Physical navigation addresses over read-only vehicle/arm views; never creates native chunks. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs")
public final class NavigationVehicle {
    private NavigationVehicle(){}
    private static final Map<World,Set<EntityGroundVehicle>> VEHICLES=Collections.synchronizedMap(new HashMap<>());
    private static final Map<VehicleWorld,Index> INDEX=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Index {
        final int revision;final List<BlockPos> sources=new ArrayList<>(),arms=new ArrayList<>(),tables=new ArrayList<>();
        Index(VehicleWorld world){revision=world.tileRevision;for(Map.Entry<BlockPos,TileEntity> entry:world.tiles.entrySet()){TileEntity tile=entry.getValue();if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer())sources.add(entry.getKey());if(tile instanceof GearTileEntity)arms.add(entry.getKey());if(tile instanceof TileEntityNavigationTable)tables.add(entry.getKey());}}
    }
    private static Index index(VehicleWorld world){Index found=INDEX.get(world);if(found==null||found.revision!=world.tileRevision){found=new Index(world);INDEX.put(world,found);}return found;}
    // Discovery must not retain parked/destroyed craft or duplicate the server index on clients.
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinWorldEvent event){if(!event.getWorld().isRemote&&event.getEntity() instanceof EntityGroundVehicle)VEHICLES.computeIfAbsent(event.getWorld(),w->Collections.newSetFromMap(new WeakHashMap<>())).add((EntityGroundVehicle)event.getEntity());}
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){VEHICLES.remove(event.getWorld());INDEX.clear();}
    static Collection<EntityGroundVehicle> vehicles(World world){Set<EntityGroundVehicle> set=VEHICLES.get(world);if(set==null)return Collections.emptyList();set.removeIf(c->c.isDead||c.world!=world||c.world.getEntityByID(c.getEntityId())!=c);return set;}
    public static boolean live(EntityGroundVehicle craft){return craft!=null&&!craft.isDead&&craft.structure!=null&&craft.view!=null&&!craft.ownershipBlocked&&!craft.transferInProgress;}
    public static World physicalWorld(World world){for(int depth=0;world instanceof VehicleWorld&&depth<10;depth++)world=((VehicleWorld)world).parent;return world;}
    public static EntityGroundVehicle craft(World world){for(int depth=0;world instanceof VehicleWorld&&depth<10;depth++){VehicleWorld view=(VehicleWorld)world;if(view.craft!=null)return view.craft;world=view.parent;}return null;}
    public static Vec3d point(World world,Vec3d point,BlockPos owner){
        for(int depth=0;world instanceof VehicleWorld&&depth<10;depth++){
            VehicleWorld view=(VehicleWorld)world;
            if(view.craft!=null)return view.craft.toWorld(point,owner);
            GearTileEntity arm=view.navigationArm;
            if(arm!=null&&arm.payload()!=null){point=arm.payload().transform(arm,arm.progress(),point.x,point.y,point.z).add(new Vec3d(arm.getPos()));owner=arm.getPos();world=arm.getWorld();}
            else{point=point.add(new Vec3d(view.origin));owner=new BlockPos(point);world=view.parent;}
        }return point;
    }
    public static Vec3d center(EntityGroundVehicle craft){return craft.toWorld(new Vec3d(craft.structure.bounds.maxX/2,craft.structure.bounds.maxY/2,craft.structure.bounds.maxZ/2));}
    public static boolean hasObserver(EntityGroundVehicle craft){return live(craft)&&hasObserver(craft.view,0);}
    private static boolean hasObserver(VehicleWorld world,int depth){if(depth>8)return false;Index index=index(world);for(int i=0;i<index.sources.size();i++){TileEntity tile=world.getTileEntity(index.sources.get(i));if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).formed())return true;}for(int i=0;i<index.arms.size();i++){TileEntity tile=world.getTileEntity(index.arms.get(i));if(tile instanceof GearTileEntity){GearTileEntity arm=(GearTileEntity)tile;if(arm.payload()!=null&&!arm.payload().materialized()&&!arm.hasFailedArmSnapshot()&&armHasObserver(arm,depth+1))return true;}}return false;}
    private static boolean armHasObserver(GearTileEntity arm,int depth){
        try{return hasObserver(arm.payload().view(arm),depth);}
        catch(RuntimeException|LinkageError unavailable){arm.quarantinePayload();return false;}
    }
    static void observers(List<NavigationSources.Area> result,EntityGroundVehicle craft,Vec3d display,com.vandorlabs.redstone.ChannelList channels){if(live(craft))observers(result,craft.view,display,channels,0);}
    private static void observers(List<NavigationSources.Area> result,VehicleWorld world,Vec3d display,com.vandorlabs.redstone.ChannelList channels,int depth){if(depth>8)return;Index index=index(world);for(int i=0;i<index.sources.size();i++){TileEntity tile=world.getTileEntity(index.sources.get(i));if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).formed())NavigationSources.append(result,(TileEntityConstructor)tile,display,channels);}for(int i=0;i<index.arms.size();i++){TileEntity tile=world.getTileEntity(index.arms.get(i));if(tile instanceof GearTileEntity){GearTileEntity arm=(GearTileEntity)tile;if(arm.payload()!=null&&!arm.payload().materialized()&&!arm.hasFailedArmSnapshot())armObservers(result,arm,display,channels,depth+1);}}}
    private static void armObservers(List<NavigationSources.Area> result,GearTileEntity arm,Vec3d display,com.vandorlabs.redstone.ChannelList channels,int depth){
        int first=result.size();
        try{observers(result,arm.payload().view(arm),display,channels,depth);}
        catch(RuntimeException|LinkageError unavailable){result.subList(first,result.size()).clear();arm.quarantinePayload();}
    }
    public static boolean carried(TileEntity tile){return tile!=null&&tile.getWorld() instanceof VehicleWorld&&((VehicleWorld)tile.getWorld()).craft!=null;}
    public static boolean usable(EntityPlayer player,TileEntity tile){
        if(!carried(tile)||tile.isInvalid())return false;VehicleWorld view=(VehicleWorld)tile.getWorld();EntityGroundVehicle craft=view.craft;
        if(!live(craft)||craft.view!=view||craft.world!=player.world||view.getTileEntity(tile.getPos())!=tile||player.isDead||player.isSpectator())return false;
        if(tile instanceof TileEntityNavigationTable){Vec3d local=craft.toLocal(player.getPositionVector(),tile.getPos());AxisAlignedBB box=((TileEntityNavigationTable)tile).navigationBounds();double dx=Math.max(box.minX-local.x,Math.max(0,local.x-box.maxX)),dy=Math.max(box.minY-local.y,Math.max(0,local.y-box.maxY)),dz=Math.max(box.minZ-local.z,Math.max(0,local.z-box.maxZ));return dx*dx+dy*dy+dz*dz<=49;}
        Vec3d p=point(view,new Vec3d(tile.getPos()).addVector(.5,.5,.5),tile.getPos());return player.getDistanceSq(p.x,p.y,p.z)<=64;
    }
    public static boolean edit(EntityPlayer player,TileEntity tile){if(!usable(player,tile))return false;Vec3d point=point(tile.getWorld(),new Vec3d(tile.getPos()).addVector(.5,.5,.5),tile.getPos());BlockPos physical=new BlockPos(point);return player.canPlayerEdit(physical,net.minecraft.util.EnumFacing.UP,player.getHeldItemMainhand())&&player.world.isBlockModifiable(player,physical)&&!ArmTransactions.locked(player.world,physical);}
    public static boolean tableUse(TileEntityNavigationTable tile,EntityPlayer player,Vec3d start,Vec3d end){
        if(NavigationModuleSlot.activate(tile,player,net.minecraft.util.EnumHand.MAIN_HAND,start,end))return true;
        TileEntityNavigationTable reader=tile.reader();com.vandorlabs.blocks.MachineMountSurface mount=reader==null?null:reader.mount();
        if(mount!=null&&mount.hit(start,end)&&!player.isSneaking()&&player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER){
            if(physicalWorld(tile.getWorld()).isRemote)return true;if(!edit(player,reader))return true;net.minecraft.item.ItemStack held=player.getHeldItemMainhand();
            if(held.isEmpty()){net.minecraft.item.ItemStack removed=reader.removeStackFromSlot(0);if(!player.inventory.addItemStackToInventory(removed)&&!removed.isEmpty())player.dropItem(removed,false);}
            else if(reader.isEmpty()&&reader.authorized(held)){net.minecraft.item.ItemStack copy=held.copy();copy.setCount(1);reader.setInventorySlotContents(0,copy);if(net.minecraft.item.ItemStack.areItemStacksEqual(copy,reader.getStackInSlot(0))&&!player.capabilities.isCreativeMode)held.shrink(1);}else return false;
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();return true;
        }
        return NavigationInteractions.table(tile,player,net.minecraft.util.EnumHand.MAIN_HAND,start,end);
    }
    public static void predict(EntityPlayer player,EntityGroundVehicle craft,BlockPos at){if(!player.world.isRemote||!live(craft))return;TileEntity raw=craft.view.getTileEntity(at);double reach=Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());Vec3d start=craft.toLocal(player.getPositionEyes(1),at),end=craft.toLocal(player.getPositionEyes(1).add(player.getLook(1).scale(reach)),at);if(raw instanceof TileEntityNavigationTable)tableUse((TileEntityNavigationTable)raw,player,start,end);else if(raw instanceof TileEntityAnimatedScreenSelector){TileEntityAnimatedScreenSelector screen=(TileEntityAnimatedScreenSelector)raw;Vec3d local=new Vec3d(at);if(!com.vandorlabs.blocks.ScreenChipSupport.activate(screen,player,net.minecraft.util.EnumHand.MAIN_HAND,start.subtract(local),end.subtract(local)))NavigationInteractions.screen(screen,player,net.minecraft.util.EnumHand.MAIN_HAND,start.subtract(local),end.subtract(local));}}
    /** Module coverage is owned and persisted by the carried table, even with no screen viewers. */
    public static void tick(EntityGroundVehicle craft){if(!live(craft)||craft.world.isRemote||craft.world.getTotalWorldTime()%20!=0)return;Index index=index(craft.view);for(int i=0;i<index.tables.size();i++){TileEntity raw=craft.view.getTileEntity(index.tables.get(i));if(raw instanceof TileEntityNavigationTable){TileEntityNavigationTable table=(TileEntityNavigationTable)raw;if(table.ownsReader()&&NavigationService.selected(table,0))NavigationService.recordSources(table);}}}
}
