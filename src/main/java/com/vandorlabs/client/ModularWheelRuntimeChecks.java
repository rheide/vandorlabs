package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real placement, full strokes, posed picking and footprint cleanup for both arm sizes. */
final class ModularWheelRuntimeChecks {
    static GearTileEntity build(WorldServer world,EntityPlayerMP player,BlockPos at,boolean large,boolean swing,EnumFacing facing,int height){
        GearBlock arm=large?(swing?GearRegistry.LARGE_GEAR_SWING_ARM:GearRegistry.LARGE_GEAR_EXTENDER):(swing?GearRegistry.GEAR_SWING_ARM:GearRegistry.GEAR_EXTENDER);
        GearBlock wheel=large?GearRegistry.LARGE_LANDING_WHEEL:GearRegistry.LANDING_WHEEL;
        place(world,player,at.down(height),wheel,facing);
        for(int y=0;y<height;y++)place(world,player,at.down(y),arm,facing);
        for(BlockPos p:arm.componentCells(world,at))world.setBlockState(p.up(),Blocks.IRON_BLOCK.getDefaultState());
        GearTileEntity root=(GearTileEntity)world.getTileEntity(at);require(root.rebuild(),"formation "+root.error());return root.loadedController();
    }
    private static void place(WorldServer world,EntityPlayerMP player,BlockPos at,GearBlock block,EnumFacing facing){IBlockState state=block.getDefaultState().withProperty(GearBlock.FACING,facing);ItemBlock item=(ItemBlock)Item.getItemFromBlock(block);require(item.placeBlockAt(new ItemStack(block),player,world,at,EnumFacing.UP,.5F,.5F,.5F,state),"item placement "+block.getRegistryName());require(block.componentIntact(world,at),"complete placement footprint");}
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos at=new BlockPos(475,215,245);Vec3d old=player.getPositionVector();ItemStack held=player.getHeldItemMainhand();float yaw=player.rotationYaw,pitch=player.rotationPitch;
        try{for(boolean large:new boolean[]{false,true})for(boolean swing:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int height:new int[]{1,3}){
            clear(world,at);GearTileEntity root=build(world,player,at,large,swing,facing,height);
            require(root.columns().size()==1&&root.members().size()==(large?4:1)*(height+1),"one physical support owns complete footprint");require(root.assembly().kind()==GearTopology.FootKind.WHEEL,"wheel topology");
            root.target(false);for(int n=0;n<32;n++)root.update();require(root.progress()==0,"full stow "+large+" "+swing+" "+facing+" h="+height+" progress="+root.progress()+" "+obstruction(root));
            java.util.List<AxisAlignedBB> boxes=root.collisionBoxes();AxisAlignedBB foot=null;for(int i=boxes.size()-root.assembly().feet().size();i<boxes.size();i++)if(boxes.get(i).getAverageEdgeLength()>0){foot=boxes.get(i);break;}require(foot!=null,"visible wheel geometry");Vec3d start=new Vec3d(foot.minX-2,(foot.minY+foot.maxY)/2,(foot.minZ+foot.maxZ)/2),end=new Vec3d(foot.maxX+1,start.y,start.z);RayTraceResult hit=GearInteractions.trace(world,start,end);require(hit!=null,"stowed wheel is selectable outside source cell");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.setPositionAndUpdate(start.x,start.y-player.getEyeHeight(),start.z);player.rotationYaw=-90;player.rotationPitch=0;RayTraceResult aimed=GearInteractions.trace(world,player.getPositionEyes(1),player.getPositionEyes(1).add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(5.5)));require(aimed!=null&&aimed.getBlockPos().equals(hit.getBlockPos()),"server aim agrees with visible wheel "+large+" "+swing+" "+facing+" h="+height+" expected="+hit+" actual="+aimed+" eye="+player.getPositionEyes(1));GearInteractions.use(player,hit.getBlockPos());require(root.target(),"server manual click on stowed wheel deploys "+large+" "+swing+" "+facing+" h="+height+" dead="+player.isDead+" spectator="+player.isSpectator());
            player.setPositionAndUpdate(old.x,old.y,old.z);for(int n=0;n<32;n++)root.update();require(root.fullyDeployed(),"redeploy wheel");
            GearTileEntity copy=(GearTileEntity)net.minecraft.tileentity.TileEntity.create(world,root.writeToNBT(new NBTTagCompound()));copy.setWorld(world);require(copy.assembly().kind()==GearTopology.FootKind.WHEEL&&copy.members().size()==root.members().size(),"save complete wheel footprint "+large+" "+swing+" "+facing+" h="+height+" copy="+copy.members().size()+" root="+root.members().size());
            if(large&&swing&&facing==EnumFacing.NORTH&&height==1)unloadedCache(world,root);
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(BlockPos p:root.members())cells.add(new VehicleStructure.Cell(p.subtract(at.add(-4,-4,-4)),world.getBlockState(p),world.getTileEntity(p).writeToNBT(new NBTTagCompound())));require(!VehicleStructure.display(cells).hasNonWheeledGear,"wheel arms permit Taxi classification");
            if(large){GearBlock arm=(GearBlock)world.getBlockState(at).getBlock();List<BlockPos> footprint=arm.componentCells(world,at);BlockPos helper=footprint.get(3);world.destroyBlock(helper,false);for(BlockPos cell:footprint)require(world.getBlockState(cell).getBlock()!=arm,"all companion cells removed");require(world.getBlockState(at).getBlock()!=arm,"root removed when helper mined");}
        }
        clear(world,at);world.setBlockState(at.east(),Blocks.OBSIDIAN.getDefaultState());ItemBlock item=(ItemBlock)Item.getItemFromBlock(GearRegistry.LARGE_GEAR_EXTENDER);require(!item.placeBlockAt(new ItemStack(GearRegistry.LARGE_GEAR_EXTENDER),player,world,at,EnumFacing.UP,.5F,.5F,.5F,GearRegistry.LARGE_GEAR_EXTENDER.getDefaultState()),"blocked 2x2 placement refused");require(world.isAirBlock(at)&&world.getBlockState(at.east()).getBlock()==Blocks.OBSIDIAN,"failed placement preserves world");
        taxi(world,player);
        }finally{clear(world,at);player.setHeldItem(EnumHand.MAIN_HAND,held);player.rotationYaw=yaw;player.rotationPitch=pitch;player.setPositionAndUpdate(old.x,old.y,old.z);}
        System.out.println("[vandorlabs][reprolab] modular-wheels PASS 32 size/arm/facing/height combinations placement stow manual picking redeploy persistence Taxi footprint cleanup");
    }
    private static void unloadedCache(WorldServer world,GearTileEntity root){
        BlockPos far=new BlockPos(6400,150,6400);require(!world.isBlockLoaded(far,false),"cache fixture must begin unloaded");
        NBTTagCompound data=root.writeToNBT(new NBTTagCompound());data.setInteger("x",far.getX());data.setInteger("y",far.getY());data.setInteger("z",far.getZ());
        GearTileEntity pending=new GearTileEntity();pending.readFromNBT(data);pending.setWorld(world);pending.readFromNBT(data);pending.onLoad();
        try{require(!world.isBlockLoaded(far,false),"loading a saved gear cache must not request its unpublished chunk");}finally{pending.invalidate();}
    }
    private static String obstruction(GearTileEntity root){double next=com.vandorlabs.gear.core.GearMotion.advance(root.progress(),false,1,GearTileEntity.ANIMATION_TICKS);List<AxisAlignedBB> before=GearGeometry.boxes(root,root.progress()),after=GearGeometry.boxes(root,next);for(int i=0;i<after.size();i++){if(before.get(i).equals(after.get(i))||after.get(i).getAverageEdgeLength()==0)continue;AxisAlignedBB sweep=GearGeometry.swept(root,i,before.get(i),after.get(i),root.progress(),next);List<AxisAlignedBB> foreign=GearGeometry.foreign(root.getWorld(),root,sweep);if(!foreign.isEmpty())return "piece="+i+" sweep="+sweep+" obstacle="+foreign;}return "no block obstruction";}
    private static void taxi(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos at=new BlockPos(430,140,235);EntityGroundVehicle craft=null;
        try{for(boolean large:new boolean[]{false,true})for(boolean swing:new boolean[]{false,true}){
            for(BlockPos p:BlockPos.getAllInBox(at.add(-2,-6,-2),at.add(10,4,10)))if(!world.isAirBlock(p))world.setBlockToAir(p);
            int height=swing?1:3;
            for(int x=0;x<8;x++)for(int z=0;z<8;z++){world.setBlockState(at.add(x,-height-1,z),Blocks.STONE.getDefaultState());world.setBlockState(at.add(x,1,z),Blocks.IRON_BLOCK.getDefaultState());}
            for(BlockPos root:new BlockPos[]{at,at.east(4),at.south(4)})build(world,player,root,large,swing,EnumFacing.NORTH,height);
            BlockPos seat=at.add(2,2,2);net.minecraft.block.Block pilot=net.minecraft.block.Block.getBlockFromName("vandorlabs:pilot_seat");world.setBlockState(seat,pilot.getDefaultState());pilot.onBlockPlacedBy(world,seat,world.getBlockState(seat),player,new ItemStack(pilot));world.setBlockState(at.add(3,2,2),net.minecraft.block.Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState());
            for(int x=5;x<8;x++)world.setBlockState(at.add(x,2,5),net.minecraft.block.Block.getBlockFromName("vandorlabs:antigravity_plate").getDefaultState());
            VehicleScan scan=new VehicleScan(world,seat);for(int n=0;n<200&&!scan.step(128);n++);require(scan.error==null&&scan.result!=null,"Taxi scan "+scan.error);require(scan.result.gearCount==3&&!scan.result.hasNonWheeledGear,"three wheeled supports count once each");
            player.setPositionAndUpdate(at.getX()-2,at.getY()+2,at.getZ()-2);craft=VehicleTransfer.assemble(world,scan.result,scan.origin,player);require(!craft.hover,"wheel craft enters Taxi");require(craft.collisionCache.bypass().isEmpty(),"wheel gear uses cached vehicle collision");require(craft.setHover(true),"wheel craft enters Hover");
            capturedStroke(craft,false);capturedStroke(craft,true);require(craft.setHover(false),"deployed wheel craft returns to Taxi");craft.rotationYaw=90;
            VehicleTransfer.park(world,craft,scan.origin,player);craft=null;int roots=0;
            for(BlockPos p:BlockPos.getAllInBox(at.add(-1,-height,-1),at.add(9,1,9)))if(world.getTileEntity(p) instanceof GearTileEntity){GearTileEntity tile=(GearTileEntity)world.getTileEntity(p);if(tile.isController()){require(tile.rebuild(),"rotated wheel gear reassembles "+tile.error());roots++;}}
            require(roots==3,"three rotated wheel controllers restored");
        }}finally{if(craft!=null&&!craft.isDead){try{craft.hover=false;VehicleTransfer.park(world,craft,new BlockPos(craft.posX,craft.posY,craft.posZ),player);}catch(Exception cleanup){craft.setDead();}}for(BlockPos p:BlockPos.getAllInBox(at.add(-2,-6,-2),at.add(10,4,10)))if(!world.isAirBlock(p))world.setBlockToAir(p);}
    }
    private static void capturedStroke(EntityGroundVehicle craft,boolean deployed)throws Exception{
        for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController())((GearTileEntity)raw).target(deployed);
        for(int tick=0;tick<600;tick++){ModularGearVehicles.tick(craft);boolean done=true;for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController()&&Math.abs(((GearTileEntity)raw).progress()-(deployed?1:0))>.00001)done=false;if(done)return;Thread.sleep(3);}
        String detail="";for(net.minecraft.tileentity.TileEntity raw:craft.view.tiles.values())if(raw instanceof GearTileEntity&&((GearTileEntity)raw).isController()){GearTileEntity root=(GearTileEntity)raw;detail+=" root="+root.getPos()+" width="+root.armWidth()+" swing="+root.isSwingArmController()+" progress="+root.progress()+" target="+root.target()+" "+obstruction(root);}throw new IllegalStateException("Captured wheel stroke failed deployed="+deployed+detail);
    }
    private static void clear(WorldServer world,BlockPos at){for(BlockPos p:BlockPos.getAllInBox(at.add(-6,-8,-6),at.add(6,3,6)))if(!world.isAirBlock(p))world.setBlockToAir(p);}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("modular wheels: "+message);}
}
