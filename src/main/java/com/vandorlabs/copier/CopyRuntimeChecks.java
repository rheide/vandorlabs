package com.vandorlabs.copier;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Disposable-world economic and crash-boundary checks for the actual queued copy path. */
public final class CopyRuntimeChecks {
    private static int assertions;
    private static void require(boolean yes,String why){assertions++;if(!yes)throw new IllegalStateException("Copy Controller: "+why);}
    private static Block block(String id){return Block.getBlockFromName("vandorlabs:"+id);}
    private static final class Fixture {
        final WorldServer world;final EntityPlayerMP actor;final BlockPos base;final EnumFacing front,right;final CopyControllerTile tile;
        Fixture(WorldServer world,EntityPlayerMP actor,BlockPos base,EnumFacing front,int size)throws Exception{
            this.world=world;this.actor=actor;this.base=base;this.front=front;right=front.rotateYCCW();
            clear(world,base);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunkFromChunkCoords((base.getX()>>4)+x,(base.getZ()>>4)+z);
            actor.setPositionAndUpdate(base.getX()+front.getFrontOffsetX()*5+.5,base.getY()+2,base.getZ()+front.getFrontOffsetZ()*5+.5);
            world.setBlockState(base,block("copy_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,front),2);
            for(int i=0;i<size;i++)for(int j=0;j<size;j++)for(int side:new int[]{-1,1})world.setBlockState(base.offset(right,side*(i+1)).offset(front,j),block("turntable_surface").getDefaultState().withProperty(BlockTurntableSurface.FACING,EnumFacing.UP),2);
            DeferredTileLoad.flush(world);tile=(CopyControllerTile)world.getTileEntity(base);tile.actor=actor.getUniqueID();tile.settings(1,0,0,ChannelList.EMPTY);tile.refreshPads(actor);
            require(tile.input.size==size&&tile.output.size==size,"paired square claims "+front+size);
            EnumFacing playerLook=front.getOpposite();require(tile.input.cells.contains(base.offset(playerLook.rotateYCCW()))&&tile.output.cells.contains(base.offset(playerLook.rotateY())),"input is on player left, output player right "+front);require(tile.chest().equals(base.offset(playerLook)),"chest is behind controller away from player "+front);
        }
        BlockPos source(){return tile.input.cell(0,0,1);}
        BlockPos target(){return tile.output.cell(0,0,1);}
        TileEntityChest supply(ItemStack... stacks){world.setBlockState(tile.chest(),Blocks.CHEST.getDefaultState(),2);TileEntityChest chest=(TileEntityChest)world.getTileEntity(tile.chest());for(int i=0;i<stacks.length;i++)chest.setInventorySlotContents(i,stacks[i].copy());return chest;}
        CopyPlan plan()throws Exception{return CopyPlan.prepare(tile,actor);}
        void copy()throws Exception{tile.requestCopy(actor);CopyTransactions.drainForChecks(world);}
        void finish()throws Exception{recover(world);require(!tile.busy()&&CopyTransactions.pendingCount(world)==0,"journal retired after verified recovery");}
    }
    public static void run(WorldServer world,EntityPlayerMP actor)throws Exception{
        assertions=0;BlockPos base=new BlockPos(704,110,200);boolean creative=actor.capabilities.isCreativeMode,saving=world.disableLevelSaving;double x=actor.posX,y=actor.posY,z=actor.posZ;
        try{
            actor.capabilities.isCreativeMode=true;
            for(EnumFacing facing:EnumFacing.HORIZONTALS){Fixture f=new Fixture(world,actor,base,facing,2);world.setBlockState(f.source(),Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,BlockStairs.EnumHalf.TOP),2);CopyPlan plan=f.plan();f.copy();require(plan.result.matches(world,plan.targetOrigin)&&plan.original.matches(world,plan.sourceOrigin),"actual Creative queued copy without material chest preserves state "+facing);}
            exactContents(world,actor,base);authorization(world,actor,base);faults(world,actor,base);
            require(CopyTransactions.pendingCount(world)==0,"no pending economic ownership at fixture end");
        }finally{CopyTransactions.fault=null;world.disableLevelSaving=saving;actor.capabilities.isCreativeMode=creative;actor.setPositionAndUpdate(x,y,z);if(CopyTransactions.pendingCount(world)==0)clear(world,base);}
        System.out.println("[vandorlabs][reprolab] copy-controller PASS assertions="+assertions+" exact Survival contents rollback durable journals restart proxies hopper checkpoint/publish faults");
    }
    private static void authorization(WorldServer world,EntityPlayerMP actor,BlockPos base)throws Exception{
        actor.capabilities.isCreativeMode=true;Fixture f=new Fixture(world,actor,base,EnumFacing.NORTH,1);
        world.setBlockState(f.source(),block("copy_controller").getDefaultState(),2);
        CopyControllerTile source=(CopyControllerTile)world.getTileEntity(f.source());UUID victim=UUID.randomUUID();source.actor=victim;
        NBTTagCompound exact=source.writeToNBT(new NBTTagCompound());
        require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(source)==null,"portable victim operator has no world authority");
        f.copy();CopyControllerTile copied=(CopyControllerTile)world.getTileEntity(f.target());
        require(copied!=null&&victim.equals(copied.actor),"actual queued copy retains victim operator configuration exactly");
        require(actor.getUniqueID().equals(com.vandorlabs.tiles.WorldActionAuthorization.actorId(copied)),"actual copier actor receives output authority instead of copied victim");
        require(com.vandorlabs.tiles.WorldActionAuthorization.actor(copied)==actor,"automation resolves actual copier actor");
        NBTTagCompound chunkProof=new NBTTagCompound();com.vandorlabs.tiles.WorldActionAuthorization.saving(new net.minecraftforge.event.world.ChunkDataEvent.Save(world.getChunkFromBlockCoords(f.target()),chunkProof));
        copied.onChunkUnload();require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(copied)==null,"unloaded tile has no active runtime binding");
        com.vandorlabs.tiles.WorldActionAuthorization.loaded(new net.minecraftforge.event.world.ChunkDataEvent.Load(world.getChunkFromBlockCoords(f.target()),chunkProof));
        DeferredTileLoad.flush(world);require(actor.getUniqueID().equals(com.vandorlabs.tiles.WorldActionAuthorization.actorId(copied)),"trusted chunk-data load restores saved position receipt after deferred identity check");
        com.vandorlabs.tiles.WorldActionAuthorization.Data stored=new com.vandorlabs.tiles.WorldActionAuthorization.Data("probe");
        NBTTagCompound persisted=((com.vandorlabs.tiles.WorldActionAuthorization.Data)world.getPerWorldStorage().getOrLoadData(com.vandorlabs.tiles.WorldActionAuthorization.Data.class,"vandorlabs_action_authorization_"+world.provider.getDimension())).writeToNBT(new NBTTagCompound());
        stored.readFromNBT(persisted);require(persisted.equals(stored.writeToNBT(new NBTTagCompound())),"world receipts serialize actor nonce position and type independently of tile NBT");
        exact.setInteger("x",f.target().getX());exact.setInteger("y",f.target().getY());exact.setInteger("z",f.target().getZ());copied.onChunkUnload();com.vandorlabs.tiles.WorldActionAuthorization.loaded(new net.minecraftforge.event.world.ChunkDataEvent.Load(world.getChunkFromBlockCoords(f.target()),chunkProof));copied.readFromNBT(exact);DeferredTileLoad.flush(world);require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(copied)==null,"in-place NBT paste cannot retain position authority");
        com.vandorlabs.tiles.WorldActionAuthorization.authorize(copied,actor);
        world.setBlockToAir(f.target());world.setBlockState(f.target(),block("copy_controller").getDefaultState(),2);
        CopyControllerTile replacement=(CopyControllerTile)world.getTileEntity(f.target());replacement.readFromNBT(exact);
        require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(replacement)==null,"same-position replacement does not inherit prior receipt");
        com.vandorlabs.tiles.WorldActionAuthorization.loaded(new net.minecraftforge.event.world.ChunkDataEvent.Load(world.getChunkFromBlockCoords(f.target()),chunkProof));DeferredTileLoad.flush(world);
        require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(replacement)==null,"stale chunk nonce cannot revive revoked receipt after replacement");
        com.vandorlabs.tiles.WorldActionAuthorization.authorize(replacement,actor);replacement.onChunkUnload();
        com.vandorlabs.tiles.WorldActionAuthorization.loaded(new net.minecraftforge.event.world.ChunkDataEvent.Load(world.getChunkFromBlockCoords(f.target()),chunkProof));DeferredTileLoad.flush(world);
        require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(replacement)==null,"old chunk proof mismatching fresh world receipt fails closed");
        for(String[] kind:new String[][]{{"vehicle_controller","VehicleOwner"},{"pilot_seat","VehicleOwner"},{"autopilot","AutopilotDepartureOwner"},{"programmable_ramp","Owner"}}){
            f=new Fixture(world,actor,base,EnumFacing.NORTH,1);if(kind[0].equals("pilot_seat"))f.tile.height=2;world.setBlockState(f.source(),block(kind[0]).getDefaultState(),2);if(kind[0].equals("pilot_seat"))block(kind[0]).onBlockPlacedBy(world,f.source(),world.getBlockState(f.source()),actor,new ItemStack(block(kind[0])));TileEntity raw=world.getTileEntity(f.source());NBTTagCompound config=raw.writeToNBT(new NBTTagCompound());config.setUniqueId(kind[1],victim);raw.readFromNBT(config);
            require(com.vandorlabs.tiles.WorldActionAuthorization.actorId(raw)==null,"copied owner field grants no authority: "+kind[0]);
            if(kind[0].equals("vehicle_controller")||kind[0].equals("programmable_ramp")){
                CopyPlan prepared=f.plan();require(prepared.original.matchesExact(world,prepared.sourceOrigin),"unchanged raw controller snapshot matches exactly: "+kind[0]);NBTTagCompound stable=raw.writeToNBT(new NBTTagCompound()),changed=stable.copy();
                if(kind[0].equals("programmable_ramp")){changed.setLong(com.vandorlabs.persistence.SaveSchema.Ramp.LAST_STEP_TICK,stable.getLong(com.vandorlabs.persistence.SaveSchema.Ramp.LAST_STEP_TICK)+1);changed.setString(com.vandorlabs.persistence.SaveSchema.Ramp.STATUS,"Changed after exact capture");}
                else changed.setBoolean("VehicleSignalHigh",!stable.getBoolean("VehicleSignalHigh"));
                raw.readFromNBT(changed);require(!prepared.original.matchesExact(world,prepared.sourceOrigin),"changed transient controller data invalidates full copy: "+kind[0]);boolean refused=false;try{prepared.validate(world,actor);}catch(IllegalArgumentException expected){refused=expected.getMessage().contains("Input blocks");}require(refused,"preflight rejects changed transient data before mutation");raw.readFromNBT(stable);require(prepared.original.matchesExact(world,prepared.sourceOrigin),"restoring complete original data matches again");
            }
            f.copy();require(f.tile.error.isEmpty(),"operator fixture copy completes: "+kind[0]+" "+f.tile.error);TileEntity result=world.getTileEntity(f.target());require(result!=null&&victim.equals(result.writeToNBT(new NBTTagCompound()).getUniqueId(kind[1])),"full operator configuration retained: "+kind[0]);
            require(actor.getUniqueID().equals(com.vandorlabs.tiles.WorldActionAuthorization.actorId(result)),"actual copying actor grants receipt: "+kind[0]);
        }
        System.out.println("[vandorlabs][reprolab] world-action-authorization PASS copied victim configuration uses actual copier authority; paste/replacement revoke");
    }
    private static ItemStack nested(){ItemStack stack=new ItemStack(Item.getItemFromBlock(Blocks.PURPLE_SHULKER_BOX));NBTTagCompound carrier=new NBTTagCompound(),item=new ItemStack(Items.DIAMOND,3).writeToNBT(new NBTTagCompound());item.setByte("Slot",(byte)0);NBTTagList items=new NBTTagList();items.appendTag(item);carrier.setTag("Items",items);carrier.setString("CustomName","Paid nested cargo");stack.setTagInfo("BlockEntityTag",carrier);return stack;}
    private static void exactContents(WorldServer world,EntityPlayerMP actor,BlockPos base)throws Exception{
        actor.capabilities.isCreativeMode=false;Fixture f=new Fixture(world,actor,base,EnumFacing.NORTH,1);world.setBlockState(f.source(),Blocks.CHEST.getDefaultState(),2);TileEntityChest source=(TileEntityChest)world.getTileEntity(f.source());source.setCustomName("Exact source chest");ItemStack nested=nested(),named=new ItemStack(Items.PAPER,2);named.setStackDisplayName("Exact custom cargo");source.setInventorySlotContents(0,nested);source.setInventorySlotContents(1,named);
        TileEntityChest supply=f.supply(new ItemStack(Blocks.CHEST),nested,named);CopyPlan plan=f.plan();require(plan.materials.stream().filter(n->n.exact).count()==2,"filled carrier paid as exact item, nested contents not charged twice");f.copy();require(plan.original.matches(world,plan.sourceOrigin)&&plan.result.matches(world,plan.targetOrigin),"source preserved and all inventory/name NBT copied");require(((IInventory)world.getTileEntity(f.tile.chest())).isEmpty(),"building item and exact stored contents withdrawn once");require(supply.isEmpty(),"stale original chest reference cannot extract withdrawn cargo");
        clear(world,base);f=new Fixture(world,actor,base,EnumFacing.NORTH,1);world.setBlockState(f.source(),Blocks.CHEST.getDefaultState(),2);((TileEntityChest)world.getTileEntity(f.source())).setInventorySlotContents(0,named);supply=f.supply(new ItemStack(Blocks.CHEST),new ItemStack(Items.PAPER,2));NBTTagCompound before=supply.writeToNBT(new NBTTagCompound());f.copy();require(f.tile.error.startsWith("Missing material:")&&world.isAirBlock(f.target())&&before.equals(supply.writeToNBT(new NBTTagCompound())),"first missing exact cargo reports error without mutation");supply.setInventorySlotContents(1,named.copy());f.copy();require(f.tile.error.isEmpty()&&world.getTileEntity(f.target()) instanceof TileEntityChest,"successful retry clears authoritative missing-material error");
    }
    private static void faults(WorldServer world,EntityPlayerMP actor,BlockPos base)throws Exception{
        actor.capabilities.isCreativeMode=false;
        BlockPos control=base.up(4);world.setBlockState(control,Blocks.CHEST.getDefaultState(),2);((TileEntityChest)world.getTileEntity(control)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND));EntityMinecartHopper nativeCart=new EntityMinecartHopper(world,control.getX()+.5,control.getY()-1,control.getZ()+.5);nativeCart.captureDroppedItems();require(!nativeCart.isEmpty(),"hopper coordinate positive control extracts from an ordinary chest");nativeCart.setDead();world.removeTileEntity(control);world.setBlockToAir(control);
        for(String stage:new String[]{"withdraw","install"}){Fixture f=chestFixture(world,actor,base);CopyPlan plan=f.plan();CopyTransactions.fault=s->{if(stage.equals(s))throw new IllegalStateException("injected "+stage);};f.copy();CopyTransactions.fault=null;require(plan.supply.before.matches(world,plan.supply.origin)&&world.isAirBlock(f.target())&&!f.tile.error.isEmpty(),"same-tick "+stage+" failure refunds exactly once and clears output");f.finish();}
        for(String stage:new String[]{"checkpoint","journal-completion","publish"}){Fixture f=chestFixture(world,actor,base);CopyPlan plan=f.plan();final boolean[] fired={false};CopyTransactions.fault=s->{if(stage.equals(s)&&!fired[0]){fired[0]=true;throw new IllegalStateException("injected "+stage);}};f.copy();CopyTransactions.fault=null;require(fired[0]&&f.tile.busy()&&CopyTransactions.pendingCount(world)==1,"failure at "+stage+" retains durable journal/ownership");require(CopyTransactions.locked(world,f.target())&&CopyTransactions.locked(world,f.tile.chest())&&CopyTransactions.locked(world,base),"pending target supply and controller protected");proxy(world,f.target());proxy(world,f.tile.chest());hopper(world,f.target());hopper(world,f.tile.chest());
            if(stage.equals("checkpoint")){
                world.disableLevelSaving=true;for(int i=0;i<3;i++)CopyTransactions.recover(world);require(CopyTransactions.pendingCount(world)==1&&plan.supply.after.matches(world,plan.supply.origin),"saving disabled never refunds or retires pending ownership");world.disableLevelSaving=false;
                CopyTransactions.unload(world);CopyTransactions.load(world);require(CopyTransactions.locked(world,f.target())&&CopyTransactions.locked(world,f.tile.chest()),"journal region locks rebuilt immediately at WorldLoad before ticks");
                require((f.target().getX()>>4)!=(f.tile.chest().getX()>>4),"restart fixture spans separate target/supply chunks");
                for(boolean targetFirst:new boolean[]{true,false}){
                    CopyTransactions.unload(world);CopyTransactions.load(world);
                    for(BlockPos at:targetFirst?new BlockPos[]{f.target(),f.tile.chest()}:new BlockPos[]{f.tile.chest(),f.target()}){CopyWorld.publish(world,at);CopyTransactions.chunkLoaded(world.getChunkFromBlockCoords(at));proxy(world,at);}
                    require(CopyTransactions.locked(world,f.target())&&CopyTransactions.locked(world,f.tile.chest()),"both chunk arrival orders retain journal locks");
                }
                CopyTransactions.recover(world);world.disableLevelSaving=true;for(int i=0;i<3;i++)CopyTransactions.recover(world);require(CopyTransactions.pendingCount(world)==1&&plan.supply.before.matches(world,plan.supply.origin),"disabled recovery checkpoint retains refunded inventory inert and journal live");proxy(world,f.tile.chest());world.disableLevelSaving=false;
            }
            f.finish();if(stage.equals("publish")){require(plan.result.matches(world,plan.targetOrigin)&&plan.supply.after.matches(world,plan.supply.origin),"post-commit partial publish finishes paid copy without refund");}else require(world.isAirBlock(f.target())&&plan.supply.before.matches(world,plan.supply.origin),"pre-commit checkpoint fault safely rolls back and refunds once");
        }
        Fixture f=chestFixture(world,actor,base);CopyPlan plan=f.plan();CopyTransactions.fault=s->{if(s.equals("checkpoint"))throw new IllegalStateException("pending output mutation");};f.copy();CopyTransactions.fault=null;world.removeTileEntity(f.target());world.setBlockToAir(f.target());CopyTransactions.recover(world);require(CopyTransactions.pendingCount(world)==1&&plan.supply.after.matches(world,plan.supply.origin),"externally missing installed output cannot obtain a refund");CopyWorld.install(world,plan.result,plan.targetOrigin,CopyWorld.prepare(world,plan.result,plan.targetOrigin),true);f.finish();require(plan.supply.before.matches(world,plan.supply.origin),"restoring exact protected data permits safe journal recovery");
    }
    private static Fixture chestFixture(WorldServer world,EntityPlayerMP actor,BlockPos base)throws Exception{Fixture f=new Fixture(world,actor,base,EnumFacing.NORTH,1);world.setBlockState(f.source(),Blocks.CHEST.getDefaultState(),2);((TileEntityChest)world.getTileEntity(f.source())).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));f.supply(new ItemStack(Blocks.CHEST),new ItemStack(Items.DIAMOND,3));return f;}
    private static void proxy(WorldServer world,BlockPos at){TileEntity tile=world.getTileEntity(at);require(tile instanceof CopyFrozenTile&&((IInventory)tile).getSizeInventory()==0,"pending inventory exposes zero native slots "+at);net.minecraftforge.items.IItemHandler handler=tile.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,null);require(handler!=null&&handler.getSlots()==0,"pending Forge capability exposes zero slots");}
    private static void hopper(WorldServer world,BlockPos at){EntityMinecartHopper cart=new EntityMinecartHopper(world,at.getX()+.5,at.getY()-1,at.getZ()+.5);cart.captureDroppedItems();require(cart.isEmpty(),"actual hopper minecart cannot extract pending inventory");cart.setDead();}
    private static void recover(WorldServer world)throws Exception{long deadline=System.nanoTime()+65_000_000_000L;while(CopyTransactions.pendingCount(world)>0&&System.nanoTime()<deadline){CopyTransactions.beforeTick(world);CopyTransactions.recover(world);Thread.sleep(25);}require(CopyTransactions.pendingCount(world)==0,"recovery reaches verified chunk checkpoint");}
    private static void clear(WorldServer world,BlockPos base){for(BlockPos cursor:BlockPos.getAllInBox(base.add(-5,-2,-5),base.add(5,5,5))){BlockPos p=cursor.toImmutable();world.removeTileEntity(p);world.setBlockState(p,Blocks.AIR.getDefaultState(),2);}}
    private CopyRuntimeChecks(){}
}
