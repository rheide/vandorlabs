package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

/** Native chest-window actions mutate the carried owner, never a detached or parked copy. */
public final class VehicleChestRuntimeChecks {
    private static int assertions;
    private static void require(boolean value,String why){assertions++;if(!value)throw new IllegalStateException("Vehicle chest: "+why);}
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        assertions=0;NBTTagList inventory=actor.inventory.writeToNBT(new NBTTagList());ItemStack cursor=actor.inventory.getItemStack().copy();double x=actor.posX,y=actor.posY,z=actor.posZ;float yaw=actor.rotationYaw,pitch=actor.rotationPitch;int cases=0;
        try{
            for(boolean pair:new boolean[]{false,true})for(boolean hover:new boolean[]{false,true})for(float heading:new float[]{0,37,90,270}){
                actor.closeScreen();actor.inventory.clear();actor.inventory.setItemStack(ItemStack.EMPTY);EntityGroundVehicle craft=craft(world,pair);craft.rotationYaw=heading;craft.hover=hover;craft.setPosition(1344,118,300);world.getChunkFromBlockCoords(new BlockPos(craft));world.getChunkFromBlockCoords(new BlockPos(craft.posX-8,118,craft.posZ-8));world.getChunkFromBlockCoords(new BlockPos(craft.posX+8,118,craft.posZ+8));world.spawnEntity(craft);
                try{
                    BlockPos local=new BlockPos(0,1,0);Vec3d center=craft.toWorld(new Vec3d(local).addVector(.5,.5,.5));Vec3d eye=craft.toWorld(new Vec3d(local).addVector(.5,.5,-2));Vec3d delta=center.subtract(eye);float look=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));actor.connection.setPlayerLocation(eye.x,eye.y-actor.getEyeHeight(),eye.z,look,0);
                    VehicleInteraction.Target target=VehicleInteraction.pointed(actor);require(target!=null&&target.craft==craft&&target.hit.action==VehicleSelection.Action.CHEST,"ordinary surface ray selects chest at yaw "+heading);require(VehicleInteraction.supported(actor,target)&&VehicleInteraction.activate(actor,target),"normal right click opens a carried chest");require(actor.openContainer instanceof ContainerChest,"native chest container");ContainerChest container=(ContainerChest)actor.openContainer;IInventory lower=container.getLowerChestInventory();require(lower.getSizeInventory()==(pair?54:27)&&container.canInteractWith(actor),"native single/double rows use physical reach in "+(hover?"Hover":"Taxi"));
                    actor.inventory.setInventorySlotContents(0,new ItemStack(Items.DIAMOND,5));container.transferStackInSlot(actor,lower.getSizeInventory()+27);require(actor.inventory.getStackInSlot(0).isEmpty(),"native shift insert moves actual player stack");require(total(lower,Items.DIAMOND)==5,"inserted items live in carried inventory");
                    container.slotClick(0,0,ClickType.PICKUP,actor);require(actor.inventory.getItemStack().getItem()==Items.EMERALD&&actor.inventory.getItemStack().getCount()==7,"native pickup removes saved named cargo");container.slotClick(lower.getSizeInventory()+28,0,ClickType.PICKUP,actor);require(actor.inventory.getItemStack().isEmpty()&&actor.inventory.getStackInSlot(1).getCount()==7,"native putdown transfers cargo into player inventory");
                    craft.signals.persist();VehicleStructure saved=VehicleStructure.decode(craft.structure.encode());VehicleWorld reloaded=new VehicleWorld(world,saved);TileEntityChest copy=(TileEntityChest)reloaded.getTileEntity(local);require(copy.getStackInSlot(0).isEmpty()&&saved.at(local).tileData().getString("CustomName").equals("Cargo bay"),"snapshot/reload preserves removal and custom chest name");int diamonds=0;for(net.minecraft.tileentity.TileEntity tile:reloaded.tiles.values())if(tile instanceof TileEntityChest)diamonds+=total((TileEntityChest)tile,Items.DIAMOND);require(diamonds==5,"snapshot/reload preserves inserted items exactly once");
                    NBTTagCompound longer=copy.writeToNBT(new NBTTagCompound());longer.setString("CustomName","Cargo bay with extended inventory label");Map<BlockPos,NBTTagCompound> one=new HashMap<>();one.put(local,longer);
                    int predicted=saved.lengthWithTiles(one);List<VehicleStructure.Cell> changedCells=new ArrayList<>();for(VehicleStructure.Cell cell:saved.cells)changedCells.add(cell.pos.equals(local)?new VehicleStructure.Cell(local,cell.state,longer):cell);
                    VehicleStructure changed=new VehicleStructure(changedCells,saved.seat,saved.facing,saved.gearCount);require(predicted==changed.encode().length,"changed chest tile byte count equals complete serialized vehicle");changed.inheritEncodedLength(saved);require(changed.lengthWithTiles(Collections.emptyMap())==predicted,"cached size follows an immutable chest update");
                    actor.closeScreen();require(VehicleChests.inventory(actor,craft,local)!=null,"inventory reopens after edits");actor.connection.setPlayerLocation(center.x+20,center.y,center.z,0,0);require(!lower.isUsableByPlayer(actor)&&VehicleChests.inventory(actor,craft,local)==null,"moving out of actual reach denies stale window");actor.connection.setPlayerLocation(eye.x,eye.y-actor.getEyeHeight(),eye.z,look,0);
                    VehicleChests.open(actor,craft,local);require(actor.openContainer instanceof ContainerChest,"reopen for transfer guard");Container stale=actor.openContainer;craft.setTransferInProgress(true);require(actor.openContainer==actor.inventoryContainer&&!stale.canInteractWith(actor),"ownership transfer closes native window before mutation");craft.setTransferInProgress(false);
                    TileEntityChest live=(TileEntityChest)craft.view.getTileEntity(local);live.setLockCode(new LockCode("Dock key"));if(pair)((TileEntityChest)craft.view.getTileEntity(local.east())).setLockCode(new LockCode("Dock key"));actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);VehicleChests.open(actor,craft,local);require(actor.openContainer==actor.inventoryContainer,"native chest lock denies unmatched held item");ItemStack key=new ItemStack(Items.STICK);key.setStackDisplayName("Dock key");actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,key);VehicleChests.open(actor,craft,local);require(actor.openContainer instanceof ContainerChest,"native named key unlocks carried chest");actor.closeScreen();
                    for(BlockPos head:new BlockPos[]{new BlockPos(0,0,0),new BlockPos(1,0,0)}){
                        double height=com.vandorlabs.coupler.BlockCoupler.couplingHeight(craft.view.getBlockState(head));Vec3d approach=craft.toWorld(new Vec3d(head).addVector(.5,height,-2)),aim=craft.toWorld(new Vec3d(head).addVector(.5,height,.5));Vec3d facing=aim.subtract(approach);float bearing=(float)Math.toDegrees(Math.atan2(-facing.x,facing.z));actor.connection.setPlayerLocation(approach.x,approach.y-actor.getEyeHeight(),approach.z,bearing,0);
                        for(boolean configurizer:new boolean[]{false,true}){actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,configurizer?new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER):ItemStack.EMPTY);VehicleInteraction.Target coupler=VehicleInteraction.pointed(actor);require(coupler!=null&&coupler.hit.action==VehicleSelection.Action.COUPLER&&coupler.hit.cell.pos.equals(head),"carried full/slab coupler ordinary surface ray");require(VehicleInteraction.activate(actor,coupler)&&actor.openContainer instanceof com.vandorlabs.coupler.ContainerCoupler,"ordinary and Configurizer open the same carried coupler dialog");com.vandorlabs.coupler.ContainerCoupler menu=(com.vandorlabs.coupler.ContainerCoupler)actor.openContainer;int packed=head.getX()|(head.getY()<<6)|(head.getZ()<<12);com.vandorlabs.coupler.ContainerCoupler resolved=com.vandorlabs.coupler.ContainerCoupler.resolve(actor,world,craft.getEntityId(),packed,Integer.MIN_VALUE);require(resolved!=null&&resolved.tile==menu.tile,"packed carried dialog resolves exact current tile");actor.closeScreen();}
                    }
                    cases++;
                }finally{actor.closeScreen();craft.setDead();VehicleLookup.remove(craft);}
            }
        }finally{actor.closeScreen();actor.inventory.readFromNBT(inventory);actor.inventory.setItemStack(cursor);actor.connection.setPlayerLocation(x,y,z,yaw,pitch);}
        System.out.println("[vandorlabs][reprolab] vehicle-chests PASS cases="+cases+" assertions="+assertions+" actual ray/rightclick native27/54slot windows Taxi/Hover arbitrary yaw pickup/shiftinsert saved inventories locks physical reach transfer closure");
    }
    private static int total(IInventory inventory,net.minecraft.item.Item item){int result=0;for(int slot=0;slot<inventory.getSizeInventory();slot++){ItemStack stack=inventory.getStackInSlot(slot);if(stack.getItem()==item)result+=stack.getCount();}return result;}
    private static EntityGroundVehicle craft(WorldServer world,boolean pair){
        List<VehicleStructure.Cell> cells=new ArrayList<>();BlockPos seat=new BlockPos(2,0,1);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new VehicleStructure.Cell(seat.up(),ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockPilotSeat.UPPER,true),null));
        Block control=Block.getBlockFromName("vandorlabs:vehicle_controller");net.minecraft.tileentity.TileEntity tile=control.createTileEntity(world,control.getDefaultState());tile.setPos(new BlockPos(2,0,0));cells.add(new VehicleStructure.Cell(tile.getPos(),control.getDefaultState(),tile.writeToNBT(new NBTTagCompound())));
        for(int i=0;i<(pair?2:1);i++){TileEntityChest chest=new TileEntityChest();chest.setPos(new BlockPos(i,1,0));chest.setCustomName("Cargo bay");if(i==0){ItemStack cargo=new ItemStack(Items.EMERALD,7);cargo.setStackDisplayName("Original cargo");chest.setInventorySlotContents(0,cargo);}cells.add(new VehicleStructure.Cell(chest.getPos(),Blocks.CHEST.getDefaultState(),chest.writeToNBT(new NBTTagCompound())));}
        for(int i=0;i<2;i++){Block coupler=Block.getBlockFromName(i==0?"vandorlabs:square_coupler":"vandorlabs:vehicle_coupler_slab");net.minecraft.tileentity.TileEntity member=coupler.createTileEntity(world,coupler.getDefaultState());member.setPos(new BlockPos(i,0,0));cells.add(new VehicleStructure.Cell(member.getPos(),coupler.getDefaultState(),member.writeToNBT(new NBTTagCompound())));}
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,seat,EnumFacing.NORTH,3));return craft;
    }
    private VehicleChestRuntimeChecks(){}
}
