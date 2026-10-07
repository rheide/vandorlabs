package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityRedstoneChannel;
import com.vandorlabs.items.ProgrammableSettings;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** New mounted controls retain their settings through real item and Duplifier paths. */
final class BinaryControlRuntimeChecks {
    static void run(World world,EntityPlayer player){
        BlockPos pos=new BlockPos(38,103,38),copy=pos.east(4);int cases=0;
        for(String id:new String[]{"small_power_lever","large_power_lever","industrial_power_lever","compact_power_lever","rocker_switch"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);boolean lever=block instanceof BlockIndustrialLever;
            for(EnumFacing face:EnumFacing.values()){
                if(lever && face==EnumFacing.DOWN)continue;
                BlockPos support=pos.offset(face.getOpposite());world.setBlockState(support,net.minecraft.init.Blocks.STONE.getDefaultState(),3);world.setBlockState(copy.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
                IBlockState state=block.getDefaultState();if(lever)state=state.withProperty(BlockIndustrialLever.FACING,face==EnumFacing.UP?EnumFacing.NORTH:face).withProperty(BlockIndustrialLever.FLOOR,face==EnumFacing.UP);else state=state.withProperty(BlockVandorSwitch.FACING,face);
                world.setBlockState(pos,state,3);TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(pos);tile.configureMount(3,3,1);tile.setRedstoneChannel(17501);
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntityRedstoneChannel restored=new TileEntityRedstoneChannel();restored.readFromNBT(saved);require(restored.getBaseHeight()==3 && restored.getBaseTilt()==3 && restored.getTiltDirection()==1,"mount NBT");
                ItemStack picked=block.getPickBlock(state,null,world,pos,player);require(picked.getSubCompound("RedstoneChannelSettings").getInteger("BaseHeight")==3,"pick lost mount");
                Block target=((net.minecraft.item.ItemBlock)picked.getItem()).getBlock();IBlockState placed=target.getDefaultState();placed=lever?placed.withProperty(BlockIndustrialLever.FLOOR,true):placed.withProperty(BlockVandorSwitch.FACING,EnumFacing.UP);
                world.setBlockState(copy,placed,3);target.onBlockPlacedBy(world,copy,placed,player,picked);TileEntityRedstoneChannel targetTile=(TileEntityRedstoneChannel)world.getTileEntity(copy);
                require(targetTile.getBaseHeight()==3 && targetTile.getBaseTilt()==3 && targetTile.getRedstoneChannel()==17501 && (!lever || targetTile.getPowerLeverSize()==tile.getPowerLeverSize()),"item placement lost configuration");
                targetTile.configureMount(0,0,0);ProgrammableSettings.apply(world,copy,ProgrammableSettings.capture(world,pos));require(targetTile.getBaseHeight()==3 && targetTile.getBaseTilt()==3 && targetTile.getTiltDirection()==1,"Duplifier lost mount");
                java.util.List<AxisAlignedBB> pieces=MountedControlGeometry.boxes(state,world,pos);require(!pieces.isEmpty(),"missing solid geometry");AxisAlignedBB biggest=pieces.get(0);
                for(AxisAlignedBB b:pieces)if(volume(b)>volume(biggest))biggest=b;
                AxisAlignedBB query=biggest.offset(pos);require(!world.getCollisionBoxes(null,query.shrink(.00001)).isEmpty(),"world ignores control collision");
                net.minecraft.entity.item.EntityArmorStand body=new net.minecraft.entity.item.EntityArmorStand(world);body.stepHeight=0;body.setNoGravity(true);body.setPosition(query.minX-.8,query.getCenter().y,query.getCenter().z);double start=body.posX;IBlockState supportState=world.getBlockState(support);world.setBlockState(support,net.minecraft.init.Blocks.AIR.getDefaultState(),2);try{body.move(net.minecraft.entity.MoverType.SELF,2,0,0);require(body.posX-start<1.99,"body walked through raised control");}finally{world.setBlockState(support,supportState,2);}
                world.setBlockToAir(copy);world.setBlockToAir(copy.down());world.setBlockToAir(pos);world.setBlockToAir(support);cases++;
            }
        }
        System.out.println("[vandorlabs][reprolab] binary-control-runtime PASS cases="+cases+" (legacy sizes, NBT, pick/placement, Duplifier and movement collision)");
    }
    private static double volume(AxisAlignedBB b){return (b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
