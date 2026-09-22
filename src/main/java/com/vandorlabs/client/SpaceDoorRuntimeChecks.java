package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockConfigurableSpaceDoor;
import com.vandorlabs.blocks.BlockVandorDoor;
import com.vandorlabs.render.SpaceDoorMotion;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Real item placement and pick-block tests; invoked by the live ReproLab. */
final class SpaceDoorRuntimeChecks {
    private static void check(boolean ok,String message) {
        if (!ok) throw new IllegalStateException("Space door runtime: "+message);
    }
    private static TileEntitySpaceDoor tile(World world,BlockPos pos) {
        return (TileEntitySpaceDoor)world.getTileEntity(pos);
    }
    private static void clear(World world,BlockPos pos) {
        world.setBlockToAir(pos.up()); world.setBlockToAir(pos);
    }
    private static IBlockState state(BlockConfigurableSpaceDoor block) {
        return block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH)
                .withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER)
                .withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.RIGHT);
    }
    static void run(World world,EntityPlayer player,BlockPos source) {
        BlockConfigurableSpaceDoor block=(BlockConfigurableSpaceDoor)Block.REGISTRY.getObject(
                new ResourceLocation("vandorlabs","space_door"));
        ItemStack blank=new ItemStack(block);
        ItemBlock item=(ItemBlock)blank.getItem();
        BlockPos target=source.east(4), neighbor=target.east();
        for (BlockPos pos:new BlockPos[]{source,target,neighbor}) {
            clear(world,pos); world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
        }
        check(item.placeBlockAt(blank.copy(),player,world,source,EnumFacing.UP,.5F,.5F,.5F,state(block)),"default placement");
        check(tile(world,source).isSliding() && tile(world,source).isMiddle()
                && tile(world,source).getSlideDirection()==0,"new door must default to sideways / middle");
        // Existing settings survive the default change and NBT reload.
        tile(world,source).configure(4,2,false,0,false,false);
        NBTTagCompound saved=tile(world,source).writeToNBT(new NBTTagCompound());
        tile(world,source).readFromNBT(saved);
        check(!tile(world,source).isSliding() && !tile(world,source).isMiddle(),"saved rotating/edge changed");
        // A different adjacent configuration must not overwrite a picked item.
        IBlockState other=state(block).withProperty(BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.LEFT);
        check(item.placeBlockAt(blank.copy(),player,world,neighbor,EnumFacing.UP,.5F,.5F,.5F,other),"neighbor placement");
        tile(world,neighbor).configure(0,0,true,2,false,true);
        NBTTagCompound neighborSettings=tile(world,neighbor).itemSettings();
        int cases=0;
        for (int design=0;design<TileEntitySpaceDoor.DESIGNS.length;design++) for (int detail=0;detail<3;detail++)
            for (SpaceDoorMotion motion:SpaceDoorMotion.values()) for (boolean framed:new boolean[]{false,true})
                for (boolean middle:new boolean[]{false,true}) for (boolean hinges:new boolean[]{false,true}) {
                    TileEntitySpaceDoor original=tile(world,source);
                    original.configure(design,detail,framed,motion.direction,middle,motion.sliding,hinges);
                    original.setRedstoneChannel(7341);
                    NBTTagCompound expected=original.itemSettings();
                    for (BlockPos half:new BlockPos[]{source,source.up()}) {
                        ItemStack picked=block.getPickBlock(world.getBlockState(half),null,world,half,player);
                        check(picked.getItem()==blank.getItem(),"pick must return unified item");
                        NBTTagCompound settings=picked.getSubCompound("SpaceDoorSettings");
                        check(expected.equals(settings),"pick lost settings from "+half);
                        check(!settings.hasKey("x") && !settings.hasKey("id") && !settings.hasKey("ChannelSignal"),
                                "pick copied transient tile data");
                        clear(world,target);
                        check(item.placeBlockAt(picked,player,world,target,EnumFacing.UP,.5F,.5F,.5F,state(block)),"picked placement");
                        check(tile(world,target).itemSettings().equals(expected),"placement changed copied properties");
                        check(!world.getBlockState(target).getValue(BlockVandorDoor.OPEN),"pick copied open state");
                        check(tile(world,neighbor).itemSettings().equals(neighborSettings),"pick changed neighbor settings");
                        cases++;
                    }
                }
        // The trigger changes both click policy and the response to a live redstone edge.
        BlockPos power=source.north();
        world.setBlockToAir(power);
        for (int trigger=0;trigger<=2;trigger++) {
            clear(world,source);
            check(item.placeBlockAt(blank.copy(),player,world,source,EnumFacing.UP,.5F,.5F,.5F,state(block)),
                    "trigger placement");
            TileEntitySpaceDoor selected=tile(world,source);
            selected.configure(2,1,true,0,true,true,true,trigger);
            NBTTagCompound savedTrigger=selected.writeToNBT(new NBTTagCompound());
            selected.readFromNBT(savedTrigger);
            check(selected.getTrigger()==trigger && selected.itemSettings().getInteger("SpaceDoorTrigger")==trigger,
                    "trigger save and pick settings");
            boolean initiallyOpen=trigger==com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_OFF;
            check(world.getBlockState(source).getValue(BlockVandorDoor.OPEN)==initiallyOpen,
                    "trigger must apply immediately without power");
            block.onBlockActivated(world,source,world.getBlockState(source),player,
                    EnumHand.MAIN_HAND,EnumFacing.NORTH,.5F,.5F,.5F);
            boolean afterClick=trigger==com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED
                    || initiallyOpen;
            check(world.getBlockState(source).getValue(BlockVandorDoor.OPEN)==afterClick,
                    "redstone-triggered door ignored right-click policy");
            world.setBlockState(power,Blocks.REDSTONE_BLOCK.getDefaultState(),3);
            block.neighborChanged(world.getBlockState(source),world,source,Blocks.REDSTONE_BLOCK,power);
            check(world.getBlockState(source).getValue(BlockVandorDoor.OPEN)
                    ==(trigger!=com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_OFF),
                    "powered trigger behavior");
            world.setBlockToAir(power);
            block.neighborChanged(world.getBlockState(source),world,source,Blocks.AIR,power);
            check(world.getBlockState(source).getValue(BlockVandorDoor.OPEN)
                    ==(trigger!=com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_ON),
                    "unpowered trigger behavior");
        }
        clear(world,source);
        check(item.placeBlockAt(blank.copy(),player,world,source,EnumFacing.UP,.5F,.5F,.5F,state(block)),
                "control pad placement");
        world.setBlockState(source.up(),world.getBlockState(source.up()).withProperty(
                BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.RIGHT),2);
        player.setSneaking(false);
        block.onBlockActivated(world,source.up(),world.getBlockState(source.up()),player,
                EnumHand.MAIN_HAND,EnumFacing.WEST,14F/16,1.5F/16,4.5F/16);
        check(player.openContainer instanceof com.vandorlabs.container.ContainerSpaceDoor
                        && !world.getBlockState(source).getValue(BlockVandorDoor.OPEN),
                "control pad did not open the settings dialog");
        player.closeScreen();
        TileEntitySpaceDoor controlled=tile(world,source);
        controlled.configure(controlled.getDesign(),controlled.getDetail(),controlled.isFramed(),
                controlled.getSlideDirection(),controlled.isMiddle(),controlled.isSliding(),
                controlled.hasHinges(),controlled.getTrigger(),false);
        check(BlockConfigurableSpaceDoor.panelSide(world,source,
                        block.getActualState(world.getBlockState(source),world,source))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE
                        && !com.vandorlabs.persistence.SpaceDoorData.read(
                        new com.vandorlabs.persistence.NbtPrimitiveData(controlled.itemSettings())).panel,
                "Panel: Off did not hide and save the pad");
        ItemStack panelOffCopy=block.getPickBlock(world.getBlockState(source.up()),null,
                world,source.up(),player);
        clear(world,target);
        check(item.placeBlockAt(panelOffCopy,player,world,target,EnumFacing.UP,.5F,.5F,.5F,state(block))
                        && !tile(world,target).hasPanel(),
                "creative pick-block did not preserve Panel: Off");
        controlled.configure(controlled.getDesign(),controlled.getDetail(),controlled.isFramed(),
                controlled.getSlideDirection(),controlled.isMiddle(),controlled.isSliding(),
                controlled.hasHinges(),controlled.getTrigger(),true);
        check(BlockConfigurableSpaceDoor.panelSide(world,source,
                        block.getActualState(world.getBlockState(source),world,source))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.RIGHT,
                "sliding-door pad remained on the old side");
        for (boolean middle:new boolean[]{false,true}) {
            controlled.configure(controlled.getDesign(),controlled.getDetail(),controlled.isFramed(),
                    controlled.getSlideDirection(),middle,true,controlled.hasHinges(),
                    controlled.getTrigger(),true);
            double z0=com.vandorlabs.render.SpaceDoorControlPanel.z0(true)
                    +controlled.positionOffset()*16;
            double z1=com.vandorlabs.render.SpaceDoorControlPanel.z1(true)
                    +controlled.positionOffset()*16;
            check(z0>=0 && z1<=16,"sliding-door pad left its own block");
        }
        controlled.configure(controlled.getDesign(),controlled.getDetail(),controlled.isFramed(),
                controlled.getSlideDirection(),true,false,controlled.hasHinges(),
                controlled.getTrigger(),true);
        IBlockState upper=world.getBlockState(source.up());
        world.setBlockState(source.up(),upper.withProperty(BlockVandorDoor.HINGE,
                BlockDoor.EnumHingePosition.LEFT),2);
        check(BlockConfigurableSpaceDoor.panelSide(world,source,
                        block.getActualState(world.getBlockState(source),world,source))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.RIGHT,
                "single-door pad did not follow its hinge");
        block.onBlockActivated(world,source,world.getBlockState(source),player,
                EnumHand.MAIN_HAND,EnumFacing.SOUTH,.5F,.5F,.5F);
        check(world.getBlockState(source).getValue(BlockVandorDoor.OPEN),
                "manual opening before control pad ray test");
        double panelZ=(com.vandorlabs.render.SpaceDoorControlPanel.z0(false)+1.5
                +controlled.positionOffset()*16)/16;
        Vec3d rayStart=new Vec3d(source.getX()-1,source.getY()+18/16.0,source.getZ()+panelZ);
        Vec3d rayEnd=new Vec3d(source.getX()+2,rayStart.y,rayStart.z);
        RayTraceResult pad=block.collisionRayTrace(world.getBlockState(source.up()),world,
                source.up(),rayStart,rayEnd);
        check(pad!=null && pad.sideHit==EnumFacing.WEST,
                "open rotating door control pad is not targetable");
        world.setBlockState(source.up(),world.getBlockState(source.up()).withProperty(
                BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.RIGHT),2);
        BlockPos east=source.east(),west=source.west();
        for (BlockPos adjacent:new BlockPos[]{east,west})
            world.setBlockState(adjacent.down(),Blocks.STONE.getDefaultState(),2);
        check(item.placeBlockAt(blank.copy(),player,world,east,EnumFacing.UP,.5F,.5F,.5F,state(block)),
                "right adjacent door placement");
        world.setBlockState(east.up(),world.getBlockState(east.up()).withProperty(
                BlockVandorDoor.HINGE,BlockDoor.EnumHingePosition.LEFT),2);
        check(block.getActualState(world.getBlockState(source),world,source).getValue(
                        com.vandorlabs.blocks.BlockConnectingDetailedDoor.PAIRED)
                        && block.getActualState(world.getBlockState(east),world,east).getValue(
                        com.vandorlabs.blocks.BlockConnectingDetailedDoor.PAIRED),
                "adjacent doors did not form a pair");
        check(BlockConfigurableSpaceDoor.panelSide(world,source,
                        block.getActualState(world.getBlockState(source),world,source))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.LEFT
                        && BlockConfigurableSpaceDoor.panelSide(world,east,
                        block.getActualState(world.getBlockState(east),world,east))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE,
                "double doors did not keep just one hinge-side pad");
        for (BlockPos part:new BlockPos[]{source,east}) {
            TileEntitySpaceDoor t=tile(world,part);
            t.configure(t.getDesign(),t.getDetail(),t.isFramed(),t.getSlideDirection(),
                    t.isMiddle(),true,t.hasHinges(),t.getTrigger(),true);
        }
        check(BlockConfigurableSpaceDoor.panelSide(world,source,
                        block.getActualState(world.getBlockState(source),world,source))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE
                        && BlockConfigurableSpaceDoor.panelSide(world,east,
                        block.getActualState(world.getBlockState(east),world,east))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.RIGHT,
                "sliding double doors did not use the opposite outer jamb");
        check(item.placeBlockAt(blank.copy(),player,world,west,EnumFacing.UP,.5F,.5F,.5F,state(block)),
                "left adjacent door placement");
        check(BlockConfigurableSpaceDoor.panelSide(world,source,
                        block.getActualState(world.getBlockState(source),world,source))
                        ==com.vandorlabs.render.SpaceDoorControlPanel.Side.NONE,
                "door with neighbors on both sides retained a pad");
        for (BlockPos adjacent:new BlockPos[]{east,west}) {
            clear(world,adjacent);
            world.setBlockToAir(adjacent.down());
        }
        for (BlockPos pos:new BlockPos[]{source,target,neighbor}) clear(world,pos);
        String[] glasses={"space_glass_small","space_glass","space_glass_large"};
        for (boolean rotated:new boolean[]{false,true}) {
            EnumFacing along=rotated?EnumFacing.SOUTH:EnumFacing.EAST;
            for (int i=0;i<3;i++) {
                Block glass=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",glasses[i]));
                world.setBlockState(source.offset(along,i),glass.getDefaultState().withProperty(
                        com.vandorlabs.blocks.BlockGlassWall.ROTATED,rotated),3);
            }
            for (int i=0;i<3;i++) {
                BlockPos p=source.offset(along,i);
                IBlockState actual=world.getBlockState(p).getActualState(world,p);
                check(actual.getValue(com.vandorlabs.blocks.BlockGlassWall.LEFT)==(i==0),"mixed glass left seam");
                check(actual.getValue(com.vandorlabs.blocks.BlockGlassWall.RIGHT)==(i==2),"mixed glass right seam");
                check(((com.vandorlabs.blocks.BlockSpaceGlass)actual.getBlock()).detail().equals(
                        TileEntitySpaceDoor.DETAILS[i]),"glass detail selection");
            }
            for (int i=0;i<3;i++) world.setBlockToAir(source.offset(along,i));
        }
        System.out.println("[vandorlabs][reprolab] space-door-settings PASS: "+cases
                +" upper/lower pick-and-place cases, defaults, saved settings and neighbor precedence");
    }
}
