package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.ProgrammableSettings;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Release regressions use actual item placement, dust, ownership and shared moving poses. */
final class CanopyReleaseRuntimeChecks {
    private static final BlockPos BASE=new BlockPos(80,100,80);
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException("canopy 2.0: "+text);}
    private static void clear(World world){for(BlockPos p:BlockPos.getAllInBox(BASE.add(-10,-10,-10),BASE.add(10,10,10)))world.setBlockToAir(p);}
    private static LargeCanopySettings copy(LargeCanopySettings settings){NBTTagCompound tag=new NBTTagCompound();settings.write(tag);LargeCanopySettings result=new LargeCanopySettings();result.read(tag);return result;}
    static void run(EntityPlayerMP player) {
        World world=player.world;boolean sneak=player.isSneaking();ItemStack held=player.getHeldItemMainhand();float yaw=player.rotationYaw;Vec3d originalPosition=player.getPositionVector();
        try {
            player.setSneaking(false);
            CanopyMesh.Face probe=new CanopyMesh.Face();probe.vertices=new Vec3d[]{new Vec3d(1.5,3,1)};probe.uv=new double[][]{{0,0}};probe.material=probe.baseMaterial="glass";probe.group="shell";
            LargeCanopySettings top=new LargeCanopySettings();top.openable=true;top.hinge=2;
            require(CanopyPose.faces(Collections.singletonList(probe),"large_front_canopy_glass",3,top,.5).get(0).vertices[0].y>3,"top hinge lifts the projecting front away from the cabin");
            for(String kind:new String[]{"large_front_canopy_glass","large_front_canopy_glass_5x5"})for(EnumFacing side:EnumFacing.values()) {
                clear(world);BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);
                world.setBlockState(BASE,Blocks.STONE.getDefaultState(),3);
                player.rotationYaw=(side.getAxis().isHorizontal()?side.getOpposite():EnumFacing.NORTH).getHorizontalAngle();
                player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block));
                require(player.getHeldItemMainhand().getItem().onItemUse(player,world,BASE,EnumHand.MAIN_HAND,side,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"six-face item placement "+kind+" "+side);
                BlockPos clicked=BASE.offset(side);EnumFacing facing=block.placementFacing(player);
                BlockPos anchor=side.getAxis()==EnumFacing.Axis.Y?clicked.offset(facing.rotateYCCW(),block.width/2).offset(facing,block.width/2):block.placementAnchor(clicked,facing);
                TileEntityCanopy tile=TileEntityCanopy.at(world,anchor);require(tile!=null && tile.draws(),"mount anchor");
                require(tile.large.mount==(side==EnumFacing.UP?2:side==EnumFacing.DOWN?3:0),"mount persisted");
                AxisAlignedBB initial=tile.selectionBounds();
                require(initial.intersects(new AxisAlignedBB(clicked)),"large back is centered at clicked cell");
                LargeCanopySettings next=copy(tile.large);next.depth=64;next.scale=40;next.openable=true;
                require(LargeCanopyOwnership.apply(tile,player,next),"mounted extrusion expands");
                AxisAlignedBB closed=tile.selectionBounds();
                Vec3d center=closed.getCenter();Vec3d outward=new Vec3d(side.getFrontOffsetX(),side.getFrontOffsetY(),side.getFrontOffsetZ());
                Vec3d eye=center.add(outward.scale(5)),end=center.subtract(outward.scale(5));
                RayTraceResult front=tile.trace(eye,end);require(front!=null,"mounted front is selectable");
                BlockPos inside=CanopyInteractions.interiorTarget(tile,eye,front.hitVec,12);
                require(inside!=null && !tile.cells().contains(inside),"outside click reaches free cabin cell "+side);
                player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.IRON_BLOCK,2));
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,anchor,front.sideHit,front.hitVec);
                new CanopyInteractions().placeInside(click);
                require(click.isCanceled() && click.getCancellationResult()==EnumActionResult.SUCCESS && world.getBlockState(inside).getBlock()==Blocks.IRON_BLOCK,"outside click places an actual cabin block");
                next.shade=12;next.tint=2;require(LargeCanopyOwnership.apply(tile,player,next),"finish edit preserves cabin blocks");
                for(int hinge=0;hinge<4;hinge++) {
                    next.hinge=hinge;require(LargeCanopyOwnership.apply(tile,player,next),"hinge setting");
                    tile.toggle();for(int tick=0;tick<16;tick++)tile.update();require(tile.progress==1 && !tile.boxes().equals(CanopyMesh.collision(tile.faces(0),"posed",tile.width(),0,tile.facing(),anchor)),"hinge changes mounted collision");
                    tile.toggle();for(int tick=0;tick<16;tick++)tile.update();require(tile.progress==0,"hinge returns closed");
                }
                TileEntityCanopy restored=new TileEntityCanopy();restored.setWorld(world);restored.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
                require(restored.cells().equals(tile.cells()) && restored.large.key().equals(tile.large.key()),"exact footprint and settings persist");
                java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> carried=new ArrayList<>();
                BlockPos origin=BASE.add(-8,-8,-8);
                carried.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
                for(BlockPos cell:tile.cells())carried.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(cell.subtract(origin),world.getBlockState(cell),com.vandorlabs.vehicle.VehicleTileData.translated(world.getTileEntity(cell).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));
                carried.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(inside.subtract(origin),Blocks.IRON_BLOCK.getDefaultState(),null));
                com.vandorlabs.vehicle.VehicleStructure snapshot=new com.vandorlabs.vehicle.VehicleStructure(carried,BlockPos.ORIGIN,EnumFacing.NORTH,1),rotated=snapshot;
                VehicleCanopyMotionRuntimeChecks.run(world,snapshot,anchor.subtract(origin));
                for(int turn=0;turn<4;turn++)rotated=com.vandorlabs.vehicle.VehicleRotation.rotate(rotated,new com.vandorlabs.vehicle.VehicleWorld(world,rotated),1);
                require(rotated.write().equals(snapshot.write()),"mounted hollow canopy keeps exact footprint through four parking rotations");
                List<BlockPos> footprint=new ArrayList<>(tile.cells());
                world.setBlockToAir(footprint.get(footprint.size()-1));
                for(BlockPos cell:footprint)require(world.isAirBlock(cell) && TileEntityCanopy.at(world,cell)==null,"removal leaves no proxies "+side);
                require(world.getBlockState(inside).getBlock()==Blocks.IRON_BLOCK,"removal preserves cabin block");
            }
            clear(world);BlockCanopy legacy=(BlockCanopy)Block.getBlockFromName("vandorlabs:opening_hinge_1x2");world.setBlockState(BASE,legacy.getDefaultState(),2);legacy.onBlockPlacedBy(world,BASE,legacy.getDefaultState(),player,new ItemStack(legacy));
            NBTTagCompound old=world.getTileEntity(BASE).writeToNBT(new NBTTagCompound());old.removeTag("CanopyMotion");TileEntityCanopy oldTile=new TileEntityCanopy();oldTile.readFromNBT(old);oldTile.setWorld(world);oldTile.faces();require(oldTile.large.motion==1,"legacy rear hinge resolves after its world is assigned");
            opening(world,player);copyFinishes(world,player);button(world,player);seat(world);
        }finally{clear(world);player.setHeldItem(EnumHand.MAIN_HAND,held);player.setSneaking(sneak);player.rotationYaw=yaw;player.setPosition(originalPosition.x,originalPosition.y,originalPosition.z);}
        System.out.println("[vandorlabs][reprolab] canopy-2.0 PASS six-face large mounts hinges=4 outside-placement exact-footprint removal opening-modes angles ceiling joins channels copy floor-button pilot-armrests");
    }
    private static void opening(World world,EntityPlayerMP player){
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:opening_slide_1x2"),glass=(BlockCanopy)Block.getBlockFromName("vandorlabs:regular_canopy_glass");
        for(int mount=0;mount<2;mount++)for(int angle=0;angle<3;angle++)for(int motion=0;motion<2;motion++)for(int width=1;width<=3;width++) {
            clear(world);LargeCanopySettings settings=new LargeCanopySettings();settings.mount=mount;settings.angle=angle;settings.motion=motion;
            ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);
            for(int column=0;column<width;column++){BlockPos pos=BASE.east(column);world.setBlockState(pos,block.getDefaultState(),2);block.onBlockPlacedBy(world,pos,block.getDefaultState(),player,stack);}
            TileEntityCanopy tile=TileEntityCanopy.at(world,BASE);require(tile.width()==width,"configured shells join");
            BlockPos rear=BASE.south(2);ItemStack rearStack=new ItemStack(glass);rearStack.setTagCompound(tag.copy());world.setBlockState(rear,glass.getDefaultState(),2);glass.onBlockPlacedBy(world,rear,glass.getDefaultState(),player,rearStack);
            require((tile.mask()&8)!=0 && TileEntityCanopy.at(world,rear).mask()!=0,"rear glass joins opening shell");
            tile.toggle();for(int tick=0;tick<16;tick++)tile.update();require(tile.progress==1,"configured shell opens");
            tile.toggle();for(int tick=0;tick<16;tick++)tile.update();require(tile.progress==0,"configured shell closes");
            settings.redstone=true;settings.channels=com.vandorlabs.redstone.ChannelList.of(29020);
            require(LargeCanopyOwnership.apply(tile,player,settings),"redstone settings apply");
            world.setBlockState(BASE.north(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);tile.refreshPower();require(tile.targetOpen,"physical signal opens");
            world.setBlockToAir(BASE.north());tile.refreshPower();require(!tile.targetOpen,"physical signal closes");
            BlockPos sourcePos=BASE.west(5);BlockPushButton source=(BlockPushButton)Block.getBlockFromName("vandorlabs:push_button");
            world.setBlockState(sourcePos.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(sourcePos,source.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),3);
            ((TileEntityRedstoneChannel)world.getTileEntity(sourcePos)).setRedstoneChannel(29020);
            source.onBlockActivated(world,sourcePos,world.getBlockState(sourcePos),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);require(tile.targetOpen,"real remote channel opens");
            source.updateTick(world,sourcePos,world.getBlockState(sourcePos),new Random());require(!tile.targetOpen,"real remote channel closes");
            NBTTagCompound values=new NBTTagCompound();values.setInteger(ProgrammableSettings.GLASS_SHADE,13);values.setInteger(ProgrammableSettings.GLASS_TINT,2);
            require(ProgrammableSettings.apply(world,BASE,values,player) && tile.large.shade==13 && tile.large.tint==2,"copy applies canopy glass finish");
            require(ProgrammableSettings.capture(world,BASE).getInteger(ProgrammableSettings.GLASS_TINT)==2,"copy captures canopy intensity");
        }
        for(String kind:new String[]{"angled_front_1x1","angled_front_1x2","opening_slide_1x2"})for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            clear(world);BlockCanopy nose=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);world.setBlockState(BASE,Blocks.STONE.getDefaultState(),3);player.rotationYaw=facing.getOpposite().getHorizontalAngle();player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(nose));
            require(player.getHeldItemMainhand().getItem().onItemUse(player,world,BASE,EnumHand.MAIN_HAND,facing,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"long nose can mount against a wall");
        }
    }
    private static void copyFinishes(World world,EntityPlayerMP player){
        String[] kinds={"regular_canopy_glass","space_visor_2x1","programmable_glass","programmable_diagonal_glass"};
        for(String donor:kinds)for(String recipient:kinds){
            clear(world);Block source=Block.getBlockFromName("vandorlabs:"+donor),target=Block.getBlockFromName("vandorlabs:"+recipient);
            require(source!=null && target!=null,"glass copy families registered");
            world.setBlockState(BASE,source.getDefaultState(),2);if(source instanceof BlockCanopy)source.onBlockPlacedBy(world,BASE,source.getDefaultState(),player,new ItemStack(source));
            BlockPos to=BASE.east(6);world.setBlockState(to,target.getDefaultState(),2);if(target instanceof BlockCanopy)target.onBlockPlacedBy(world,to,target.getDefaultState(),player,new ItemStack(target));
            NBTTagCompound values=new NBTTagCompound();values.setInteger(ProgrammableSettings.GLASS_SHADE,13);values.setInteger(ProgrammableSettings.GLASS_TINT,2);
            require(ProgrammableSettings.apply(world,BASE,values,player),"source shade/intensity set");
            require(ProgrammableSettings.apply(world,to,ProgrammableSettings.capture(world,BASE),player),"cross-family glass copy");
            NBTTagCompound actual=ProgrammableSettings.capture(world,to);require(actual.getInteger(ProgrammableSettings.GLASS_SHADE)==13 && actual.getInteger(ProgrammableSettings.GLASS_TINT)==2,"shade/intensity survive cross-family copy");
        }
    }
    private static void button(World world,EntityPlayerMP player){
        for(int channel:new int[]{0,29021}) {
            clear(world);BlockPushButton button=(BlockPushButton)Block.getBlockFromName("vandorlabs:push_button");
            world.setBlockState(BASE.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(BASE.east().down(),Blocks.STONE.getDefaultState(),3);
            world.setBlockState(BASE.east(),Blocks.REDSTONE_WIRE.getDefaultState(),3);
            world.setBlockState(BASE,button.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),3);
            TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(BASE);tile.setRedstoneChannel(channel);
            button.onBlockActivated(world,BASE,world.getBlockState(BASE),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
            require(world.getBlockState(BASE.east()).getValue(net.minecraft.block.BlockRedstoneWire.POWER)==15,"floor button powers adjacent dust immediately");
            button.updateTick(world,BASE,world.getBlockState(BASE),new Random());require(world.getBlockState(BASE.east()).getValue(net.minecraft.block.BlockRedstoneWire.POWER)==0,"button release clears dust");
        }
    }
    private static void seat(World world){
        clear(world);BlockPilotSeat seat=(BlockPilotSeat)ModBlocks.PILOT_SEAT;
        for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            world.setBlockState(BASE,seat.getDefaultState().withProperty(BlockPilotSeat.FACING,facing),2);
            for(int mask=0;mask<4;mask++){
                world.setBlockState(BASE.offset(facing.rotateYCCW()),(mask&1)!=0?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
                world.setBlockState(BASE.offset(facing.rotateY()),(mask&2)!=0?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
                require(seat.getActualState(world.getBlockState(BASE),world,BASE).getValue(BlockPilotSeat.ARMS)==mask,"armrests follow local solid neighbors");
            }
            clear(world);
        }
    }
}
