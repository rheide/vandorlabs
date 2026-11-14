package com.vandorlabs.client;

import com.vandorlabs.ports.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DeferredTileLoad;
import java.io.File;
import java.lang.reflect.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.*;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.*;

/** Actual installed pipe networks, with optional types accessed only inside this explicit fixture. */
final class ResourcePortModLiveChecks {
    private static final String[] CASES={"TD_POWER","TD_FLUID","TD_MATTER","MEK_POWER","MEK_FLUID","MEK_MATTER","GC_POWER","GC_FLUID","RF_POWER"};
    private static final int START=env("VANDOR_RESOURCE_PORT_MOD_START",0),END=env("VANDOR_RESOURCE_PORT_MOD_END",CASES.length);
    private static int fixture=START,stage,ticks,settled;
    private static volatile String status="initial";
    private static BlockPos at(){return new BlockPos(1000,110,fixture*6);}
    private static String name(){return CASES[fixture];}
    private static boolean fair(){return name().equals("MEK_POWER");}
    private static ResourcePortBlock block(){return name().endsWith("POWER")?ResourcePorts.POWER:name().endsWith("FLUID")?ResourcePorts.FLUID:ResourcePorts.MATTER;}
    private static int amount(){return fair()?20:block()==ResourcePorts.MATTER?7:128;}
    private static int env(String key,int fallback){String value=System.getenv(key);return value==null?fallback:Integer.parseInt(value);}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Resource port mods fixture="+fixture+" stage="+stage+" "+why+" "+status);}
    private static Object call(Object receiver,String method,Class<?>[] signature,Object... args)throws Exception{
        if(receiver.getClass().getName().startsWith("mekanism.")){
            // Class.getMethod enumerates unrelated optional method signatures and
            // resolves absent MCMultipart types. Resolve only this public API.
            Class<?> result=method.equals("getAllCurrentConnections")?byte.class:void.class;
            try{return java.lang.invoke.MethodHandles.publicLookup().findVirtual(receiver.getClass(),method,java.lang.invoke.MethodType.methodType(result,signature)).bindTo(receiver).invokeWithArguments(args);}catch(Throwable failure){if(failure instanceof Exception)throw (Exception)failure;if(failure instanceof Error)throw (Error)failure;throw new IllegalStateException(failure);}
        }
        return receiver.getClass().getMethod(method,signature).invoke(receiver,args);
    }
    private static ItemStack transport()throws Exception{
        if(name().startsWith("TD_")){Object duct=Class.forName("cofh.thermaldynamics.duct.TDDucts").getField(block()==ResourcePorts.POWER?"energyBasic":block()==ResourcePorts.FLUID?"fluidBasic":"itemBasic").get(null);return ((ItemStack)duct.getClass().getField("itemStack").get(duct)).copy();}
        if(name().startsWith("MEK_")){Block transmitter=(Block)Class.forName("mekanism.common.MekanismBlocks").getField("Transmitter").get(null);return new ItemStack(transmitter,1,block()==ResourcePorts.POWER?0:block()==ResourcePorts.FLUID?1:3);}
        Block nativeBlock=Block.getBlockFromName(name().equals("GC_POWER")?"galacticraftcore:aluminum_wire":name().equals("GC_FLUID")?"galacticraftcore:fluid_pipe":"rftools:powercell");need(nativeBlock!=null&&nativeBlock!=Blocks.AIR,"registered exact optional block");return new ItemStack(nativeBlock);
    }
    private static TileEntity place(WorldServer world,EntityPlayerMP actor,BlockPos pos,ItemStack stack){
        world.getChunkFromBlockCoords(pos);world.removeTileEntity(pos);world.setBlockToAir(pos);need(stack.getItem() instanceof ItemBlock,"native optional placement item");ItemBlock item=(ItemBlock)stack.getItem();Block nativeBlock=item.getBlock();IBlockState state=nativeBlock.getStateForPlacement(world,pos,EnumFacing.UP,.5F,.5F,.5F,item.getMetadata(stack.getItemDamage()),actor);need(item.placeBlockAt(stack,actor,world,pos,EnumFacing.UP,.5F,.5F,.5F,state),"actual native ItemBlock placement "+nativeBlock.getRegistryName());return world.getTileEntity(pos);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) private static void pull(TileEntity tile,EnumFacing side)throws Exception{Field field=tile.getClass().getField("connectionTypes");Object array=field.get(tile);Class<?> type=array.getClass().getComponentType();Array.set(array,side.getIndex(),Enum.valueOf((Class)type,"PULL"));call(tile,"refreshConnections",new Class<?>[0]);call(tile,"notifyTileChange",new Class<?>[0]);}
    private static void sink(WorldServer world,EntityPlayerMP actor,EnumFacing direction)throws Exception{
        BlockPos pos=at().offset(direction,3);Block nativeBlock=block()==ResourcePorts.POWER?Block.getBlockFromName("vandorlabs:battery_block"):block()==ResourcePorts.FLUID?Block.getBlockFromName("vandorlabs:hexagonal_fuel_tank"):Blocks.CHEST;
        TileEntity tile=place(world,actor,pos,new ItemStack(nativeBlock));if(block()==ResourcePorts.POWER)world.setBlockState(pos,world.getBlockState(pos).withProperty(com.vandorlabs.blocks.BlockBattery.FACING,direction.getOpposite()),2);if(block()==ResourcePorts.FLUID)world.setBlockState(pos,world.getBlockState(pos).withProperty(com.vandorlabs.blocks.BlockAnimatedScreenSelector.FACING,direction.getOpposite()),2);need(tile!=null,"installed ordinary destination");
    }
    private static void setup(Minecraft mc)throws Exception{
        mc.displayGuiScreen(null);mc.getIntegratedServer().callFromMainThread(()->{
            try{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);for(BlockPos p:BlockPos.getAllInBox(at().add(-4,-1,-3),at().add(5,3,3))){world.getChunkFromBlockCoords(p);world.removeTileEntity(p);world.setBlockToAir(p);}for(BlockPos p:BlockPos.getAllInBox(at().add(-4,-1,-3),at().add(5,-1,3)))world.setBlockState(p,Blocks.STONE.getDefaultState(),2);
                actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.connection.setPlayerLocation(at().getX()+6.5,at().getY()+3,at().getZ()-7.5,40,15);
                ResourcePortTile port=(ResourcePortTile)place(world,actor,at(),new ItemStack(block()));ResourcePortSettings disabled=ResourcePortSettings.placed(EnumFacing.NORTH.getIndex()).withFace(EnumFacing.NORTH.getIndex(),ResourcePortSettings.Face.DISABLED).withFace(EnumFacing.SOUTH.getIndex(),ResourcePortSettings.Face.DISABLED).withTransfer(0,false,ResourcePortSettings.OnTexture.CONNECTED);port.configure(disabled,0,ChannelList.EMPTY);
                ItemStack pipe=transport();if(name().equals("RF_POWER")){TileEntity cell=place(world,actor,at().east(),pipe);for(int i=0;i<3&&!String.valueOf(call(cell,"getMode",new Class<?>[]{EnumFacing.class},EnumFacing.WEST)).equals("MODE_INPUT");i++)call(cell,"toggleMode",new Class<?>[]{EnumFacing.class},EnumFacing.WEST);}
                else for(EnumFacing direction:fair()?new EnumFacing[]{EnumFacing.EAST,EnumFacing.WEST}:new EnumFacing[]{EnumFacing.EAST}){TileEntity first=place(world,actor,at().offset(direction),pipe.copy());place(world,actor,at().offset(direction,2),pipe.copy());sink(world,actor,direction);if(name().startsWith("MEK_"))pull(first,direction.getOpposite());}
                port.onLoad();DeferredTileLoad.flush(world);return null;
            }catch(Exception failure){throw new IllegalStateException(failure);}
        }).get();settled=0;ticks=0;
    }
    private static boolean visual(net.minecraft.world.World world,EnumFacing direction)throws Exception{
        BlockPos pos=at().offset(direction);TileEntity pipe=world.getTileEntity(pos);if(pipe==null)return false;EnumFacing side=direction.getOpposite();
        if(name().startsWith("TD_"))return !String.valueOf(call(pipe,"getVisualConnectionType",new Class<?>[]{int.class},side.getIndex())).equals("NONE");
        if(name().startsWith("MEK_"))return (((Number)call(pipe,"getAllCurrentConnections",new Class<?>[0])).intValue()&(1<<side.getIndex()))!=0;
        if(name().startsWith("GC_")){IBlockState state=world.getBlockState(pos);state=state.getBlock().getActualState(state,world,pos);for(IProperty<?> property:state.getPropertyKeys())if(property.getName().equals(side.getName()))return Boolean.TRUE.equals(state.getValue((IProperty)property));return false;}
        IEnergyStorage handler=pipe.getCapability(CapabilityEnergy.ENERGY,side);return handler!=null&&handler.canReceive();
    }
    private static int sinkAmount(WorldServer world,EnumFacing direction){TileEntity sink=world.getTileEntity(at().offset(direction,name().equals("RF_POWER")?1:3));if(sink==null)return -1;
        if(block()==ResourcePorts.POWER){IEnergyStorage energy=sink.getCapability(CapabilityEnergy.ENERGY,direction.getOpposite());return energy==null?-1:energy.getEnergyStored();}
        if(block()==ResourcePorts.FLUID){IFluidHandler handler=sink.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,direction.getOpposite());int count=0;for(IFluidTankProperties tank:handler.getTankProperties()){FluidStack fluid=tank.getContents();if(fluid!=null)count+=fluid.amount;}return count;}
        IItemHandler handler=sink.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,direction.getOpposite());int count=0;for(int i=0;i<handler.getSlots();i++){ItemStack stack=handler.getStackInSlot(i);if(!stack.isEmpty()){need(stack.getItem()==Items.DIAMOND&&stack.getDisplayName().equals("Port cargo"),"actual network retains item type and authored stack NBT");count+=stack.getCount();}}return count;
    }
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(fixture>=END){System.out.println("[vandorlabs][reprolab] utility26-focused PASS resource-port-mods selected="+START+".."+END+" actual TD/Mekanism/Galacticraft/RFTools storage/pipe networks and client connections");return true;}
        need(++ticks<900,"bounded native network transfer");
        if(stage==0){if(ticks<80&&fixture==START)return false;for(String id:new String[]{"thermaldynamics","mekanism","galacticraftcore","rftools"})need(Loader.isModLoaded(id),"explicit installed optional dependency "+id);setup(mc);stage=1;return false;}
        if(stage==1){if(++settled<20)return false;if(!name().equals("RF_POWER"))need(!visual(mc.world,EnumFacing.EAST),"disabled port has no actual client pipe connection");mc.getIntegratedServer().callFromMainThread(()->{ResourcePortTile port=(ResourcePortTile)mc.getIntegratedServer().getWorld(0).getTileEntity(at());ResourcePortSettings active=port.settings().withFace(EnumFacing.NORTH.getIndex(),ResourcePortSettings.Face.INPUT).withFace(EnumFacing.EAST.getIndex(),ResourcePortSettings.Face.OUTPUT);if(fair())active=active.withFace(EnumFacing.WEST.getIndex(),ResourcePortSettings.Face.OUTPUT).withTransfer(1,false,ResourcePortSettings.OnTexture.CONNECTED);port.configure(active,0,ChannelList.EMPTY);return null;}).get();stage=2;settled=0;return false;}
        if(stage==2){if(++settled<20||!visual(mc.world,EnumFacing.EAST)||fair()&&!visual(mc.world,EnumFacing.WEST))return false;
            boolean ready=mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);ResourcePortTile port=(ResourcePortTile)world.getTileEntity(at());TileEntity peer=world.getTileEntity(at().east());status="case="+name()+" sourceConnected="+port.connectedMask()+" sourceArt="+port.appearanceMask()+" sink="+sinkAmount(world,EnumFacing.EAST)+" peer="+(peer==null?"missing":peer.getClass().getName());if((port.connectedMask()&(1<<EnumFacing.EAST.getIndex()))==0){need(ticks<120,"Connected artwork recognizes actual compatible native pipe/storage after bounded cache refresh");return false;}if(block()==ResourcePorts.POWER)need(port.getCapability(CapabilityEnergy.ENERGY,EnumFacing.NORTH).receiveEnergy(amount(),false)==amount(),"inject exact test RF");else if(block()==ResourcePorts.FLUID)need(port.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER,amount()),true)==amount(),"inject exact test fluid");else{ItemStack cargo=new ItemStack(Items.DIAMOND,amount());cargo.setStackDisplayName("Port cargo");need(port.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,EnumFacing.NORTH).insertItem(0,cargo,false).isEmpty(),"inject exact tagged test cargo");}return true;}).get();if(!ready)return false;stage=3;return false;
        }
        boolean delivered=mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);int east=sinkAmount(world,EnumFacing.EAST),west=fair()?sinkAmount(world,EnumFacing.WEST):0;ResourcePortTile port=(ResourcePortTile)world.getTileEntity(at());status="case="+name()+" east="+east+" west="+west+" sourceConnected="+port.connectedMask()+" sourceArt="+port.appearanceMask();need(east+west<=amount(),"native network never generates extra resources");if(east+west!=amount())return false;if(fair())need(Math.abs(east-west)<=1,"actual two Mekanism PULL cables fairly share one rate1 budget");return true;}).get();
        if(!delivered)return false;need(visual(mc.world,EnumFacing.EAST),"actual client pipe branch stays connected after resource delivery");mc.getFramebuffer().bindFramebuffer(true);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"port-mod-"+name().toLowerCase(java.util.Locale.ROOT)+".png"));System.out.println("[vandorlabs][reprolab] resource-port-mod PASS "+name()+" "+status);fixture++;stage=0;ticks=80;return false;
    }
    private ResourcePortModLiveChecks(){}
}
