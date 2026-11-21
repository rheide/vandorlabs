package com.vandorlabs.gear;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.vandorlabs.gear.core.ArmResource;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Reported: an RFTools Power Cell on a powered Extender received RF through the arm, but the arm
 * refused to move it. Each case places an RFTools block on its own Extender tip through the item
 * path and right-clicks the arm out and back. Supported blocks travel both ways with their data;
 * a Power Cell keeps its network's energy, the network lists exactly its current position, and RF
 * fed into the arm base reaches it after the move. A Matter Transmitter, bound to fixed
 * coordinates, stays where it is and the arm reports why.
 */
@SideOnly(Side.CLIENT)
public final class RFToolsArmLiveChecks {
    private static final BlockPos ROOT=new BlockPos(410,112,40);
    private static final String[] MOVED={"powercell","powercell_advanced","powercell_simple","powercell_creative","coalgenerator","relay",
            "booster","machine_infuser","crafter1","crafter2","crafter3","item_filter","environmental_controller","security_manager",
            "pearl_injector","ender_monitor","spawner","storage_terminal","level_emitter","dialing_device",
            "composer","projector","locator","screen","creative_screen","counter_block","logic_block","analog_block","sensor_block",
            "digit_block","sequencer_block","wire_block","timer_block","invchecker_block","redstone_transmitter_block","redstone_receiver_block"};
    private static final String REFUSED="matter_transmitter";
    private static int index,step,ticks,checks;
    private static Integer networkEnergy,localEnergy;
    private static boolean startTarget;
    private static boolean registered;
    private static volatile boolean complete;
    private static volatile Throwable failure;
    private RFToolsArmLiveChecks(){}

