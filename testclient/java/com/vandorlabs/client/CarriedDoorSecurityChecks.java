package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Real carried/native tiles, inventories, snapshot publication and wire payloads; no rendering. */
public final class CarriedDoorSecurityChecks {
    private static final BlockPos DOOR=new BlockPos(2,0,0),SEAT=BlockPos.ORIGIN;
    private static BlockConfigurableSpaceDoor doorBlock;
    private static BlockLargeProgrammableDoor largeBlock;
    private static BlockPilotSeat seatBlock;
    private static sun.misc.Unsafe unsafe;
    private static net.minecraft.server.dedicated.DedicatedServer server;
    private static final Map<UUID,EntityPlayerMP> ONLINE=new HashMap<>();
    private static final List<VehicleNetwork.Packet> PACKETS=new ArrayList<>();
    private static int assertions,scenarios,packetCount,packetBytes;
    private static void need(boolean value,String reason){assertions++;if(!value)throw new AssertionError(reason);}

    private static final class World extends NonRenderingChecks.MemoryWorld {
        final Map<BlockPos,Long> scheduled=new HashMap<>();
        World(boolean remote){super(remote);time=100;}
        @Override public net.minecraft.server.MinecraftServer getMinecraftServer(){return server;}
        @Override public void scheduleUpdate(BlockPos pos,Block block,int ticks){scheduled.merge(pos.toImmutable(),time+ticks,Math::min);}
    }
    private static final class Player extends EntityPlayer {
        boolean spectator;
        final List<ItemStack> drops=new ArrayList<>();
        Player(World world){super(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CarriedDoorChecks"));}
        @Override public boolean isSpectator(){return spectator;}
        @Override public boolean isCreative(){return capabilities.isCreativeMode;}
        @Override public net.minecraft.entity.item.EntityItem dropItem(ItemStack item,boolean ignored){
            if(item.isEmpty())return null;drops.add(item.copy());return new net.minecraft.entity.item.EntityItem(world,posX,posY,posZ,item);
        }
        void emptyHand(){ItemStack stack=getHeldItemMainhand();if(stack.isEmpty())return;setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);for(int i=1;i<inventory.mainInventory.size();i++)if(inventory.mainInventory.get(i).isEmpty()){inventory.mainInventory.set(i,stack);return;}throw new AssertionError("fixture cannot empty hand");}
        int cards(){int count=0;for(ItemStack stack:inventory.mainInventory)if(stack.getItem()==ModItems.ACCESS_CARD)count+=stack.getCount();for(ItemStack stack:inventory.offHandInventory)if(stack.getItem()==ModItems.ACCESS_CARD)count+=stack.getCount();return count;}
    }
    /** Only the online-player lookup is substituted; it uses the actual player's inventory/container. */
    private static final class Players extends net.minecraft.server.dedicated.DedicatedPlayerList {
        private Players(){super(null);}
        @Override public EntityPlayerMP getPlayerByUUID(UUID id){return ONLINE.get(id);}
    }
    private static void connect(Player player)throws Exception{
        EntityPlayerMP online=(EntityPlayerMP)unsafe.allocateInstance(EntityPlayerMP.class);
        online.world=player.world;online.inventory=player.inventory;online.inventoryContainer=player.inventoryContainer;
        ONLINE.put(player.getUniqueID(),online);
    }
    private static ItemStack card(int count,int company){ItemStack item=new ItemStack(ModItems.ACCESS_CARD,count,company);item.setStackDisplayName("Physical credential");NBTTagCompound data=item.getTagCompound();data.setString("FixturePayload","unchanged");return item;}

