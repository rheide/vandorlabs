package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Real world ticks and disk journals: deliberately never enters ArmTransactions.matrix(). */
@SideOnly(Side.CLIENT)
public final class ArmLiveRuntimeChecks {
    private static final GearBlock[] BLOCKS={GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM};
    private static int ticks,stage,started,fixture=Integer.getInteger("vandorlabs.armLiveStart",0);
    private static boolean savedDone,followupDone,replacementDone;
    // Start with the reported floor-mounted stack. Other axes keep their mounting plane too.
    private static final EnumFacing[] FACES={EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.WEST,EnumFacing.EAST};
    private static BlockPos root(){return new BlockPos(15+fixture*32,100,0);}
    private static EnumFacing face(){return fixture>=26?FACES[(fixture-26)%6]:fixture<24?FACES[fixture%6]:EnumFacing.EAST;}
    private static GearBlock block(){return fixture>=26?BLOCKS[((fixture-26)/6)]:fixture<24?BLOCKS[fixture/6]:BLOCKS[(fixture-24)*2];}
    private static net.minecraft.block.Block payloadBlock(){return fixture==24||fixture==25?Blocks.CHEST:Blocks.STONE;}
    private static java.util.Set<BlockPos> surroundings(){
        java.util.Set<BlockPos> cells=new java.util.LinkedHashSet<>();if(fixture<26)return cells;
        BlockPos source=root().offset(face(),2);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=2;z++)if(x!=0||z!=0)cells.add(source.add(ArmOrientation.vector(face(),new BlockPos(x,0,z))));
        for(int depth=0;depth<2;depth++)cells.add(root().offset(face(),depth).add(ArmOrientation.vector(face(),new BlockPos(1,0,0))));
        return cells;
    }
    private static void surroundingsIntact(WorldServer world){for(BlockPos at:surroundings())require(world.getBlockState(at).getBlock()==Blocks.COBBLESTONE&&!ArmTransactions.locked(world,at),"surrounding structure stays in place and unlocked at "+at);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-live fixture="+fixture+" face="+face()+" stage="+stage+" tick="+ticks+": "+why);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(Boolean.getBoolean("vandorlabs.armImprovementsOnly"))return ArmImprovementsLiveChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armWallOnly"))return ArmWallRuntimeChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.mekanismTankOnly"))return com.vandorlabs.vehicle.MekanismTankLiveChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armPlacementOnly"))return com.vandorlabs.client.ArmPlacementLiveChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armRiderOnly"))return ArmRiderRuntimeChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armJoinedOnly"))return JoinedArmRuntimeChecks.tick(mc);
        if(Boolean.getBoolean("vandorlabs.armMiningOnly"))return ArmPayloadMiningRuntimeChecks.tick(mc);
        if(fixture>=38){String second=System.getProperty("vandorlabs.armSecondFixture","");if(!replacementDone&&!second.isEmpty()){replacementDone=mc.getIntegratedServer().callFromMainThread(()->ArmReplacementRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0),second)).get();return false;}return ArmPayloadMiningRuntimeChecks.tick(mc);}
        if(stage==0||stage==1)mc.displayGuiScreen(null);if(++ticks<80)return false;
        if(!followupDone){mc.getIntegratedServer().callFromMainThread(()->{ArmFollowupRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();followupDone=true;return false;}
        String saved=System.getProperty("vandorlabs.armSavedFixture","");
        if(!savedDone&&!saved.isEmpty()){savedDone=mc.getIntegratedServer().callFromMainThread(()->ArmSavedRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0),saved)).get();return false;}
        BlockPos root=root(),source=root.offset(face(),2);
        if(stage==0){mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer w=mc.getIntegratedServer().getWorld(0);EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            p.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.currentItem=0;p.connection.setPlayerLocation(root.getX()+2.5,100,2.5,135,0);
            for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5))){BlockPos d=at.subtract(root);int depth=d.getX()*face().getFrontOffsetX()+d.getY()*face().getFrontOffsetY()+d.getZ()*face().getFrontOffsetZ();w.setBlockState(at,depth==-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block(),2));
            for(BlockPos support:new BlockPos[]{root.offset(face().getOpposite()),root})require(p.getHeldItemMainhand().onItemUse(p,w,support,EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary arm placement");
            p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(payloadBlock()));require(p.getHeldItemMainhand().onItemUse(p,w,root.offset(face()),EnumHand.MAIN_HAND,face(),.5F,.5F,.5F)==EnumActionResult.SUCCESS,"ordinary payload placement");if(fixture==24||fixture==25)((net.minecraft.inventory.IInventory)w.getTileEntity(source)).setInventorySlotContents(0,new ItemStack(net.minecraft.init.Items.DIAMOND,37));for(BlockPos at:surroundings())w.setBlockState(at,Blocks.COBBLESTONE.getDefaultState(),2);p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();return null;
        }).get();stage=1;started=ticks;return false;}
        if(stage==1){
            require(ticks-started<200,"client fixture sync mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPosition());
            if(mc.playerController.getCurrentGameType()!=GameType.CREATIVE||mc.player.inventory.currentItem!=0||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getDistanceSq(root.getX()+2.5,100,2.5)>1||mc.world.getBlockState(source).getBlock()!=payloadBlock())return false;
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(fixture%2==0?root:root.offset(face()),face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));stage=2;started=ticks;return false;
        }
        if(stage==4){
            require(ticks-started<200,"Configurizer opens after completed motion; held="+mc.player.getHeldItemMainhand());
            if(mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER)return false;
            mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(root,face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));stage=5;return false;
        }
        if(stage==5){require(ticks-started<200,"client receives arm configuration dialog");if(!(mc.currentScreen instanceof com.vandorlabs.client.GuiModularGear))return false;}
        if(stage==6){mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(root,face(),EnumHand.MAIN_HAND,.5F,.5F,.5F));stage=3;started=ticks;return false;}
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer w=mc.getIntegratedServer().getWorld(0);GearTileEntity arm=(GearTileEntity)w.getTileEntity(root);EntityPlayerMP p=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            require(arm!=null,"root survives");
            if(stage==2){if(ticks-started<10)return false;require(!arm.target(),"single click must retain retract request while journal is pending; progress="+arm.progress()+" error="+arm.error());
                require(ticks-started<240,"retraction completes: "+arm.error());if(arm.progress()!=0||ArmTransactions.locked(w,root)||block().kind()==GearBlock.Kind.EXTENDER&&(arm.payload()==null||!arm.payload().materialized()))return false;
                require(w.isAirBlock(source)&&arm.payload()!=null,"payload travels with arm exactly once");
                if(fixture>=26){require(arm.payload().blocks.cells.size()==1,"unjoined extender captures only its tip block");surroundingsIntact(w);net.minecraft.nbt.NBTTagCompound persisted=arm.writeToNBT(new net.minecraft.nbt.NBTTagCompound());arm.readFromNBT(persisted);arm.operator(p);arm.onLoad();require(arm.payload()!=null&&arm.payload().blocks.cells.size()==1,"saved retracted payload survives tile reload");}
                if(block().kind()==GearBlock.Kind.EXTENDER)require(arm.payload().materialized(),"aligned retracted payload becomes a normal block");
                AxisAlignedBB box=arm.collisionBoxes().get(0);Vec3d aim=new Vec3d((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
                Vec3d endOn=aim.add(new Vec3d(face().getDirectionVec()).scale(4));RayTraceResult nativeHit=w.rayTraceBlocks(endOn,aim,false,true,false);
                require(GearInteractions.trace(w,endOn,aim)!=null||arm.payload().materialized()&&nativeHit!=null&&arm.payload().placedCells(arm).contains(nativeHit.getBlockPos()),"end-on ray selects the arm or its nearer native payload");
                require(GearInteractions.trace(w,aim.add(ArmOrientation.vector(face(),-4,0,0)),aim)!=null,"exposed side of retracted arm remains selectable");
                if(fixture==24||fixture==25){net.minecraft.inventory.IInventory inventory=(net.minecraft.inventory.IInventory)arm.payload().view(arm).getTileEntity(BlockPos.ORIGIN);require(inventory.getStackInSlot(0).getCount()==37,"captured chest retains 37 diamonds");}
                stage=6;started=ticks;return false;}
            if(stage==3){require(ticks-started<240,"reverse and journal retirement complete: "+arm.error());if(arm.progress()!=1||ArmTransactions.locked(w,root)||ArmTransactions.locked(w,source))return false;
                require(w.getBlockState(source).getBlock()==payloadBlock(),"reverse restores payload");
                surroundingsIntact(w);if(fixture==24||fixture==25)require(((net.minecraft.inventory.IInventory)w.getTileEntity(source)).getStackInSlot(0).getCount()==37,"restored chest owns 37 diamonds once");
                p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();stage=4;started=ticks;return false;}
            require(ticks-started<200,"server opens arm configuration after motion");if(!(p.openContainer instanceof com.vandorlabs.container.ContainerModularGear))return false;
            p.closeScreen();p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            require(p.interactionManager.tryHarvestBlock(source)&&w.isAirBlock(source),"payload can be mined after movement");
            require(p.interactionManager.tryHarvestBlock(root)&&w.isAirBlock(root),"arm can be dismantled after movement");
            System.out.println("[vandorlabs] arm-live case PASS fixture="+fixture+" block="+block().getRegistryName()+" face="+face());
            fixture++;stage=0;started=ticks;
            if(fixture<38)return false;
            System.out.println("[vandorlabs] arm-live PASS cases=38 real click mounted stacks six axes regular/Large extender/rotating payload reversal Configurizer mining real ticks disk journals unlocked chest inventories; twelve unjoined 3x4 surroundings retained tile reloads");return false;
        }).get();
    }
    private ArmLiveRuntimeChecks(){}
}
