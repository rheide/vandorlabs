package com.vandorlabs.coupler;

import com.vandorlabs.items.*;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Metadata/placement/collision and the two actual dialog entry paths on registered blocks. */
public final class CouplerStaticChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Vehicle coupler: "+message);}
    private static final class RecordingPlayer extends net.minecraftforge.common.util.FakePlayer {
        int dialog=-1,opens;
        RecordingPlayer(WorldServer world){super(world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("18234a4e-2213-4de7-8401-b01dec417712"),"CouplerFixture"));}
        @Override public void openGui(Object mod,int id,World world,int x,int y,int z){dialog=id;opens++;require(ContainerCoupler.resolve(this,world,new BlockPos(x,y,z))!=null,"both entry paths resolve the same authorized coupler container");}
    }
    public static void run(WorldServer world,BlockPos pos){
        BlockCoupler full=(BlockCoupler)Block.getBlockFromName("vandorlabs:square_coupler");BlockCouplerSlab slab=(BlockCouplerSlab)Block.getBlockFromName("vandorlabs:vehicle_coupler_slab");require(slab!=null&&full.getLocalizedName().equals("Vehicle Coupler"),"renamed full ID and registered slab");RecordingPlayer actor=new RecordingPlayer(world);actor.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);int states=0,placements=0;
        try{
            for(IBlockState enumerated:slab.getBlockState().getValidStates())require(slab.getMetaFromState(enumerated)>=0&&slab.getMetaFromState(enumerated)<16,"shared six-axis property fallback remains legal during Forge harvest-level registration");
            for(int meta=0;meta<16;meta++){
                IBlockState state=slab.getStateFromMeta(meta);require(slab.getMetaFromState(state)==meta&&state.getValue(BlockCoupler.FACING).getAxis().isHorizontal(),"16 lossless horizontal half/lock states");AxisAlignedBB[] boxes=slab.collisionBoxes(state),original=CouplerShape.getBoxes(state.getValue(BlockCoupler.FACING),state.getValue(BlockCoupler.LOCKED));double offset=state.getValue(BlockCouplerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP?.5:0;require(boxes.length==9,"all nine native collision cuboids");for(int i=0;i<boxes.length;i++){AxisAlignedBB a=boxes[i],b=original[i];require(a.minX==b.minX&&a.maxX==b.maxX&&a.minZ==b.minZ&&a.maxZ==b.maxZ&&a.minY==b.minY*.5+offset&&a.maxY==b.maxY*.5+offset,"native horizontal dimensions with exact half-height collision");require(a.minY>=offset&&a.maxY<=offset+.5,"slab collision stays in selected half");}states++;
            }
            for(EnumFacing facing:EnumFacing.HORIZONTALS){actor.rotationYaw=facing.getHorizontalAngle();for(EnumFacing clicked:EnumFacing.values())for(float height:new float[]{.25F,.75F}){
                IBlockState placed=slab.getStateForPlacement(world,pos,clicked,.5F,height,.5F,0,actor);EnumFacing expected=clicked.getAxis().isHorizontal()?clicked:facing.getOpposite();require(placed.getValue(BlockCoupler.FACING)==expected,"floor/ceiling preserve horizontal mounting orientation");boolean top=clicked==EnumFacing.DOWN||clicked!=EnumFacing.UP&&height>.5F;require((placed.getValue(BlockCouplerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP)==top,"vanilla top/bottom placement from actual clicked face/height");placements++;
            }}
            for(BlockCoupler block:new BlockCoupler[]{full,slab}){
                world.setBlockState(pos,block.getDefaultState(),3);com.vandorlabs.tiles.DeferredTileLoad.flush(world);actor.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);actor.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);actor.dialog=-1;
                TileCoupler tile=(TileCoupler)world.getTileEntity(pos);
                require(!tile.writeToNBT(new NBTTagCompound()).hasKey("CouplerManualOverride"),"untouched couplers retain the previous saved tile layout");
                require(block.onBlockActivated(world,pos,world.getBlockState(pos),actor,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)&&tile.requested()&&tile.connected()&&actor.dialog==-1,"ordinary interaction couples without opening dialog");
                NBTTagCompound manual=tile.writeToNBT(new NBTTagCompound());require(manual.getBoolean("CouplerManualOverride")&&manual.getBoolean("CouplerManualRequested"),"manual coupling persists in saved tile data");TileCoupler reloadedManual=new TileCoupler();reloadedManual.readFromNBT(manual);require(reloadedManual.writeToNBT(new NBTTagCompound()).equals(manual),"manual coupling survives exact tile NBT round trip");
                require(block.onBlockActivated(world,pos,world.getBlockState(pos),actor,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)&&!tile.requested()&&!tile.connected(),"second ordinary interaction uncouples");
                actor.setSneaking(true);actor.dialog=-1;require(block.onBlockActivated(world,pos,world.getBlockState(pos),actor,EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F)&&actor.dialog==com.vandorlabs.GuiHandler.GUI_COUPLER&&!tile.requested(),"sneak interaction opens common dialog without toggling");actor.setSneaking(false);
                tile.setChannelSignal(true);require(tile.requested()&&tile.connected(),"redstone change takes over manual coupling");tile.setChannelSignal(false);require(!tile.requested()&&!tile.connected(),"redstone release uncouples again");
                actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));actor.dialog=-1;net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(actor,EnumHand.MAIN_HAND,pos,EnumFacing.NORTH,new Vec3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()));ItemConfigurizer.onRightClickBlock(event);require(event.isCanceled()&&actor.dialog==com.vandorlabs.GuiHandler.GUI_COUPLER,"Configurator opens identical coupler dialog rather than generic channel settings");
                tile.configure(ChannelList.of(65441),true);require(tile.connected(),"slab/full share actual channel inversion tile");NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileCoupler restored=new TileCoupler();restored.readFromNBT(saved);require(restored.writeToNBT(new NBTTagCompound()).equals(saved),"exact same tile NBT on full and slab");
                int before=actor.opens;actor.setPosition(pos.getX()+30,pos.getY(),pos.getZ());ContainerCoupler.open(actor,world,pos);require(actor.opens==before&&ContainerCoupler.resolve(actor,world,pos)==null,"shared entry path rejects out-of-reach interaction");world.setBlockToAir(pos);
            }
        }finally{world.setBlockToAir(pos);actor.setDead();}
        System.out.println("[vandorlabs][reprolab] coupler-static PASS slab-states="+states+" placements="+placements+" exact native nine-box halfheight allfaces horizontal-only right-click toggle sneak/Configurator authorized dialog redstone takeover stable fullID and exact shared tileNBT");
    }
    private CouplerStaticChecks(){}
}