    private static List<VehicleStructure.Cell> cells(boolean large,boolean paired,int mode){
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(SEAT,seatBlock.getDefaultState(),null));
        int leaves=paired?2:1;
        for(int leaf=0;leaf<leaves;leaf++)for(int col=0;col<(large?3:1);col++)for(int row=0;row<(large?3:2);row++){
            BlockPos root=DOOR.west(leaf),at=root.east(col).up(row);
            TileEntitySpaceDoor tile=large?new TileEntityLargeProgrammableDoor():new TileEntitySpaceDoor();tile.setPos(at);
            if(large)((TileEntityLargeProgrammableDoor)tile).assign(root);
            tile.setPlacementDepth(0);tile.setPanelMode(2);tile.configureSecurity(mode,8);
            IBlockState state=(large?largeBlock:doorBlock).getDefaultState().withProperty(BlockDoor.FACING,EnumFacing.SOUTH)
                    .withProperty(BlockDoor.HALF,row==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER)
                    .withProperty(BlockDoor.HINGE,leaf==0?BlockDoor.EnumHingePosition.LEFT:BlockDoor.EnumHingePosition.RIGHT);
            state=state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state));
            NBTTagCompound data=tile.writeToNBT(new NBTTagCompound());NBTTagCompound opaque=new NBTTagCompound();opaque.setString("provider_fixture","do not replace");data.setTag("ForgeCaps",opaque);
            cells.add(new VehicleStructure.Cell(at,state,data));
        }
        return cells;
    }
    private static final class Fixture {
        final World world;
        final boolean carried,large,paired;
        final Player player;
        final BlockPos offset;
        EntityGroundVehicle craft,mirror;
        Fixture(boolean carried,boolean large,boolean paired,int mode,boolean remote,boolean creative)throws Exception{
            ONLINE.clear();PACKETS.clear();this.carried=carried;this.large=large;this.paired=paired;world=new World(remote);
            List<VehicleStructure.Cell> source=cells(large,paired,mode);offset=carried?BlockPos.ORIGIN:new BlockPos(100,80,100);
            if(carried){craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(source,SEAT,EnumFacing.SOUTH,1));craft.setPosition(100,80,100);}
            else for(VehicleStructure.Cell cell:source)if(cell.hasTileData()){
                BlockPos at=cell.pos.add(offset);world.states.put(at,cell.state);
                NBTTagCompound data=VehicleTileData.translated(cell.tileData(),offset);TileEntity tile=TileEntity.create(world,data);need(tile!=null,"native fixture tile factory");tile.setWorld(world);world.tiles.put(at,tile);
            }
            player=new Player(world);player.setPosition(102.5,80,101);player.capabilities.isCreativeMode=creative;connect(player);
            if(carried&&!remote)mirror();scenarios++;
        }
        net.minecraft.world.World view(){return carried?craft.view:world;}
        BlockPos root(){return DOOR.add(offset);}
        TileEntitySpaceDoor door(){return door(root());}
        TileEntitySpaceDoor door(BlockPos at){return (TileEntitySpaceDoor)view().getTileEntity(at);}
        void use(int side){door().useSecurity(player,EnumHand.MAIN_HAND,side);}
        void mirror(){World remote=new World(true);remote.time=world.time;mirror=new EntityGroundVehicle(remote);mirror.install(VehicleStructure.read(craft.structure.write()));mirror.setPosition(craft.posX,craft.posY,craft.posZ);}
        void wire(){
            if(mirror==null){PACKETS.clear();return;}
            ((World)mirror.world).time=world.time;
            for(VehicleNetwork.Packet packet:new ArrayList<>(PACKETS)){
                need(packet.valid,"published credential wire packet decodes");
                if(packet.type==VehicleNetwork.TILE)VehicleSignals.apply(mirror,packet);
                else if(packet.type==VehicleNetwork.DOORS)need(VehicleDoors.apply(mirror,packet.data),"published door group packet applies");
                else throw new AssertionError("unexpected credential packet "+packet.type);
            }
            PACKETS.clear();
            for(VehicleStructure.Cell cell:craft.structure.cells)if(cell.hasTileData()){
                need(mirror.view.getBlockState(cell.pos)==craft.view.getBlockState(cell.pos),"client OPEN state follows authoritative snapshot");
                need(mirror.structure.at(cell.pos).tileData().equals(cell.tileData()),"client full credential NBT follows snapshot at "+cell.pos);
                need(cell.tileData().getCompoundTag("ForgeCaps").getString("provider_fixture").equals("do not replace"),"opaque capability envelope retained");
            }
        }
        void persist(){if(carried){craft.signals.persist();wire();}}
        void step(long tick){
            if(carried){world.time=tick;craft.signals.tick();}
            else{
                int updates=0;
                while(!world.scheduled.isEmpty()){
                    Map.Entry<BlockPos,Long> next=Collections.min(world.scheduled.entrySet(),Map.Entry.comparingByValue());
                    if(next.getValue()>tick)break;BlockPos at=next.getKey();world.time=next.getValue();world.scheduled.remove(at);
                    need(++updates<2000,"native scheduled updates remain bounded");IBlockState state=world.getBlockState(at);state.getBlock().updateTick(world,at,state,world.rand);
                }
                world.time=tick;
            }
            wire();
        }
        void open(boolean expected){
            for(int leaf=0;leaf<(paired?2:1);leaf++)for(int col=0;col<(large?3:1);col++)for(int row=0;row<(large?3:2);row++)
                need(view().getBlockState(root().west(leaf).east(col).up(row)).getValue(BlockDoor.OPEN)==expected,"OPEN across full door group expected="+expected+" carried="+carried+" large="+large+" tick="+world.time);
        }
        void reload()throws Exception{
            if(carried){
                NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());wire();
                EntityGroundVehicle old=craft;old.signals.close();old.setDead();
                craft=new EntityGroundVehicle(world);craft.readFromNBT(saved);need(craft.structure!=null&&craft.view!=null,"saved vehicle reload retains installed credential");mirror();
            }else{
                world.scheduled.clear();
                for(BlockPos at:new ArrayList<>(world.tiles.keySet())){TileEntity old=world.tiles.get(at);NBTTagCompound saved=old.writeToNBT(new NBTTagCompound());old.invalidate();TileEntity replacement=TileEntity.create(world,saved);replacement.setWorld(world);world.tiles.put(at,replacement);replacement.onLoad();}
                DeferredTileLoad.flush(world);
            }
        }
        void close(){if(craft!=null){craft.signals.close();craft.setDead();}if(mirror!=null)mirror.setDead();com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(new net.minecraftforge.event.world.WorldEvent.Unload(world));ONLINE.clear();PACKETS.clear();}
    }

    private static void reproduction()throws Exception{
        Fixture f=new Fixture(true,false,false,2,false,false);
        try{
            need(f.craft.view.isRemote&&!f.world.isRemote,"remote carried simulation has authoritative server parent");
            ItemStack held=card(2,3);f.player.setHeldItem(EnumHand.MAIN_HAND,held);f.use(0);
            need(held.getCount()==1&&!f.door().keyReader().isEmpty(),"server-carried secured door must borrow exactly one matching credential");
            f.step(100);f.open(true);
        }finally{f.close();}
    }
    private static void modes()throws Exception{
        int checked=0;
        for(boolean carried:new boolean[]{false,true})for(boolean large:new boolean[]{false,true})for(int mode=1;mode<=4;mode++)for(int side=0;side<2;side++)for(boolean creative:new boolean[]{false,true}){
            Fixture f=new Fixture(carried,large,false,mode,false,creative);
            try{
                f.player.setHeldItem(EnumHand.MAIN_HAND,card(2,4));f.use(side);need(f.player.cards()==2&&f.door().keyReader(side).isEmpty()&&f.door().keyReader(side).rejected(),"wrong company rejected without loss");
                ItemStack accepted=card(2,3);f.player.setHeldItem(EnumHand.MAIN_HAND,accepted);f.use(side);TileEntityTokenSlot reader=f.door().keyReader(side);
                boolean returning=mode==1||mode==4;int remaining=creative&&!returning?2:1;
                need(f.player.cards()==remaining&&reader.getStackInSlot(0).getCount()==1,"one reader item; Creative only duplicates retained cards");
                need(reader.getStackInSlot(0).getTagCompound().getString("FixturePayload").equals("unchanged"),"complete credential payload retained");
                long sequence=reader.insertionSequence();f.use(side);need(f.player.cards()==remaining&&reader.insertionSequence()==sequence,"repeated click cannot borrow twice");
                if(returning)need(reader.removeStackFromSlot(0).isEmpty(),"returning item cannot be extracted");
                f.persist();f.step(100);f.open(!returning);f.step(107);f.open(!returning);f.step(108);f.open(true);
                need(mode!=4||f.door().securityToggle(),"toggle applies at scan midpoint");
                f.step(120);need(f.door().keyReader(side).returning()==false,"return finishes at twenty ticks");
                need(f.player.cards()==(returning?2:remaining),"return owns exactly one item including Creative");
                f.step(mode==1?208:200);f.open(mode==2||mode==4);
                if(!returning){need(!f.door().keyReader(side).isEmpty(),"retained pulse expiry does not destroy card");f.player.emptyHand();int before=f.player.cards();f.use(side);need(f.player.cards()==before+1&&f.door().keyReader(side).isEmpty(),"empty-hand removal returns one retained card");f.step(f.world.time);f.open(false);}
                need(!f.door().itemSettings().hasKey("DoorKeyReader")&&!f.door().itemSettings().hasKey("DoorSecurityToggled"),"picked settings never duplicate credential or toggle ownership");checked++;
            }finally{f.close();}
        }
        System.out.println("carried-door-security modes PASS cases="+checked+" native/carried ordinary/large front/rear four-modes survival/creative denied-company full-item-NBT deadlines");
    }
    private static void inventorySelection()throws Exception{
        for(boolean large:new boolean[]{false,true})for(int source=0;source<3;source++){
            Fixture f=new Fixture(true,large,false,2,false,false);
            try{
                f.player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(net.minecraft.init.Items.STICK));
                if(source==0)f.player.inventory.mainInventory.set(17,card(2,3));else if(source==1)f.player.setHeldItem(EnumHand.OFF_HAND,card(2,3));else{f.player.setHeldItem(EnumHand.MAIN_HAND,card(2,4));f.player.inventory.mainInventory.set(17,card(2,3));}
                f.use(1);need(!f.door().keyReader(1).isEmpty()&&f.player.cards()==(source==2?3:1),"authorized inventory/offhand credential is selected without consuming wrong held item");f.step(100);f.open(true);
                f.door().setPanelMode(1);ItemStack before=f.player.getHeldItemMainhand().copy();f.use(1);need(ItemStack.areItemStacksEqual(before,f.player.getHeldItemMainhand()),"hidden rear reader cannot consume a credential");
            }finally{f.close();}
        }
    }
    private static void persistence()throws Exception{
        for(boolean carried:new boolean[]{false,true})for(boolean large:new boolean[]{false,true})for(int mode:new int[]{1,4}){
            Fixture f=new Fixture(carried,large,false,mode,false,false);
            try{
                f.player.setHeldItem(EnumHand.MAIN_HAND,card(2,3));f.use(0);TileEntityTokenSlot first=f.door().keyReader();long sequence=first.insertionSequence();
                f.reload();need(f.door().keyReader().returning()&&f.door().keyReader().insertionSequence()==sequence,"reload before scan retains pending physical credential");
                need(!first.expire(1000)&&first.takeForDrop().isEmpty(),"displaced reader cannot return or drop a second credential");
                f.step(108);f.open(true);f.reload();need(f.door().keyReader().returning(),"reload after scan retains return owner");
                f.step(120);need(f.player.cards()==2&&f.door().keyReader().isEmpty(),"saved return finishes exactly once");f.step(200);need(f.player.cards()==2,"repeated expiry cannot duplicate return");
                if(mode==4){need(f.door().securityToggle(),"saved midpoint does not toggle twice");f.open(true);}else{f.step(208);f.open(false);}
            }finally{f.close();}
        }
        System.out.println("carried-door-security persistence PASS save/reload before-scan/after-scan stale-owner exactly-once return toggle/pulse no-item-settings-copy opaque-capabilities");
    }
    private static void deferredReturns()throws Exception{
        for(boolean carried:new boolean[]{false,true})for(boolean large:new boolean[]{false,true}){
            Fixture f=new Fixture(carried,large,false,4,false,false);
            try{
                f.player.setHeldItem(EnumHand.MAIN_HAND,card(1,3));f.use(0);f.door().configureSecurity(0,8);f.reload();
                if(!carried)need(f.world.scheduled.containsKey(f.root()),"disabled-security native reload rearms borrowed-card return");
                f.step(120);need(f.player.cards()==1&&f.door().keyReader().isEmpty(),"security Off still returns a pending card");need(!f.door().securityToggle(),"disabled security does not apply a pending toggle");
            }finally{f.close();}
        }
        Fixture f=new Fixture(true,false,false,4,false,false);
        try{
            f.player.setHeldItem(EnumHand.MAIN_HAND,card(1,3));f.use(0);ONLINE.clear();f.step(120);need(f.door().keyReader().returning()&&f.player.cards()==0,"offline owner retains credential in reader");
            f.reload();connect(f.player);for(int i=0;i<f.player.inventory.mainInventory.size();i++)f.player.inventory.mainInventory.set(i,new ItemStack(net.minecraft.init.Items.STICK,64));f.player.setHeldItem(EnumHand.OFF_HAND,new ItemStack(net.minecraft.init.Items.STICK,64));
            f.player.capabilities.isCreativeMode=true;f.step(140);need(f.door().keyReader().returning()&&f.player.cards()==0,"full Creative inventory cannot delete a returned credential");
            f.player.inventory.mainInventory.set(5,ItemStack.EMPTY);f.step(159);need(f.player.cards()==0,"full-inventory retry respects its twenty-tick deadline");
            ONLINE.get(f.player.getUniqueID()).world=new World(false);f.step(160);need(f.player.cards()==1&&f.door().keyReader().isEmpty(),"online owner receives one card after changing dimension");f.step(180);need(f.player.cards()==1,"repeated tick cannot return twice");
        }finally{f.close();}
        for(boolean carried:new boolean[]{false,true}){
            Fixture drop=new Fixture(carried,false,false,2,false,false);
            try{
                drop.player.setHeldItem(EnumHand.MAIN_HAND,card(1,3));drop.use(0);for(int i=0;i<drop.player.inventory.mainInventory.size();i++)drop.player.inventory.mainInventory.set(i,new ItemStack(net.minecraft.init.Items.STICK,64));drop.player.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);
                drop.door().useSecurity(drop.player,EnumHand.OFF_HAND,0);need(drop.door().keyReader().isEmpty()&&drop.player.drops.size()==1&&drop.player.drops.get(0).getCount()==1,"full-inventory manual removal drops exactly one credential");drop.persist();
                drop.door().useSecurity(drop.player,EnumHand.OFF_HAND,0);need(drop.player.drops.size()==1,"repeat removal cannot duplicate drop");
            }finally{drop.close();}
        }
        for(boolean carried:new boolean[]{false,true}){
            Fixture changed=new Fixture(carried,false,false,4,false,false);
            try{changed.player.setHeldItem(EnumHand.MAIN_HAND,card(1,3));changed.use(0);changed.door().configureSecurity(4,16);changed.step(108);changed.open(false);changed.step(120);need(changed.player.cards()==1&&changed.door().keyReader().isEmpty(),"changed company rejects pending scan while returning the original physical card");}
            finally{changed.close();}
        }
        System.out.println("carried-door-security returns PASS disabled-mode reload offline/full-inventory/Creative retry cross-dimension return and one-item removal/drop");
    }
    private static void ownershipAndClients()throws Exception{
        for(boolean carried:new boolean[]{false,true})for(boolean large:new boolean[]{false,true}){
            Fixture client=new Fixture(carried,large,false,4,true,false);
            try{
                client.player.setHeldItem(EnumHand.MAIN_HAND,card(2,3));client.door().keyReader(0);client.door().keyReader(1);NBTTagCompound before=client.door().writeToNBT(new NBTTagCompound());client.use(0);client.door().keyReader().reject();need(!client.door().keyReader().insertToggle(client.player,client.player.getHeldItemMainhand()),"client cannot borrow an item");need(before.equals(client.door().writeToNBT(new NBTTagCompound()))&&client.player.cards()==2,"client use/rejection leaves inventory and credential NBT unchanged");
                NBTTagCompound pending=before.copy(),reader=pending.getCompoundTag("DoorKeyReader");reader.setTag("InsertedToken",card(1,3).writeToNBT(new NBTTagCompound()));reader.setUniqueId("TokenReturnTo",client.player.getUniqueID());reader.setLong("TokenInsertStarted",80);reader.setBoolean("TokenToggleApplied",false);client.door().readFromNBT(pending);before=client.door().writeToNBT(new NBTTagCompound());
                need(!client.door().keyReader().expire(1000),"client cannot apply a received pending scan or expiry");need(client.door().keyReader().takeForDrop().isEmpty(),"client cannot extract a received pending credential");client.door().tickReaders();need(before.equals(client.door().writeToNBT(new NBTTagCompound()))&&client.player.cards()==2,"client timers/drop leave received credential NBT and inventory unchanged");
            }finally{client.close();}
        }
        Fixture f=new Fixture(true,false,false,4,false,false);
        try{
            f.player.setHeldItem(EnumHand.MAIN_HAND,card(2,3));
            f.craft.transferInProgress=true;f.use(0);f.craft.transferInProgress=false;f.craft.ownershipBlocked=true;f.use(0);f.craft.ownershipBlocked=false;f.player.spectator=true;f.use(0);f.player.spectator=false;f.player.setPosition(120,80,100);f.use(0);f.player.setPosition(102.5,80,101);
            need(f.player.cards()==2&&f.door().keyReader().isEmpty(),"transfer/blocked/spectator/distant actors cannot borrow credentials");
            f.use(0);f.craft.transferInProgress=true;need(!f.door().keyReader().expire(120)&&f.player.cards()==1,"in-flight ownership transfer freezes credential expiry");f.craft.transferInProgress=false;
            f.persist();TileEntitySpaceDoor stale=f.door();List<VehicleStructure.Cell> next=new ArrayList<>();for(VehicleStructure.Cell cell:f.craft.structure.cells){NBTTagCompound tag=cell.tileData();if(cell.pos.equals(DOOR))tag.setInteger("SpaceDetail",2);next.add(new VehicleStructure.Cell(cell.pos,cell.state,tag));}f.craft.install(new VehicleStructure(next,SEAT,EnumFacing.SOUTH,1));need(f.door()!=stale,"fixture installs a different carried owner at the same position");f.mirror();
            NBTTagCompound staleBefore=stale.writeToNBT(new NBTTagCompound());stale.useSecurity(f.player,EnumHand.MAIN_HAND);need(!stale.keyReader().expire(120)&&stale.keyReader().takeForDrop().isEmpty()&&staleBefore.equals(stale.writeToNBT(new NBTTagCompound())),"displaced carried owner cannot mutate or transfer inventory");f.step(120);need(f.player.cards()==2,"replacement current owner returns exactly one card");
        }finally{f.close();}
        System.out.println("carried-door-security authority PASS client no-mutation current-owner identity transfer/blocked/spectator/reach guards");
    }
    private static void pairedReaders()throws Exception{
        Fixture f=new Fixture(true,false,true,4,false,false);
        try{
            need(f.door().mate().equals(f.root().west()),"fixture contains a real paired ordinary door");f.player.setHeldItem(EnumHand.MAIN_HAND,card(3,3));f.use(0);f.use(1);need(f.player.cards()==1&&f.door().keyReader(0).returning()&&f.door().keyReader(1).returning(),"front and rear borrow separate physical cards");
            f.step(108);f.open(false);need(!f.door().securityToggle()&&!f.door(f.root().west()).securityToggle(),"two simultaneous scans toggle paired leaves exactly twice");f.step(120);need(f.player.cards()==3,"both readers return their own card once");
            f.use(1);f.step(128);f.open(true);need(f.door(f.root().west()).securityToggle(),"one rear-reader toggle propagates to paired leaf");f.reload();f.step(140);f.open(true);need(f.player.cards()==3,"paired snapshots retain one pending return without reapplying toggle");
        }finally{f.close();}
        for(boolean large:new boolean[]{false,true}){
            Fixture retained=new Fixture(true,large,false,2,false,false);
            try{retained.player.setHeldItem(EnumHand.MAIN_HAND,card(2,3));retained.use(0);retained.use(1);retained.step(100);retained.open(true);retained.use(1);retained.step(100);retained.open(true);need(!retained.door().keyReader(0).isEmpty()&&retained.door().keyReader(1).isEmpty(),"removing rear card preserves front reader ownership");retained.player.emptyHand();retained.use(0);retained.step(100);retained.open(false);need(retained.player.cards()==2,"independent front/rear retained cards return exactly once");}
            finally{retained.close();}
        }
    }
    private static void standaloneSavedCopy(){
        TileEntityTokenSlot reader=new TileEntityTokenSlot();reader.configure(false,8,2);
        NBTTagCompound pending=reader.writeToNBT(new NBTTagCompound());pending.setTag("InsertedToken",card(1,3).writeToNBT(new NBTTagCompound()));pending.setUniqueId("TokenReturnTo",UUID.randomUUID());pending.setLong("TokenInsertStarted",100);
        TileEntityTokenSlot copy=(TileEntityTokenSlot)TileEntity.create(null,pending);need(copy!=null&&copy.getWorld()==null,"standalone saved-copy fixture remains unattached");
        need(copy.expire(108)&&copy.getOutputLevel()==15,"released standalone saved-copy timeline still applies one toggle");copy.expire(140);need(copy.returning()&&copy.getOutputLevel()==15,"unattached copy retains its item and cannot return to a live inventory");need(copy.takeForDrop().getCount()==1&&copy.takeForDrop().isEmpty(),"unattached standalone data extracts one retained item");
        System.out.println("carried-door-security standalone-copy PASS released worldless timeline/drop semantics; no live inventory return");
    }
    private static void initialize()throws Exception{
        net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();
        java.lang.reflect.Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);unsafe=(sun.misc.Unsafe)field.get(null);server=(net.minecraft.server.dedicated.DedicatedServer)unsafe.allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);server.setPlayerList((Players)unsafe.allocateInstance(Players.class));
        java.lang.reflect.Field thread=net.minecraft.server.MinecraftServer.class.getDeclaredField("serverThread");thread.setAccessible(true);thread.set(server,Thread.currentThread());
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntitySpaceDoor.class,new ResourceLocation("vandorlabs:carried_security_door"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new ResourceLocation("vandorlabs:carried_security_large_door"));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityTokenSlot.class,new ResourceLocation("vandorlabs:carried_security_reader"));
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.ACCESS_CARD);net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.register(ModItems.DATA_CHIP);
        BlockDetailedDoor model=new BlockDetailedDoor("carried_security_model",BlockVandorDoor.DoorMotion.SLIDING,true,true,false,1,15,7,90,-90,-16,16);
        doorBlock=new BlockConfigurableSpaceDoor("carried_security_door",true,model);largeBlock=new BlockLargeProgrammableDoor("carried_security_large_door",model);seatBlock=new BlockPilotSeat();
        for(Block block:new Block[]{doorBlock,largeBlock,seatBlock})net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        // Keep the real VehicleSignals/VehicleDoors packet path; replace only remote transport.
        // Some compatible Forge development jars add a logical side without bootstrapping
        // its network map. This standalone runner does not execute their normal launcher.
        java.lang.reflect.Field registryChannels=net.minecraftforge.fml.common.network.NetworkRegistry.class.getDeclaredField("channels");registryChannels.setAccessible(true);
        Map<net.minecraftforge.fml.relauncher.Side,Map<String,net.minecraftforge.fml.common.network.FMLEmbeddedChannel>> registry=(Map)registryChannels.get(net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE);
        for(net.minecraftforge.fml.relauncher.Side side:net.minecraftforge.fml.relauncher.Side.values())registry.computeIfAbsent(side,ignored->new java.util.concurrent.ConcurrentHashMap<>());
        java.lang.reflect.Field channels=net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper.class.getDeclaredField("channels");channels.setAccessible(true);
        io.netty.channel.Channel channel=(io.netty.channel.Channel)((Map<?,?>)channels.get(com.vandorlabs.network.PacketHandler.INSTANCE)).get(net.minecraftforge.fml.relauncher.Side.SERVER);
        channel.pipeline().addLast("credential-fixture-transport",new io.netty.channel.ChannelOutboundHandlerAdapter(){
            @Override public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){
                if(!(message instanceof VehicleNetwork.Packet))throw new AssertionError("unexpected fixture message "+message);
                io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{((VehicleNetwork.Packet)message).toBytes(bytes);packetBytes+=bytes.readableBytes();VehicleNetwork.Packet decoded=new VehicleNetwork.Packet();decoded.fromBytes(bytes);need(decoded.valid,"credential packet round trip");PACKETS.add(decoded);packetCount++;promise.setSuccess();}finally{bytes.release();}
            }
        });
    }
    public static void main(String[] args)throws Exception{
        initialize();reproduction();modes();inventorySelection();persistence();deferredReturns();ownershipAndClients();pairedReaders();standaloneSavedCopy();
        System.out.println("carried-door-security PASS scenarios="+scenarios+" assertions="+assertions+" actual-packets="+packetCount+" encoded-bytes="+packetBytes+" read-only vehicle view retained; no rendering/client connection/process-crash recovery claimed");
    }
}
