package com.vandorlabs.gear;

import com.vandorlabs.gear.track.*;
import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Real Large/regular arm placement and Track break/replace events; no manual recovery rebuild. */
final class TrackLifecycleRuntimeChecks {
    private static int checks;
    static void run(WorldServer world,EntityPlayerMP actor,BlockPos root){
        ItemStack held=actor.getHeldItemMainhand();boolean creative=actor.capabilities.isCreativeMode;Vec3d before=actor.getPositionVector();checks=0;
        try{actor.capabilities.isCreativeMode=true;actor.setPosition(root.getX()+10,root.getY(),root.getZ()+10);
            for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_EXTENDER,GearRegistry.LARGE_GEAR_EXTENDER})for(int style=0;style<3;style++)for(boolean active:new boolean[]{false,true}){
                clear(world,root);world.setBlockState(root.up(),Blocks.IRON_BLOCK.getDefaultState(),2);
                for(int n=0;n<2;n++){actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(block,4));BlockPos support=root.up().down(n);require(Item.getItemFromBlock(block).onItemUse(actor,world,support,EnumHand.MAIN_HAND,EnumFacing.DOWN,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"actual arm item hangs from previous section "+block.getRegistryName());}
                GearTileEntity arm=(GearTileEntity)world.getTileEntity(root);pump(world,root);require(arm.isOperational()&&arm.assembly().columnHeight()==2&&arm.activeExtension()==2,"two stacked sections infer two blocks of extra travel");
                NBTTagCompound settings=arm.settings();settings.setDouble("ActiveExtension",-2);settings.setBoolean("Deployed",!active);arm.configure(settings);require(arm.activeExtension()==2,"legacy configured reach cannot collapse automatic stack");pump(world,root);
                BlockPos foot=root.down(2);place(world,actor,arm,foot,style,active,false);place(world,actor,arm,foot.east(),style,active,true);pump(world,root);
                require(arm.assembly().kind()==GearTopology.FootKind.TRACK&&arm.assembly().feet().size()==2,"initial two-Track group forms");double pose=arm.progress();boolean target=arm.target();require(pose==(active?0:1),"initial endpoint follows physical placement");
                if(block.sizeClass()==2){
                    world.destroyBlock(foot,false);pump(world,root);verify(world,arm,foot,1,pose,target,"remove anchor; surviving Track still meets Large footprint");require(world.getTileEntity(foot.east()) instanceof TrackTile,"surviving lateral Track stays physically present");
                    NBTTagCompound saved=arm.writeToNBT(new NBTTagCompound());GearTileEntity copy=(GearTileEntity)net.minecraft.tileentity.TileEntity.create(world,saved);require(copy.assembly()!=null&&copy.assembly().feet().size()==1&&copy.assembly().anchorFoot().x==1,"offset Large Track contact survives exact NBT");NBTTagCompound rotated=saved.copy();for(int n=0;n<4;n++)GearData.rotate(rotated,1);require(saved.equals(rotated),"offset contact survives four parking rotations");
                    // Replace the missing anchor with a real ordinary block item. Its
                    // neighbour events must rejoin it at the existing active/rest pose.
                    placeRest(world,actor,foot,style);pump(world,root);verify(world,arm,foot,2,pose,target,"replace anchor without rebuilding");
                }
                world.destroyBlock(foot.east(),false);pump(world,root);verify(world,arm,foot,1,pose,target,"2 to1");
                place(world,actor,arm,foot.east(),style,active,true);pump(world,root);verify(world,arm,foot,2,pose,target,"replace lateral Track");
                world.destroyBlock(foot.east(),false);world.destroyBlock(foot,false);pump(world,root);require(arm.isOperational()&&arm.assembly().kind()==GearTopology.FootKind.NONE&&arm.assembly().columnHeight()==2,"removing all Tracks retains live two-section arm");require(arm.progress()==pose&&arm.target()==target,"zero-foot transition preserves stationary endpoint");
                place(world,actor,arm,foot,style,active,false);pump(world,root);verify(world,arm,foot,1,pose,target,"0 to1 without rebuilding column");
                require(world.getTileEntity(root)==arm&&world.getBlockState(root.down()).getBlock()==block,"editing feet never replaces either arm section");
            }
        }finally{clear(world,root);actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.capabilities.isCreativeMode=creative;actor.setPosition(before.x,before.y,before.z);}
        System.out.println("[vandorlabs][reprolab] track-foot-lifecycle PASS checks="+checks+" actual regular/Large stacked items three styles rest/active endpoints 2-to1 replacement 0-to1 same controller automatic travel");
    }
    private static void place(WorldServer world,EntityPlayerMP actor,GearTileEntity arm,BlockPos at,int style,boolean active,boolean append){
        ItemStack stack=new ItemStack(TrackRegistry.COMPACT,4);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("TrackStyle",style);stack.setTagInfo("ModularGearSettings",settings);actor.setHeldItem(EnumHand.MAIN_HAND,stack);
        if(active){BlockPos clicked=append?at.west():arm.getPos();Vec3d point=append?new Vec3d(at.west().down(2)).addVector(1,.5,.5):new Vec3d(arm.getPos()).addVector(.5,-3,.5);require(GearInteractions.attachTrack(actor,arm,new RayTraceResult(point,append?EnumFacing.EAST:EnumFacing.DOWN,clicked)),"ordinary held Track attaches to active endpoint");}
        else placeRest(world,actor,at,style);
    }
    private static void placeRest(WorldServer world,EntityPlayerMP actor,BlockPos at,int style){ItemStack stack=new ItemStack(TrackRegistry.COMPACT,4);NBTTagCompound settings=new NBTTagCompound();settings.setInteger("TrackStyle",style);stack.setTagInfo("ModularGearSettings",settings);actor.setHeldItem(EnumHand.MAIN_HAND,stack);require(((ItemBlock)Item.getItemFromBlock(TrackRegistry.COMPACT)).placeBlockAt(stack,actor,world,at,EnumFacing.DOWN,.5F,.5F,.5F,TrackRegistry.COMPACT.getDefaultState()),"actual Track item placement at logical attachment cell");}
    private static void verify(WorldServer world,GearTileEntity arm,BlockPos foot,int count,double pose,boolean target,String stage){require(arm.isOperational()&&arm.assembly().kind()==GearTopology.FootKind.TRACK&&arm.assembly().feet().size()==count,"remaining Track group heals from lifecycle events: "+stage+" "+arm.error());require(arm.progress()==pose&&arm.target()==target,"Track editing preserves root pose and target: "+stage);for(com.vandorlabs.gear.core.GearTopology.Cell cell:arm.assembly().feet())require(((GearTileEntity)world.getTileEntity(arm.localCell(cell))).loadedController()==arm,"every surviving/replaced foot binds to same root");}
    private static void pump(WorldServer world,BlockPos root){for(int tick=0;tick<45;tick++)for(BlockPos p:BlockPos.getAllInBox(root.add(-2,-6,-2),root.add(3,1,3)))if(world.getTileEntity(p) instanceof GearTileEntity)((GearTileEntity)world.getTileEntity(p)).update();}
    private static void clear(WorldServer world,BlockPos root){for(BlockPos p:BlockPos.getAllInBox(root.add(-3,-8,-3),root.add(5,2,5)))if(!world.isAirBlock(p)){world.removeTileEntity(p);world.setBlockToAir(p);}}
    private static void require(boolean ok,String message){checks++;if(!ok)throw new IllegalStateException("Track foot lifecycle: "+message);}
    private TrackLifecycleRuntimeChecks(){}
}
