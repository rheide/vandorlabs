package com.vandorlabs.gear;

import com.vandorlabs.blocks.BlockTurntableSurface;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Normal placement/interaction and real asynchronous journals, separate from matrix shortcuts. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ArmImprovementsLiveChecks {
    private static int fixture,stage,ticks,started;
    private static boolean matricesDone,activeEdited;
    private static final EnumFacing[] FACES={EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.UP,EnumFacing.EAST,EnumFacing.UP,EnumFacing.EAST,EnumFacing.UP,EnumFacing.UP,EnumFacing.UP,EnumFacing.UP,EnumFacing.NORTH,EnumFacing.EAST,EnumFacing.SOUTH,EnumFacing.WEST};
    private static boolean joined(){return fixture>=8&&fixture<12;}
    private static int payloadCount(){return joined()?25:2;}
    private static boolean push(){return fixture<4||fixture>=12;}
    private static EnumFacing face(){return FACES[fixture];}
    private static BlockPos root(){return new BlockPos(16+fixture*24,104,32);}
    private static GearBlock block(){return push()?(fixture>=12?GearRegistry.GEAR_EXTENDER:GearRegistry.LARGE_GEAR_EXTENDER):new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM}[(fixture-4)%4];}
    private static Block payload(){return push()?Blocks.STONE:Block.getBlockFromName("vandorlabs:turntable_surface");}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("arm-improvements fixture="+fixture+" stage="+stage+" tick="+ticks+": "+message);}
    private static EnumFacing side(int x,int z){BlockPos vector=ArmOrientation.vector(face(),new BlockPos(x,0,z));return EnumFacing.getFacingFromVector(vector.getX(),vector.getY(),vector.getZ());}
    private static BlockPos placed(GearTileEntity arm,BlockPos construction){BlockPos local=construction.subtract(arm.getPos()).subtract(arm.payload().offset);return new BlockPos(arm.payload().transform(arm,0,local.getX()+.5,local.getY()+.5,local.getZ()+.5).add(new net.minecraft.util.math.Vec3d(arm.getPos())));}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(!com.vandorlabs.client.GearActivationLiveChecks.tick(mc))return false;
        if(++ticks<60)return false;
        if(!matricesDone){mc.getIntegratedServer().callFromMainThread(()->{com.vandorlabs.client.Mechanism27RuntimeChecks.server(actor(mc).getServerWorld(),actor(mc));return null;}).get();matricesDone=true;return false;}
        if(fixture>=16){com.vandorlabs.client.Mechanism27RuntimeChecks.client(mc,new java.io.File(System.getProperty("vandorlabs.reprolab")));System.out.println("[vandorlabs][reprolab] arm-improvements PASS exhaustive-mechanism-matrices analog-ramp stationary-doors tank-storage-cache rendered-tanks tabs angle four-horizontal-push-no-pull Surface-layer-real-async-journals reload reversal mining");return true;}
        BlockPos root=root(),source=root.offset(face(),2),across=source.add(ArmOrientation.vector(face(),new BlockPos(1,0,0)));
        if(stage==0){mc.displayGuiScreen(null);mc.getIntegratedServer().callFromMainThread(()->{
            EntityPlayerMP p=actor(mc);WorldServer world=p.getServerWorld();p.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.currentItem=0;
            for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5))){BlockPos d=at.subtract(root);int depth=d.getX()*face().getFrontOffsetX()+d.getY()*face().getFrontOffsetY()+d.getZ()*face().getFrontOffsetZ();world.setBlockState(at,depth==-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
            p.connection.setPlayerLocation(root.getX()+(joined()?-2.5:3.5),root.getY()+2,root.getZ()+(joined()?-2.5:3.5),135,0);
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));
            for(BlockPos support:new BlockPos[]{root.offset(face().getOpposite()),root})require(p.getHeldItemMainhand().onItemUse(p,world,support,EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary supported arm placement");
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(payload(),2));require(p.getHeldItemMainhand().onItemUse(p,world,root.offset(face()),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary payload placement");
            if(push()){p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.IRON_BLOCK));require(p.getHeldItemMainhand().onItemUse(p,world,source,EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary obstacle placement");}
            else world.setBlockState(across,world.getBlockState(source).withProperty(BlockTurntableSurface.FACING,face()),2);
            if(joined()){
                for(int dx:new int[]{0,2})for(int dz:fixture>=10?new int[]{0}:new int[]{0,2}){
                    BlockPos base=root.add(dx,0,dz);
                    if(dx!=0||dz!=0){p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));for(BlockPos support:new BlockPos[]{base.down(),base})require(p.getHeldItemMainhand().onItemUse(p,world,support,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary joined arm placement");}
                    GearTileEntity member=(GearTileEntity)world.getTileEntity(base);member.operator(p);require(member.rebuild(),"joined column forms");NBTTagCompound options=member.settings();options.setBoolean("JoinArms",true);member.configure(options);
                }
                for(int dx=-1;dx<=3;dx++)for(int dz=-1;dz<=3;dz++)world.setBlockState(root.add(dx,2,dz),payload().getDefaultState(),3);
            }
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(root);arm.operator(p);require(arm.rebuild(),"normal stack forms");if(joined())require(arm.columns().size()==(fixture>=10?2:4),"all compatible support columns share one assembly");NBTTagCompound settings=arm.settings();settings.setBoolean("PushBlocks",push());settings.setBoolean("RotatePayload",fixture==7);arm.configure(settings);
            p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();return null;
        }).get();stage=1;started=ticks;return false;}
        if(stage==1||stage==3||stage==5||stage==7){
            require(ticks-started<240,"client sync mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition());
            if(mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(root.getX()+(joined()?-2.5:3.5),root.getY()+2,root.getZ()+(joined()?-2.5:3.5))>1||mc.world.getBlockState(root).getBlock()!=block())return false;
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(root,face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));stage++;started=ticks;return false;
        }
        return mc.getIntegratedServer().callFromMainThread(()->{
            EntityPlayerMP p=actor(mc);WorldServer world=p.getServerWorld();GearTileEntity arm=(GearTileEntity)world.getTileEntity(root);require(arm!=null,"root survives");
            require(ticks-started<400,"real asynchronous journal completes progress="+arm.progress()+" target="+arm.target()+" error="+arm.error());
            if(stage==2){if(arm.progress()!=0||ArmTransactions.locked(world,root))return false;
                require(!arm.target()&&arm.payload()!=null&&arm.payload().blocks.cells.size()==payloadCount(),"pending journal retains requested action and both payload cells");
                if(push())require(world.isAirBlock(source)&&world.getBlockState(source.offset(face())).getBlock()==Blocks.STONE&&world.getBlockState(source.offset(face(),2)).getBlock()==Blocks.IRON_BLOCK,"push publishes chain as native cells exactly once");
                NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(saved);arm.operator(p);arm.onLoad();stage=3;started=ticks;return false;
            }
            BlockPos third=across.add(ArmOrientation.vector(face(),new BlockPos(1,0,0))),corner=across.add(ArmOrientation.vector(face(),new BlockPos(0,0,1)));
            if(stage==6){
                if(arm.progress()!=0||ArmTransactions.locked(world,root))return false;
                require(arm.payload()!=null&&arm.payload().blocks.cells.size()==3,"Surface added at native rest endpoint joins the next real stroke");
                if(arm.payload().materialized()){
                    BlockPos at=placed(arm,third),support=placed(arm,across);require(world.getBlockState(at).getBlock()==payload()&&p.interactionManager.tryHarvestBlock(at)&&world.isAirBlock(at),"mine newly joined Surface at native active endpoint");
                    p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(payload()));require(p.getHeldItemMainhand().onItemUse(p,world,support,EnumHand.MAIN_HAND,side(0,1),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"add connected Surface at native active endpoint");p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();activeEdited=true;
                }
                stage=7;started=ticks;return false;
            }
            if(arm.progress()!=1||ArmTransactions.locked(world,root))return false;
            if(push())require(world.isAirBlock(source)&&world.getBlockState(source.offset(face())).getBlock()==Blocks.STONE&&world.getBlockState(source.offset(face(),2)).getBlock()==Blocks.IRON_BLOCK,"retraction never pulls either pushed block");
            else require(world.getBlockState(source).getBlock()==payload()&&world.getBlockState(across).getBlock()==payload(),"surface layer returns to exact native source ownership");
            if(stage==4&&!push()&&!joined()){
                p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(payload()));require(p.getHeldItemMainhand().onItemUse(p,world,across,EnumHand.MAIN_HAND,side(1,0),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"add connected Surface at native rest endpoint");require(world.getBlockState(third).getBlock()==payload(),"third native Surface is real before activation");p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();stage=5;started=ticks;return false;
            }
            if(stage==8){BlockPos extra=activeEdited?corner:third;require(world.getBlockState(extra).getBlock()==payload(),"endpoint Surface edits return as native world blocks");if(activeEdited)require(world.isAirBlock(third),"mined active endpoint Surface is not resurrected");require(p.interactionManager.tryHarvestBlock(extra)&&world.isAirBlock(extra),"edited Surface remains normally mineable after reversal");}
            if(joined())for(int dx=-1;dx<=3;dx++)for(int dz=-1;dz<=3;dz++)require(world.getBlockState(root.add(dx,2,dz)).getBlock()==payload(),"all 25 joined Surface cells return as native blocks");
            BlockPos mined=push()?source.offset(face()):across;require(p.interactionManager.tryHarvestBlock(mined)&&world.isAirBlock(mined),"native endpoint remains normally mineable");
            System.out.println("[vandorlabs][reprolab] arm-improvements case PASS fixture="+fixture+" normal-placement real-click asynchronous-journal native-owner reload reversal mining");
            fixture++;stage=0;activeEdited=false;started=ticks;
            if(fixture<16)return false;
            System.out.println("[vandorlabs][reprolab] arm-improvements asynchronous-cases complete");return false;
        }).get();
    }
    private ArmImprovementsLiveChecks(){}
}