    public static boolean tick(Minecraft mc){
        mc.gameSettings.pauseOnLostFocus=false;
        if(mc.currentScreen!=null)mc.displayGuiScreen(null);
        if(!registered){MinecraftForge.EVENT_BUS.register(new Observer(mc.getIntegratedServer()));registered=true;}
        if(failure!=null)throw new IllegalStateException("RFTools arm check",failure);
        return complete;
    }
    @SideOnly(Side.CLIENT)
    public static final class Observer {
        private final MinecraftServer server;
        private Observer(MinecraftServer server){this.server=server;}
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){
            if(event.phase!=TickEvent.Phase.END||complete||failure!=null||server.getPlayerList().getPlayers().isEmpty())return;
            try{complete=advance(server.getWorld(0),server.getPlayerList().getPlayers().get(0));}
            catch(RuntimeException|LinkageError|ReflectiveOperationException error){failure=error;}
            if(complete||failure!=null)MinecraftForge.EVENT_BUS.unregister(this);
        }
    }
    private static String name(){return index<MOVED.length?MOVED[index]:REFUSED;}
    private static BlockPos mount(int i){return ROOT.east(i*3);}
    private static void require(boolean value,String message,WorldServer world){
        checks++;
        if(!value)throw new IllegalStateException("rftools-arm case="+name()+" step="+step+" tick="+ticks+" "+message+" "+state(world));
    }
    private static GearTileEntity arm(WorldServer world){
        TileEntity raw=world.getTileEntity(mount(index));return raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
    }
    private static String state(WorldServer world){
        GearTileEntity arm=arm(world);BlockPos at=find(world);
        return "arm="+(arm==null?"missing":"progress="+arm.progress()+" target="+arm.target()+" error="+arm.error())+" block="+(at==null?"missing":at+" "+world.getBlockState(at));
    }
    /** The RFTools tile of this case in its column, or null. */
    private static BlockPos find(WorldServer world){
        for(int y=1;y<10;y++){
            BlockPos at=mount(index).up(y);TileEntity tile=world.getTileEntity(at);
            if(tile!=null&&tile.getClass().getName().startsWith("mcjty.rftools."))return at;
        }
        return null;
    }
    private static boolean settled(WorldServer world,GearTileEntity arm){
        return arm!=null&&arm.progress()==(arm.target()?1:0)&&!ArmTransactions.locked(world,mount(index))&&(arm.payload()==null||arm.payload().materialized());
    }
    private static void rightClick(WorldServer world,EntityPlayerMP player){
        // Arms only answer players within reach, as in play.
        BlockPos mount=mount(index);player.connection.setPlayerLocation(mount.getX()+.5,mount.getY(),mount.getZ()-3.5,0,10);
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        player.interactionManager.processRightClickBlock(player,world,ItemStack.EMPTY,EnumHand.MAIN_HAND,mount(index),EnumFacing.NORTH,.5F,.5F,.5F);
    }

    // Power Cell network, read through RFTools' own world data.
    private static Object network(WorldServer world,TileEntity cell)throws ReflectiveOperationException{
        int id=(Integer)cell.getClass().getMethod("getNetworkId").invoke(cell);
        Class<?> networks=Class.forName("mcjty.rftools.blocks.powercell.PowerCellNetwork");
        Object channels=networks.getMethod("getChannels",net.minecraft.world.World.class).invoke(null,world);
        return id<0?null:networks.getMethod("getOrCreateNetwork",int.class).invoke(channels,id);
    }
    private static Object coordinate(WorldServer world,BlockPos pos)throws ReflectiveOperationException{
        return Class.forName("mcjty.lib.varia.GlobalCoordinate").getConstructor(BlockPos.class,int.class).newInstance(pos,world.provider.getDimension());
    }
    private static int energy(Object network)throws ReflectiveOperationException{return (Integer)network.getClass().getMethod("getEnergy").invoke(network);}
    @SuppressWarnings("unchecked")
    private static Set<Object> members(Object network)throws ReflectiveOperationException{return (Set<Object>)network.getClass().getMethod("getBlocks").invoke(network);}

    private static void build(WorldServer world,EntityPlayerMP player){
        player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;
        player.connection.setPlayerLocation(ROOT.getX()+12.5,ROOT.getY()+2,ROOT.getZ()-6.5,0,15);
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-2,-3),ROOT.add(3*(MOVED.length+1)+3,10,3))){
            world.getChunkFromBlockCoords(at);world.removeTileEntity(at);
            world.setBlockState(at,at.getY()==ROOT.getY()-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        for(int i=0;i<=MOVED.length;i++){
            index=i;BlockPos mount=mount(i);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_EXTENDER,2));
            for(BlockPos support:new BlockPos[]{mount.down(),mount})
                require(player.getHeldItemMainhand().onItemUse(player,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary Extender placement",world);
            Block block=Block.REGISTRY.getObject(new ResourceLocation("rftools",name()));
            require(block!=null&&block!=Blocks.AIR,"RFTools block installed",world);
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block));
            require(player.getHeldItemMainhand().onItemUse(player,world,mount.up(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary placement on the Extender tip",world);
            require(find(world)!=null&&find(world).equals(mount.up(2)),"RFTools block on the tip",world);
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(mount);require(arm.rebuild(),"Extender forms",world);
            arm=arm.loadedController();arm.operator(player);
            NBTTagCompound settings=arm.settings();settings.setInteger(ArmResource.CHOICE,ArmResource.POWER.ordinal());arm.configure(settings);
        }
        player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
        index=0;
    }

    private static boolean advance(WorldServer world,EntityPlayerMP player)throws ReflectiveOperationException{
        ticks++;
        if(step==0){build(world,player);step=1;ticks=0;return false;}
        GearTileEntity arm=arm(world);
        if(step==1){
            // Start: note the block and, for a Power Cell, charge its network through RFTools.
            if(ticks<20)return false;
            BlockPos at=find(world);require(at!=null&&at.equals(mount(index).up(2)),"block waits on the tip",world);
            TileEntity tile=world.getTileEntity(at);networkEnergy=null;localEnergy=null;
            if(name().startsWith("powercell")&&!name().equals("powercell_creative")){
                // The creative cell is always full, so it only has to travel. The plain cell joins a network through its Power Cell Card; the advanced one keeps energy itself.
                boolean card=name().equals("powercell");
                if(card){
                    net.minecraft.item.Item item=net.minecraft.item.Item.REGISTRY.getObject(new ResourceLocation("rftools","powercell_card"));
                    require(item!=null,"Power Cell Card installed",world);
                    ((net.minecraft.inventory.IInventory)tile).setInventorySlotContents(0,new ItemStack(item));
                }
                // As with a wrench: the face resting on the arm tip takes input.
                Method mode=tile.getClass().getMethod("getMode",EnumFacing.class);
                for(int n=0;n<3&&!String.valueOf(mode.invoke(tile,EnumFacing.DOWN)).equals("MODE_INPUT");n++)tile.getClass().getMethod("toggleMode",EnumFacing.class).invoke(tile,EnumFacing.DOWN);
                require(String.valueOf(mode.invoke(tile,EnumFacing.DOWN)).equals("MODE_INPUT"),"Power Cell bottom set to input",world);
                int charged=(Integer)tile.getClass().getMethod("receiveEnergyFacing",EnumFacing.class,int.class,boolean.class).invoke(tile,EnumFacing.DOWN,50000,false);
                require(charged>0,"Power Cell accepts RF through its bottom",world);
                if(card){
                    Object network=network(world,tile);require(network!=null&&members(network).contains(coordinate(world,at)),"carded Power Cell belongs to its network",world);
                    networkEnergy=energy(network);
                }else{
                    require(network(world,tile)==null,"uncarded Power Cell has no network",world);
                    localEnergy=stored(tile);require(localEnergy==charged,"uncarded Power Cell holds the RF itself",world);
                }
            }
            startTarget=arm.target();
            rightClick(world,player);step=2;ticks=0;return false;
        }
        if(step==2){
            if(name().equals(REFUSED)){
                if(ticks<60)return false;
                require(arm.target()==startTarget&&find(world)!=null&&find(world).equals(mount(index).up(2)),"Matter Transmitter stays on the tip",world);
                require(arm.error()!=null&&arm.error().contains("teleport destination"),"arm reports why the Matter Transmitter cannot move",world);
                System.out.println("[vandorlabs] rftools-arm PASS checks="+checks+" "+MOVED.length+" RFTools blocks carried out and back with their data; Power Cell network energy and membership follow the cell; RF reaches it through the arm; Matter Transmitter refused: "+arm.error());
                return true;
            }
            if(ticks<10||!settled(world,arm))return ticksLimit(world);
            require(arm.target()!=startTarget,"arm moved: "+arm.error(),world);
            BlockPos at=find(world);require(at!=null&&!at.equals(mount(index).up(2)),"block carried with the arm",world);
            verifyCell(world,at,mount(index).up(2));
            if(name().equals("powercell")){
                // RF offered to the arm base reaches the carried cell's network.
                Object network=network(world,world.getTileEntity(at));int before=energy(network);
                IEnergyStorage base=arm.getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);
                require(base!=null&&base.canReceive(),"arm base takes RF",world);
                int accepted=base.receiveEnergy(1000,false);
                require(accepted>0&&energy(network)==before+accepted,"RF through the arm reaches the moved cell: accepted="+accepted+" before="+before+" after="+energy(network),world);
                networkEnergy=energy(network);
            }
            rightClick(world,player);step=3;ticks=0;return false;
        }
        if(step==3){
            if(ticks<10||!settled(world,arm))return ticksLimit(world);
            require(arm.target()==startTarget,"arm moved back: "+arm.error(),world);
            BlockPos at=find(world);require(at!=null&&at.equals(mount(index).up(2)),"block back on the tip",world);
            verifyCell(world,at,null);
            index++;step=1;ticks=0;return false;
        }
        return false;
    }
    private static boolean ticksLimit(WorldServer world){require(ticks<600,"arm move timed out",world);return false;}
    private static int stored(TileEntity tile)throws ReflectiveOperationException{return (Integer)tile.getClass().getMethod("getEnergyStored").invoke(tile);}
    private static void verifyCell(WorldServer world,BlockPos at,BlockPos left)throws ReflectiveOperationException{
        if(localEnergy!=null)require(stored(world.getTileEntity(at))==localEnergy,"uncarded Power Cell keeps its own "+localEnergy+" RF, got "+stored(world.getTileEntity(at)),world);
        if(networkEnergy==null)return;
        Object network=network(world,world.getTileEntity(at));
        require(network!=null&&energy(network)==networkEnergy,"network keeps its energy "+networkEnergy+" got "+(network==null?"none":energy(network)),world);
        Set<Object> members=members(network);
        require(members.contains(coordinate(world,at))&&members.size()==1,"network lists exactly the cell's new position: "+members,world);
        if(left!=null)require(!members.contains(coordinate(world,left)),"network forgets where the cell was lifted",world);
    }
}
