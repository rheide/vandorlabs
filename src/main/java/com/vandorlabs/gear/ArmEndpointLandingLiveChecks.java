package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Isolated reproduction: place a landing Plate in an aligned, activated rotating tip. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmEndpointLandingLiveChecks {
    private static final BlockPos ROOT=new BlockPos(1200,150,100);
    private static int stage,ticks,started;
    private static boolean clicked,activeBound;
    private static BlockPos placed;
    private static void require(boolean ok,String detail){if(!ok)throw new IllegalStateException("arm-endpoint-landing stage="+stage+" tick="+ticks+": "+detail);}
    public static boolean tick(Minecraft mc)throws Exception{
        ticks++;mc.displayGuiScreen(null);
        if(stage==1||stage==3){
            require(ticks-started<300,"client mode/slot/held/position synchronized mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand());
            if(!clicked&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(ROOT.getX()+3.5,ROOT.getY()+2,ROOT.getZ()-2.5)<1&&mc.world.getTileEntity(ROOT) instanceof GearTileEntity){mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(ROOT,EnumFacing.DOWN,EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;}
            if(!clicked)return false;
        }
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(stage==0){ArmActiveLanding.testObserver=message->System.out.println("[vandorlabs][reprolab] arm-endpoint-landing trace "+message);ArmEndpointLandingCaps.enable();ArmActiveLanding.testBound=bound->{if(bound.getPos().equals(ROOT)){require(bound.progress()==0&&bound.isLandingAssembly()&&world.getTileEntity(ROOT.down(2)) instanceof GearTileEntity&&((GearTileEntity)world.getTileEntity(ROOT.down(2))).loadedController()==bound,"actual native landing owner binds while still active phase="+bound.progress()+" landing="+bound.isLandingAssembly()+" foot="+world.getTileEntity(ROOT.down(2))+" owner="+(world.getTileEntity(ROOT.down(2)) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(ROOT.down(2))).owner():null)+" controller="+(world.getTileEntity(ROOT.down(2)) instanceof GearTileEntity?((GearTileEntity)world.getTileEntity(ROOT.down(2))).loadedController():null)+" bound="+bound+" live="+world.getTileEntity(ROOT)+" members="+bound.members());activeBound=true;}};
                for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-6,-6,-6),ROOT.add(6,6,6))){world.removeTileEntity(at);world.setBlockToAir(at);}
                actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;world.setBlockState(ROOT.up(),Blocks.STONE.getDefaultState(),2);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_SWING_ARM,2));
                for(int n=0;n<2;n++)require(actor.getHeldItemMainhand().onItemUse(actor,world,ROOT.up(1-n),EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported arm placement");
                GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);arm.operator(actor);require(arm.rebuild(),"bare column forms");net.minecraft.nbt.NBTTagCompound settings=arm.settings();settings.setBoolean("RotatePayload",true);arm.configure(settings);
                actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();actor.connection.setPlayerLocation(ROOT.getX()+3.5,ROOT.getY()+2,ROOT.getZ()-2.5,0,0);stage=1;started=ticks;return false;
            }
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            if(stage==1||stage==3){stage++;started=ticks;return false;}
            if(stage==4&&(ticks-started)%80==0)System.out.println("[vandorlabs][reprolab] arm-endpoint-landing poll kind="+arm.assembly().kind()+" operational="+arm.isOperational()+" awaiting="+com.vandorlabs.tiles.WorldActionAuthorization.awaitingLoad(arm)+" "+ArmTransactions.diagnostic(world,ROOT));require(ticks-started<400,"requested motion completes progress="+arm.progress()+" target="+arm.target()+" error="+arm.error()+" "+ArmTransactions.diagnostic(world,ROOT));
            if(ArmTransactions.locked(world,ROOT))return false;
            if(stage==2){if(arm.progress()!=0)return false;ArmPayload.Endpoint endpoint=ArmPayload.endpoint(arm,0);require(endpoint!=null,"activated endpoint has an exact grid mapping");placed=endpoint.placed(arm.localCell(0,-arm.assembly().columnHeight(),0));require(world.isAirBlock(placed),"activated attachment cell is empty");
                require(((net.minecraft.item.ItemBlock)net.minecraft.item.Item.getItemFromBlock(GearRegistry.LANDING_PLATE)).placeBlockAt(new ItemStack(GearRegistry.LANDING_PLATE),actor,world,placed,EnumFacing.UP,.5F,.5F,.5F,GearRegistry.LANDING_PLATE.getDefaultState()),"place native Plate at activated attachment cell");stage=3;started=ticks;clicked=false;return false;
            }
            if(arm.error().contains("cannot rotate back")&&"true".equals(System.getenv("VANDOR_ARMS_210_EXPECT_ENDPOINT_REFUSAL"))){require(world.getBlockState(placed).getBlock()==GearRegistry.LANDING_PLATE,"refused baseline preserves the Plate");System.out.println("[vandorlabs][reprolab] arm-endpoint-landing BASELINE-REFUSAL reproduced exact-grid Plate preserved error="+arm.error());System.out.println("[vandorlabs][reprolab] utility26-focused PASS arms-210-endpoint-baseline");return true;}
            if(arm.progress()!=1)return false;
            require(world.getBlockState(ROOT.down(2)).getBlock()==GearRegistry.LANDING_PLATE,"attached Plate follows reversal to native source endpoint");GearTileEntity foot=(GearTileEntity)world.getTileEntity(ROOT.down(2));require(activeBound&&arm.isLandingAssembly()&&arm.payload()==null&&foot.loadedController()==arm&&foot.mountDirection()==arm.mountDirection()&&foot.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getCompoundTag("ForgeCaps").getCompoundTag(ArmEndpointLandingCaps.KEY.toString()).getInteger("Representation")==22,"restored Plate retains exact physical frame and native capability representation");ArmActiveLanding.testBound=null;ArmActiveLanding.testObserver=null;System.out.println("[vandorlabs][reprolab] arm-endpoint-landing PASS aligned activated Plate actual native landing binding active/rest real journals");System.out.println("[vandorlabs][reprolab] utility26-focused PASS arms-210-endpoint");return true;
        }).get();
    }
    private ArmEndpointLandingLiveChecks(){}
}
