package com.vandorlabs.gear;

import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.fml.relauncher.*;

/** Exact saved wall, two independent arms and three touching granite blocks. */
@SideOnly(Side.CLIENT)
public final class ArmWallRuntimeChecks {
    private static int stage,ticks,fixture;
    private static boolean clicked,negative;
    private static BlockPos root(){return new BlockPos(-75,83+fixture*2,160);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-wall fixture="+fixture+" stage="+stage+": "+why);}
    public static boolean tick(Minecraft mc)throws Exception{
        if(fixture>=2)return true;
        mc.displayGuiScreen(null);
        if(stage==1&&ticks>20&&!clicked&&mc.playerController.getCurrentGameType()==GameType.CREATIVE&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().isEmpty()
                &&mc.player.getDistanceSq(-71.5,84,157.5)<1&&mc.world.getTileEntity(root()) instanceof GearTileEntity){
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock(root(),EnumFacing.EAST,EnumHand.MAIN_HAND,.5F,.5F,.5F));clicked=true;
        }
        return mc.getIntegratedServer().callFromMainThread(()->run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0))).get();
    }
    private static boolean run(WorldServer world,EntityPlayerMP player)throws Exception{
        require(++ticks<400,"bounded motion");
        if(stage==0){
            for(BlockPos p:BlockPos.getAllInBox(new BlockPos(-78,78,154),new BlockPos(-68,91,167))){world.removeTileEntity(p);world.setBlockToAir(p);}
            NBTTagCompound data;try(java.io.InputStream input=Files.newInputStream(Paths.get(System.getProperty("vandorlabs.armWallFixture")))){data=CompressedStreamTools.readCompressed(input);}
            NBTTagList cells=data.getTagList("Cells",10);
            for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);BlockPos at=new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));Block block=Block.getBlockFromName(cell.getString("Name"));require(block!=null,"registered saved block");world.setBlockState(at,block.getStateFromMeta(cell.getInteger("Meta")),2);
                if(cell.hasKey("Tile",10)){world.removeTileEntity(at);world.setTileEntity(at,TileEntity.create(world,cell.getCompoundTag("Tile")));}}
            sweepChecks(world,((GearTileEntity)world.getTileEntity(root())).writeToNBT(new NBTTagCompound()));
            for(int y:new int[]{83,85})((GearTileEntity)world.getTileEntity(new BlockPos(-75,y,160))).operator(player);
            player.setGameType(GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.inventory.currentItem=0;player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryContainer.detectAndSendChanges();player.connection.setPlayerLocation(-71.5,84,157.5,45,0);
            stage=1;ticks=0;return false;
        }
        GearTileEntity arm=((GearTileEntity)world.getTileEntity(root())).loadedController();require(arm!=null,"saved controller available");
        if(ArmTransactions.locked(world,arm.getPos()))return false;
        if(stage==1){if(!clicked)return false;
            if(ticks>80&&arm.progress()==1&&arm.target()){
                require(false,"saved move refused: "+arm.error());
            }
            if(arm.progress()!=0)return false;
            require(world.getBlockState(new BlockPos(-73,84,160)).getBlock()==net.minecraft.init.Blocks.STONE,"middle block stays in place");
            arm.target(true);stage=2;ticks=0;return false;
        }
        if(stage==3){
            if(ticks<60)return false;
            require(arm.progress()==1&&arm.target()&&arm.payload()!=null&&arm.payload().materialized(),"real obstruction refuses at native endpoint: "+arm.error());
            require(!arm.error().isEmpty(),"real obstruction reports a reason");
            require(world.getBlockState(root().east(2)).getBlock()==net.minecraft.init.Blocks.STONE,"refused move restores source payload");
            world.setBlockToAir(root().east(2).south());arm.target(false);stage=4;ticks=0;return false;
        }
        if(stage==4){if(arm.progress()!=0)return false;arm.target(true);stage=2;ticks=0;return false;}
        if(arm.progress()!=1||arm.payload()==null||!arm.payload().materialized())return false;
        if(!negative){negative=true;world.setBlockState(root().east(2).south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);arm.target(false);stage=3;ticks=0;return false;}
        require(world.getBlockState(root().east(2)).getBlock()==net.minecraft.init.Blocks.STONE,"payload restored exactly once");
        System.out.println("[vandorlabs] arm-wall case PASS "+fixture);
        if(++fixture<2){stage=1;ticks=0;clicked=false;negative=false;return false;}
        System.out.println("[vandorlabs] arm-wall PASS exact saved pair activation reversal fixed wall touching middle block real obstruction refusal recovery retry");return true;
    }
    private static void sweepChecks(WorldServer world,NBTTagCompound saved){
        BlockPos pos=new BlockPos(-60,100,160);int cases=0;
        for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM}){
            world.removeTileEntity(pos);world.setBlockState(pos,block.getDefaultState(),2);
            for(EnumFacing face:EnumFacing.values())for(com.vandorlabs.gear.core.GearMotion.Direction direction:com.vandorlabs.gear.core.GearMotion.Direction.values())for(String mode:new String[]{"ROTATE","ROTATE_LEVEL"}){
                NBTTagCompound tag=saved.copy();tag.removeTag("ArmPayload");tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());tag.setString("ArmMountDirection",face.name());tag.setString("GearDirection",direction.name());tag.setString("GearMode",mode);
                GearTileEntity arm=new GearTileEntity();arm.readFromNBT(tag);arm.setWorld(world);world.setTileEntity(pos,arm);
                Vec3d axis=ArmOrientation.vector(face,direction.axisX(),0,direction.axisZ());
                for(int step=0;step<24;step++){
                    double before=step/24D,after=(step+1)/24D;
                    java.util.List<AxisAlignedBB> first=GearGeometry.boxes(arm,before),last=GearGeometry.boxes(arm,after);
                    for(int i=0;i<first.size();i++){
                        AxisAlignedBB sweep=GearGeometry.swept(arm,i,first.get(i),last.get(i),before,after);
                        AxisAlignedBB expanded=sweep.grow(.000101);
                        if(axis.x!=0)require(sweep.minX>=first.get(i).minX&&sweep.maxX<=first.get(i).maxX,"no sweep inflation along X hinge");
                        if(axis.y!=0)require(sweep.minY>=first.get(i).minY&&sweep.maxY<=first.get(i).maxY,"no sweep inflation along Y hinge");
                        if(axis.z!=0)require(sweep.minZ>=first.get(i).minZ&&sweep.maxZ<=first.get(i).maxZ,"no sweep inflation along Z hinge");
                        for(int sample=0;sample<=4;sample++){
                            AxisAlignedBB b=GearGeometry.boxes(arm,before+(after-before)*sample/4D).get(i);
                            require(expanded.minX<=b.minX&&expanded.minY<=b.minY&&expanded.minZ<=b.minZ&&expanded.maxX>=b.maxX&&expanded.maxY>=b.maxY&&expanded.maxZ>=b.maxZ,"sweep retains intermediate geometry "+face+" "+direction+" "+mode+" step="+step+" part="+i);
                        }
                    }
                }
                cases++;world.removeTileEntity(pos);
            }
        }
        world.setBlockToAir(pos);System.out.println("[vandorlabs] arm-wall sweep PASS configurations="+cases+" six mounts four fold directions both sizes both payload modes 24 stroke intervals five samples each");
    }
    private ArmWallRuntimeChecks(){}
}
