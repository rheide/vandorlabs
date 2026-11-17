package com.vandorlabs.gear;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** One stopped fractional Dish/Part click, then its normal settings route; no direct server action injection. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmCoreUseLiveChecks {
    private static final BlockPos ROOT=ArmCoreFixture.ROOT;
    private static final java.lang.reflect.Field TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.network.NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static int stage,ticks,checks,activity,oldWindow;private static int held;private static NBTTagCompound configured;private static boolean sent;private static Vec3d feet,aim;private static GameType mode=GameType.CREATIVE;private static String state="not sampled";
    private static void need(boolean value,String why){checks++;if(!value)throw new IllegalStateException("Carried Core use stage="+stage+" age="+ticks+": "+why+" server="+state);}
    private static GearTileEntity root(World world){TileEntity raw=world.getTileEntity(ROOT);return raw instanceof GearTileEntity?(GearTileEntity)raw:null;}
    private static boolean teleportPending(EntityPlayerMP actor){try{return TELEPORT.get(actor.connection)!=null;}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
    private static void location(EntityPlayerMP actor,Vec3d eye,Vec3d target){Vec3d d=target.subtract(eye);actor.connection.setPlayerLocation(eye.x,eye.y-actor.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));feet=new Vec3d(eye.x,eye.y-actor.getEyeHeight(),eye.z);aim=target;actor.motionX=actor.motionY=actor.motionZ=0;}
    private static void mode(EntityPlayerMP actor,GameType next){mode=next;ArmCoreFixture.emptyHands(actor,next);}
    private static TileEntityConstructor core(GearTileEntity arm){TileEntityConstructor found=null;for(TileEntity tile:arm.payload().view(arm).tiles.values())if(tile instanceof TileEntityConstructor){need(found==null,"exactly one carried machine owner");found=(TileEntityConstructor)tile;}need(found!=null,"one complete formed Dish owner");return found;}
    private static boolean held(ItemStack stack){return held==0?stack.isEmpty():stack.getItem()==(held==1?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.CONFIGURIZER)&&stack.getCount()==1;}
    private static void hand(EntityPlayerMP actor,int kind){held=kind;actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,kind==0?ItemStack.EMPTY:new ItemStack(kind==1?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.CONFIGURIZER));actor.inventoryContainer.detectAndSendChanges();}
    private static void next(int step){stage=step;ticks=0;sent=false;System.out.println("[vandorlabs][reprolab] carried-core-use stage="+step);}
    private static boolean ready(Minecraft mc)throws Exception{
        if(mc.playerController.getCurrentGameType()!=mode||mc.player.inventory.currentItem!=0||!held(mc.player.getHeldItemMainhand())||!mc.player.capabilities.isFlying||feet==null||mc.player.getDistanceSq(feet.x,feet.y,feet.z)>.02)return false;
        return mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);GearTileEntity arm=root(actor.world);return arm!=null&&!teleportPending(actor)&&actor.interactionManager.getGameType()==mode&&actor.inventory.currentItem==0&&held(actor.getHeldItemMainhand())&&!ArmTransactions.locked(actor.world,ROOT);}).get();
    }
    public static boolean tick(Minecraft mc)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;if(mc.currentScreen!=null&&stage<8)mc.displayGuiScreen(null);need(++ticks<350,"bounded actual click mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector()+" screen="+mc.currentScreen);
        if(ticks%80==0)System.out.println("[vandorlabs][reprolab] carried-core-use waiting stage="+stage+" "+state);
        if((stage==1||stage==4||stage==6||stage==8||stage==16)&&!sent&&ready(mc)){
            Vec3d eye=mc.player.getPositionEyes(1);RayTraceResult hit=GearInteractions.trace(mc.world,eye,eye.add(mc.player.getLook(1).scale(5)));
            if(hit!=null&&hit.getBlockPos().equals(ROOT)&&(stage==1||stage==16?!(hit.hitInfo instanceof ArmPayloadHarvest.Target):hit.hitInfo instanceof ArmPayloadHarvest.Target)){
                com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageModularGearUse(ROOT));sent=true;
            }
        }
        if(stage==9&&!sent&&mc.currentScreen instanceof com.vandorlabs.client.GuiNavigationTable){need(mc.player.openContainer instanceof ContainerCarriedCore,"Configurator opens the dedicated machine screen and exact configuration-only context");click(mc.currentScreen,1,0);java.lang.reflect.Field channels=com.vandorlabs.client.GuiNavigationTable.class.getDeclaredField("channels");channels.setAccessible(true);net.minecraft.client.gui.GuiTextField field=(net.minecraft.client.gui.GuiTextField)channels.get(mc.currentScreen);field.setFocused(true);field.setText("");java.lang.reflect.Method key=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class,"keyTyped","func_73869_a",char.class,int.class);for(char ch:"61,62".toCharArray())key.invoke(mc.currentScreen,ch,0);sent=true;}
        if(stage==11&&!sent&&mc.currentScreen instanceof com.vandorlabs.client.GuiNavigationTable){click(mc.currentScreen,1,1);sent=true;}
        if(stage==13&&!sent&&mc.currentScreen instanceof com.vandorlabs.client.GuiNavigationTable){click(mc.currentScreen,1,0);sent=true;}
        if(stage==15&&!sent){com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(new com.vandorlabs.network.MessageMachineDisplay(oldWindow,new com.vandorlabs.navigation.NavigationScreenSettings(-1,false,2,false,false),new int[0],127,0,com.vandorlabs.redstone.ChannelList.of(99)));sent=true;}
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP actor){
        if(stage==0){ArmCoreFixture.setup(world,actor,2,"standalone_dish_core");mode(actor,GameType.CREATIVE);location(actor,new Vec3d(ROOT).addVector(.5,.5,-3.5),new Vec3d(ROOT).addVector(.5,.5,.5));stage=1;ticks=0;return false;}
        GearTileEntity arm=root(world);need(arm!=null,"same physical arm controller");NavigationArmChecks.verify(arm);state="mode="+actor.interactionManager.getGameType()+" target="+arm.target()+" phase="+arm.progress()+" payload="+(arm.payload()==null?"none":arm.payload().materialized()?"native":"captured")+" lock="+ArmTransactions.locked(world,ROOT)+" held="+actor.getHeldItemMainhand()+" error="+arm.error();
        if(stage==1){if(!sent||ArmTransactions.locked(world,ROOT)||arm.progress()!=0||arm.payload()==null)return false;need(!arm.payload().materialized(),"actual rotate-level endpoint remains fractional/captured");need(!arm.target(),"arm stopped at the captured endpoint");mode(actor,GameType.SURVIVAL);TileEntityConstructor machine=core(arm);activity=machine.activity();aimBody(actor,arm,machine);stage=4;ticks=0;sent=false;return false;}
        if(stage==4){if(!sent)return false;stage=5;ticks=0;return false;}
        if(stage==5){if(ticks<4)return false;need(!arm.target()&&arm.progress()==0,"posed Core Part click is consumed and never toggles its arm");need(core(arm).activity()==(activity+1)%3,"ordinary empty-hand click cycles the carried Dish activity");hand(actor,1);next(6);return false;}
        if(stage==6){if(!sent)return false;next(7);return false;}
        if(stage==7){if(ticks<4)return false;need(!arm.target()&&arm.progress()==0&&actor.getHeldItemMainhand().getCount()==1,"chip transfer is safely consumed/refused without arm toggle or player consumption");need(core(arm).getStackInSlot(0).getCount()==1,"single arm-owned inserted chip retained");hand(actor,2);next(8);return false;}
        if(stage==8){if(!sent)return false;next(9);return false;}
        if(stage==9){if(!sent||!core(arm).screenSettings().redstone||!core(arm).getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(61,62)))return false;need(actor.openContainer instanceof ContainerCarriedCore&&actor.openContainer.inventorySlots.isEmpty(),"machine dialog has no transferable inventory slots");need(!arm.target()&&arm.progress()==0,"configuration leaves the arm stopped");next(11);return false;}
        if(stage==11){if(!sent||core(arm).screenSettings().redstone)return false;need(!arm.target(),"right-click previous activation applied to machine only");next(13);return false;}
        if(stage==13){if(!sent||!core(arm).screenSettings().redstone)return false;oldWindow=actor.openContainer.windowId;configured=arm.payload().write();NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(saved);arm.operator(actor);arm.sync();need(configured.equals(arm.payload().write()),"root save/load retains exact machine settings, chip, and opaque capabilities");need(!actor.openContainer.canInteractWith(actor),"replacement payload invalidates the old exact session");next(15);return false;}
        if(stage==15){if(!sent||ticks<4)return false;need(configured.equals(arm.payload().write()),"stale window cannot edit a reloaded machine snapshot");TileEntityConstructor machine=core(arm);NBTTagCompound own=arm.payload().blocks.at(machine.getPos()).tileData(),roundtrip=machine.writeToNBT(new NBTTagCompound());own.removeTag("ForgeCaps");roundtrip.removeTag("ForgeCaps");need(own.equals(roundtrip),"edited Core own fields remain exactly codec-representable before handback; differing keys="+different(own,roundtrip));hand(actor,0);location(actor,new Vec3d(ROOT).addVector(.5,.5,-3.5),new Vec3d(ROOT).addVector(.5,.5,.5));next(16);return false;}
        if(stage==16){if(!sent)return false;next(17);return false;}
        if(stage==17){need(ticks<40||arm.error().isEmpty(),"native handback refused: "+arm.error());if(ArmTransactions.locked(world,ROOT)||arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;TileEntityConstructor nativeCore=null;for(BlockPos at:arm.payload().placedCells(arm))if(world.getTileEntity(at) instanceof TileEntityConstructor)nativeCore=(TileEntityConstructor)world.getTileEntity(at);need(nativeCore!=null&&nativeCore.formed(),"complete native machine restored");need(nativeCore.activity()==(activity+1)%3&&nativeCore.screenSettings().redstone&&nativeCore.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(61,62)),"native handback retains activity and edited settings/channels");need(nativeCore.getStackInSlot(0).getCount()==1,"native handback preserves exactly one inserted chip");for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:arm.payload().blocks.cells)need(cell.tileData().getCompoundTag("ForgeCaps").getCompoundTag(ArmCoreFixture.CAPS.toString()).getInteger("Representation")==22,"physical opaque capability remains exact through operation/configuration");System.out.println("[vandorlabs][reprolab] arm-live PASS carried-core-use checks="+checks+" actualFractionalDishPart/Survival/activity/Configurator/settings/previous/chipRefusal/stale/reload/nativeHandback");return true;}
        return false;
    }
    private static void aimBody(EntityPlayerMP actor,GearTileEntity arm,TileEntityConstructor machine){
        VehicleWorld view=arm.payload().view(arm);
        for(com.vandorlabs.vehicle.VehicleStructure.Cell cell:arm.payload().blocks.cells)if(cell.state.getBlock() instanceof BlockConstructorPart){
            Vec3d center=arm.payload().transform(arm,arm.progress(),cell.pos.getX()+.5,cell.pos.getY()+.5,cell.pos.getZ()+.5).add(new Vec3d(ROOT));
            for(EnumFacing side:EnumFacing.values()){Vec3d eye=center.add(new Vec3d(side.getDirectionVec()).scale(3));AxisAlignedBB body=new AxisAlignedBB(eye.x-.3,eye.y-actor.getEyeHeight(),eye.z-.3,eye.x+.3,eye.y-actor.getEyeHeight()+actor.height,eye.z+.3);if(!worldClear(actor,body))continue;
                RayTraceResult hit=GearInteractions.trace(actor.world,eye,center);if(hit==null||!(hit.hitInfo instanceof ArmPayloadHarvest.Target))continue;BlockPos local=((ArmPayloadHarvest.Target)hit.hitInfo).cell;if(!(view.getTileEntity(local) instanceof TileEntityConstructorPart))continue;
                Vec3d origin=arm.payload().transform(arm,arm.progress(),0,0,0).add(new Vec3d(ROOT)),a=arm.payload().transform(arm,arm.progress(),1,0,0).add(new Vec3d(ROOT)).subtract(origin),b=arm.payload().transform(arm,arm.progress(),0,1,0).add(new Vec3d(ROOT)).subtract(origin),c=arm.payload().transform(arm,arm.progress(),0,0,1).add(new Vec3d(ROOT)).subtract(origin),s=eye.subtract(origin),e=hit.hitVec.subtract(origin);
                MachineMountSurface mount=machine.mount();if(mount!=null&&mount.hit(new Vec3d(s.dotProduct(a),s.dotProduct(b),s.dotProduct(c)),new Vec3d(e.dotProduct(a),e.dotProduct(b),e.dotProduct(c))))continue;
                need(((TileEntityConstructorPart)view.getTileEntity(local)).owner().equals(machine.getPos()),"actual posed Part resolves the unique machine owner");location(actor,eye,hit.hitVec);return;
            }
        }throw new IllegalStateException("No unobstructed actual Dish Part body ray");
    }
    private static void click(net.minecraft.client.gui.GuiScreen screen,int id,int mouse)throws Exception{java.lang.reflect.Field buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n");for(Object raw:(java.util.List<?>)buttons.get(screen)){net.minecraft.client.gui.GuiButton button=(net.minecraft.client.gui.GuiButton)raw;if(button.id==id){need(button.enabled&&button.visible,"actual machine control available "+id);java.lang.reflect.Method method=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class);method.invoke(screen,button.x+button.width/2,button.y+button.height/2,mouse);return;}}throw new IllegalStateException("Machine control absent "+id);}
    private static String different(NBTTagCompound a,NBTTagCompound b){java.util.Set<String> keys=new java.util.TreeSet<>(a.getKeySet());keys.addAll(b.getKeySet());java.util.List<String> diff=new java.util.ArrayList<>();for(String key:keys)if(!java.util.Objects.equals(a.getTag(key),b.getTag(key)))diff.add(key);return diff.toString();}
    private static boolean worldClear(EntityPlayerMP actor,AxisAlignedBB body){return actor.world.getCollisionBoxes(actor,body).isEmpty()&&actor.world.checkNoEntityCollision(body,actor);}
    private ArmCoreUseLiveChecks(){}
}
