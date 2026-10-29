package com.vandorlabs.gear;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Native placement, full world ticks and actual asynchronous ownership journals. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class GearPushLiveChecks {
    private static final int FIRST=Integer.getInteger("vandorlabs.armPushFixture",0);
    private static final int LIMIT=Integer.getInteger("vandorlabs.armPushLimit",24);
    private static int fixture=FIRST,stage,ticks;
    private static final BlockPos ROOT=new BlockPos(620,170,260);
    private static BlockPos scale(BlockPos p,int n){return new BlockPos(p.getX()*n,p.getY()*n,p.getZ()*n);}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException("push fixture="+fixture+" stage="+stage+" ticks="+ticks+": "+reason);}
    private static BlockPos side(EnumFacing face,int x,int z){
        BlockPos u=ArmOrientation.vector(face,new BlockPos(1,0,0)),v=ArmOrientation.vector(face,new BlockPos(0,0,1));
        if(u.compareTo(BlockPos.ORIGIN)<0)u=scale(u,-1);if(v.compareTo(BlockPos.ORIGIN)<0)v=scale(v,-1);
        return scale(u,x).add(scale(v,z));
    }
    private static String blockers(GearTileEntity arm){
        StringBuilder result=new StringBuilder(" controller="+arm.getPos()+" owner="+arm.owner()+" operational="+arm.isOperational()+" members="+arm.members()+" payload="+(arm.payload()==null?"none":arm.payload().blocks.cells.size()+" cells native="+arm.payload().materialized()));
        double next=com.vandorlabs.gear.core.GearMotion.advance(arm.progress(),arm.target(),1,GearTileEntity.ANIMATION_TICKS);
        java.util.List<net.minecraft.util.math.AxisAlignedBB> old=GearGeometry.boxes(arm,arm.progress()),after=GearGeometry.boxes(arm,next);int found=0;
        for(int i=0;i<Math.min(old.size(),after.size())&&found<8;i++)if(!old.get(i).equals(after.get(i))){
            net.minecraft.util.math.AxisAlignedBB swept=old.get(i).union(after.get(i)).shrink(.0001);
            java.util.List<net.minecraft.util.math.AxisAlignedBB> obstacles=GearGeometry.foreign(arm.getWorld(),arm,swept);
            if(!obstacles.isEmpty()){result.append(" collision[").append(i).append("] swept=").append(swept).append(" obstacles=").append(obstacles);found++;}
        }
        return result.toString();
    }
    public static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){require(FIRST>=0&&LIMIT>FIRST&&LIMIT<=24,"fixture range must satisfy 0 <= start < limit <= 24; start="+FIRST+" limit="+LIMIT);mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ticks<80)return false;
        boolean clientReady=mc.playerController.getCurrentGameType()==net.minecraft.world.GameType.CREATIVE&&mc.player.capabilities.isFlying&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()&&mc.player.getDistanceSq(ROOT.getX()+.5,ROOT.getY()+4,ROOT.getZ()+.5)<1;
        String client="position="+mc.player.getPositionVector()+" distanceSq="+mc.player.getDistanceSq(ROOT)+" mode="+mc.playerController.getCurrentGameType()+" flying="+mc.player.capabilities.isFlying+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand();
        return mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(fixture==LIMIT){System.out.println("[vandorlabs][reprolab] arm-push PASS range=["+FIRST+","+LIMIT+") cases="+(LIMIT-FIRST)+" regular-Large joined-line-rectangle connected-Surface four-horizontal-faces retracted-config incremental-gap return-original leave-contacted reload real-asynchronous-journals");return true;}
            EnumFacing face=EnumFacing.HORIZONTALS[fixture%4];GearBlock block=fixture%8<4?GearRegistry.GEAR_EXTENDER:GearRegistry.LARGE_GEAR_EXTENDER;
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(ROOT);
            if(stage==0){
                actor.setGameType(net.minecraft.world.GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();
                actor.connection.setPlayerLocation(ROOT.getX()+.5,ROOT.getY()+4,ROOT.getZ()+.5,0,0);stage=-1;ticks=80;return false;
            }
            if(stage==-1){
                require(ticks<320,"fixture client placement synchronization: "+client+" actor="+actor.getPositionVector()+" mode="+actor.interactionManager.getGameType()+" flying="+actor.capabilities.isFlying+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand());
                if(!clientReady)return false;
                for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-8,-3,-8),ROOT.add(8,3,8)))world.setBlockToAir(at);
                world.setBlockState(ROOT.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),3);
                for(int n=0;n<4;n++){BlockPos at=ROOT.offset(face,n);require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,at,face,.5F,.5F,.5F,block.getDefaultState()),"ordinary arm placement");}
                arm=(GearTileEntity)world.getTileEntity(ROOT);arm.operator(actor);require(arm.rebuild(),"native arm forms "+arm.error());
                if(fixture>=8){
                    for(int x:new int[]{0,2})for(int z:fixture>=16?new int[]{0,2}:new int[]{0}){
                        BlockPos base=ROOT.add(side(face,x,z));
                        if(!base.equals(ROOT)){
                            world.setBlockState(base.offset(face.getOpposite()),Blocks.STONE.getDefaultState(),3);
                            for(int n=0;n<4;n++)require(((ItemBlock)Item.getItemFromBlock(block)).placeBlockAt(new ItemStack(block),actor,world,base.offset(face,n),face,.5F,.5F,.5F,block.getDefaultState()),"ordinary joined column placement");
                        }
                        GearTileEntity member=(GearTileEntity)world.getTileEntity(base);member.operator(actor);require(member.rebuild(),"joined column forms");NBTTagCompound settings=member.settings();settings.setBoolean("JoinArms",true);member.configure(settings);
                    }
                    for(int x=0;x<=2;x++)for(int z=0;z<=(fixture>=16?2:0);z++)world.setBlockState(ROOT.offset(face,4).add(side(face,x,z)),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState(),3);
                    require(arm.rebuild()&&arm.columns().size()==(fixture>=16?4:2),"joined group forms before motion");
                }
                world.setBlockState(ROOT.offset(face,4),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState(),3);world.setBlockState(ROOT.offset(face,4).up(),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState(),3);arm.target(false);stage=1;ticks=80;return false;
            }
            if(ticks>=800)require(false,"motion timeout progress="+arm.progress()+" target="+arm.target()+" error="+arm.error()+" locked="+ArmTransactions.locked(world,ROOT)+" actor="+actor.getPositionVector()+" mode="+actor.interactionManager.getGameType()+" flying="+actor.capabilities.isFlying+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand()+" client="+client+blockers(arm));
            if(stage==1&&arm.progress()==0&&!ArmTransactions.locked(world,ROOT)){
                NBTTagCompound options=arm.settings();options.setBoolean("PushBlocks",true);arm.configure(options);
                world.setBlockState(ROOT.offset(face,3).up(),Blocks.IRON_BLOCK.getDefaultState(),3);
                // A late immovable block must refuse before detaching the Surface.
                BlockPos obstruction=ROOT.offset(face,5).up();world.setBlockState(obstruction,Blocks.BEDROCK.getDefaultState(),3);
                arm.target(true);require(!arm.preparePayloadMotion()&&arm.progress()==0&&arm.payload().materialized()&&world.getBlockState(ROOT.offset(face,1).up()).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface,"complete push path refuses before source removal");
                world.setBlockToAir(obstruction);arm.target(true);stage=2;return false;
            }
            if(stage==2&&arm.progress()==1&&!ArmTransactions.locked(world,ROOT)){
                require(world.getBlockState(ROOT.offset(face,4)).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface,"initial Surface moves three blocks "+arm.error());
                require(world.getBlockState(ROOT.offset(face,5).up()).getBlock()==Blocks.IRON_BLOCK,"block beyond air moves only two blocks "+arm.error());
                NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());require(GearTileEntity.class.isInstance(net.minecraft.tileentity.TileEntity.create(world,saved)),"push settings reload");
                arm.target(false);stage=3;return false;
            }
            if(stage==3&&arm.progress()==0&&!ArmTransactions.locked(world,ROOT)){
                require(world.getBlockState(ROOT.offset(face,1)).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface&&world.getBlockState(ROOT.offset(face,1).up()).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface&&world.getBlockState(ROOT.offset(face,5).up()).getBlock()==Blocks.IRON_BLOCK,"retraction returns original Surface layer and leaves contacted blocks "+arm.error());
                if(fixture>=8)for(int x=0;x<=2;x++)for(int z=0;z<=(fixture>=16?2:0);z++)require(world.getBlockState(ROOT.offset(face,1).add(side(face,x,z))).getBlock() instanceof com.vandorlabs.blocks.BlockTurntableSurface,"every joined original Surface returns natively");
                require(world.getBlockState(ROOT.offset(face.getOpposite())).getBlock()==Blocks.STONE,"mounting support remains in place");System.out.println("[vandorlabs][reprolab] arm-push case PASS fixture="+fixture);fixture++;stage=0;ticks=80;
            }
            return false;
        }).get();
    }
}
