package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Changing the source endpoint uses the same journal and collision path as an ordinary stroke. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GearStartLiveChecks {
    private static final int FIRST=Integer.getInteger("vandorlabs.armStartFixture",0);
    private static final int LIMIT=Integer.getInteger("vandorlabs.armStartLimit",48);
    private static int fixture=FIRST,stage,ticks;
    private static BlockPos obstruction;
    private static final BlockPos ROOT=new BlockPos(620,170,260);
    private static BlockPos scale(BlockPos p,int n){return new BlockPos(p.getX()*n,p.getY()*n,p.getZ()*n);}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("start-angle fixture="+fixture+" stage="+stage+" ticks="+ticks+": "+reason);}
    private static void dialog(WorldServer world,EntityPlayerMP actor,GearTileEntity arm,String client,String reason){
        if(com.vandorlabs.container.ContainerModularGear.resolve(actor,world,ROOT.getX(),ROOT.getY(),ROOT.getZ())!=null)return;
        net.minecraft.tileentity.TileEntity raw=world.getTileEntity(ROOT);
        GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;
        require(false,reason+" raw="+(raw==null?"null":raw.getClass().getSimpleName()+"@"+raw.getPos()+" invalid="+raw.isInvalid())
            +" rawOwner="+(raw instanceof GearTileEntity?((GearTileEntity)raw).owner():null)+" controller="+(root==null?"null":root.getPos()+" owner="+root.owner()+" operational="+root.isOperational()+" mode="+root.mode()+" progress="+root.progress()+" target="+root.target()+" start="+root.startAngle()+" physicalStart="+root.physicalStartAngle()+" end="+root.configuredEndAngle()+" payload="+(root.payload()==null?"none":root.payload().materialized()?"native":"captured"))
            +" actor="+actor.getPositionVector()+" distanceSq="+actor.getDistanceSq(ROOT)+" mode="+actor.interactionManager.getGameType()+" flying="+actor.capabilities.isFlying+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand()
            +" rootLocked="+ArmTransactions.locked(world,ROOT)+" controllerLocked="+(root!=null&&ArmTransactions.locked(world,root.getPos()))
            +" accessAllowed="+com.vandorlabs.items.ConfigurationAccess.canKeepDialogOpen(actor,world,ROOT)+" armIdentity="+(raw==arm)+" client="+client);
    }
    public static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){require(FIRST>=0&&LIMIT>FIRST&&LIMIT<=48,"fixture range must satisfy 0 <= start < limit <= 48; start="+FIRST+" limit="+LIMIT);mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ticks<80)return false;
        boolean clientReady=mc.playerController.getCurrentGameType()==net.minecraft.world.GameType.CREATIVE&&mc.player.capabilities.isFlying&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(ROOT.getX()+.5,ROOT.getY()+4,ROOT.getZ()+.5)<1;
        String client="position="+mc.player.getPositionVector()+" distanceSq="+mc.player.getDistanceSq(ROOT)+" mode="+mc.playerController.getCurrentGameType()+" flying="+mc.player.capabilities.isFlying+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand();
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(fixture==LIMIT){System.out.println("[vandorlabs][reprolab] arm-start PASS range=["+FIRST+","+LIMIT+") cases="+(LIMIT-FIRST)+" regular-Large six-mounts both-payload-modes joined-independent blocked-Start-refusal retry Surface native-payload source-retarget journal motion reversal reload endpoint-pose");return true;}
            EnumFacing face=EnumFacing.values()[fixture%6];GearBlock block=fixture%12<6?GearRegistry.GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_SWING_ARM;
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            if(stage==0){
                actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();
                actor.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+4,ROOT.getZ()+.5,0,0);stage=-1;ticks=80;return false;
            }
            if(stage==-1){
                require(ticks<320,"fixture client placement synchronization: "+client+" actor="+actor.getPositionVector()+" mode="+actor.interactionManager.getGameType()+" flying="+actor.capabilities.isFlying+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand());
                if(!clientReady)return false;
                for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-7,-7,-7),ROOT.add(7,7,7)))world.setBlockToAir(at);
                world.setBlockState(ROOT.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),3);
                for(int n=0;n<2;n++){BlockPos at=ROOT.offset(face,n);require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,at,face,.5F,.5F,.5F,block.getDefaultState()),"ordinary arm placement");}
                arm=(GearTileEntity)world.getTileEntity(ROOT);arm.operator(actor);require(arm.rebuild(),"native arm forms "+arm.error());
                NBTTagCompound options=arm.settings();options.setBoolean("RotatePayload",fixture%24<12);arm.configure(options);arm.rebuild();
                net.minecraft.block.Block payload=fixture>=24?net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface"):Blocks.IRON_BLOCK;
                world.setBlockState(ROOT.offset(face,2),payload.getDefaultState(),3);
                if(fixture>=24){
                    BlockPos unit=ArmOrientation.vector(face,new BlockPos(1,0,0));if(unit.compareTo(BlockPos.ORIGIN)<0)unit=scale(unit,-1);
                    BlockPos side=scale(unit,2);
                    BlockPos second=ROOT.add(side);world.setBlockState(second.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),3);
                    for(int n=0;n<2;n++)require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,second.offset(face,n),face,.5F,.5F,.5F,block.getDefaultState()),"ordinary joined placement");
                    for(BlockPos base:new BlockPos[]{ROOT,second}){GearTileEntity member=(GearTileEntity)world.getTileEntity(base);member.operator(actor);require(member.rebuild(),"joined arm forms");options=member.settings();options.setBoolean("JoinArms",true);options.setBoolean("RotatePayload",fixture%24<12);member.configure(options);}
                    for(int n=0;n<=2;n++)world.setBlockState(ROOT.offset(face,2).add(scale(unit,n)),payload.getDefaultState(),3);
                    require(arm.rebuild()&&arm.columns().size()==2,"joined line shares one root");
                }
                options=arm.settings();options.setDouble("StartAngle",90);options.setDouble("ActiveAngle",-90);arm.configure(options);
                // The obstacle is at the requested destination, clear of the source.
                // The original implementation takes several steps before noticing it.
                com.vandorlabs.gear.core.GearMotion.Bounds end=arm.pose(0).transformFootBounds(new com.vandorlabs.gear.core.GearMotion.Bounds(0,-2,0,1,-1,1));
                net.minecraft.util.math.AxisAlignedBB box=ArmOrientation.box(face,new net.minecraft.util.math.AxisAlignedBB(end.minX,end.minY,end.minZ,end.maxX,end.maxY,end.maxZ)).offset(ROOT);
                obstruction=new BlockPos((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
                require(!arm.members().contains(obstruction)&&!obstruction.equals(ROOT.offset(face,2)),"late obstacle is outside source and arm cells");
                world.setBlockState(obstruction,Blocks.BEDROCK.getDefaultState(),3);
                stage=10;ticks=80;return false;
            }
            require(ticks<1000,"motion timeout progress="+arm.progress()+" target="+arm.target()+" start="+arm.physicalStartAngle()+" pose="+arm.pose(arm.progress()).angleDegrees()+" error="+arm.error());
            if(stage==10){
                require(arm.progress()==1,"blocked Start must refuse the complete adjustment before taking any step");
                if(!arm.target()||ArmTransactions.locked(world,ROOT)||arm.payload()!=null&&!arm.payload().materialized())return false;
                require(arm.physicalStartAngle()==0&&arm.startAngle()==0&&arm.configuredEndAngle()==-90,"refused Start clears adjustment and retains old endpoints");
                require(world.getBlockState(ROOT.offset(face,2)).getBlock()==(fixture>=24?net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface"):Blocks.IRON_BLOCK),"refusal restores original native attachment");
                dialog(world,actor,arm,client,"refused Start leaves configuration accessible");
                world.setBlockToAir(obstruction);NBTTagCompound options=arm.settings();options.setDouble("StartAngle",90);arm.configure(options);stage=1;return false;
            }
            if(stage==1&&arm.physicalStartAngle()==90&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(arm.pose(1).angleDegrees()==90&&arm.pose(0).angleDegrees()==-90,"separate configured endpoints");
                NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());GearTileEntity reloaded=(GearTileEntity)net.minecraft.tileentity.TileEntity.create(world,saved);require(reloaded!=null&&reloaded.startAngle()==90&&reloaded.configuredEndAngle()==-90,"saved endpoints survive reload");
                NBTTagCompound options=arm.settings();options.setDouble("StartAngle",45);arm.configure(options);stage=20;return false;
            }
            if(stage==20&&arm.physicalStartAngle()==45&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(arm.payload()!=null&&!arm.payload().materialized(),"arbitrary 45 degree pose keeps one captured attachment");
                dialog(world,actor,arm,client,"dialog remains available at arbitrary stationary angle");
                world.setBlockState(obstruction,Blocks.BEDROCK.getDefaultState(),3);
                NBTTagCompound options=arm.settings();options.setDouble("StartAngle",90);arm.configure(options);stage=21;return false;
            }
            if(stage==21){
                require(arm.progress()==1,"blocked adjustment from arbitrary angle does not move");
                if(!arm.target()||ArmTransactions.locked(world,ROOT))return false;
                require(arm.physicalStartAngle()==45&&arm.startAngle()==45&&arm.payload()!=null&&!arm.payload().materialized(),"blocked arbitrary Start restores settings and captured ownership");
                // Load the old release's blocked partial adjustment, keeping the
                // actual captured attachment rather than a matrix-only fake.
                NBTTagCompound options=arm.settings();options.setDouble("StartAngle",90);arm.configure(options);arm.captureProgress(.75);arm.captureBlocked(true);
                NBTTagCompound stuck=arm.writeToNBT(new NBTTagCompound());arm.readFromNBT(stuck);arm.operator(actor);arm.onLoad();stage=30;return false;
            }
            if(stage==30&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(arm.startAngle()==45&&arm.physicalStartAngle()==45&&arm.configuredEndAngle()==-90,"saved blocked adjustment returns to previous endpoint and clears setting");
                require(arm.payload()!=null&&!arm.payload().materialized(),"legacy partial adjustment retains exactly one captured attachment");
                dialog(world,actor,arm,client,"recovered saved adjustment permits configuration and retry");
                world.setBlockToAir(obstruction);arm.target(false);stage=2;return false;
            }
            if(stage==2&&arm.progress()==0&&!ArmTransactions.locked(world,ROOT)){require(arm.pose(0).angleDegrees()==-90,"configured end angle reached");arm.target(true);stage=3;return false;}
            if(stage==3&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(arm.pose(1).angleDegrees()==45&&arm.payload()!=null,"reversal reaches configured source with retained ownership");
                System.out.println("[vandorlabs][reprolab] arm-start case PASS fixture="+fixture);fixture++;stage=0;ticks=80;
            }
            return false;
        }).get();
    }
}
