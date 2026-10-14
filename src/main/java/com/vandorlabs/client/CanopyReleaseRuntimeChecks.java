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
    private static final class PlacementGuard {
        final BlockPos denied;boolean seen;
        PlacementGuard(BlockPos denied){this.denied=denied;}
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void place(net.minecraftforge.event.world.BlockEvent.PlaceEvent event){if(event.getPos().equals(denied)){seen=true;event.setCanceled(true);}}
    }
    private static int farUses;
    private static final BlockPos BASE=new BlockPos(80,100,80);
    private static void require(boolean value,String text){if(!value)throw new IllegalStateException("canopy 2.0: "+text);}
    private static void clear(World world){for(BlockPos p:BlockPos.getAllInBox(BASE.add(-10,-10,-10),BASE.add(10,10,10)))world.setBlockToAir(p);}
    private static LargeCanopySettings copy(LargeCanopySettings settings){NBTTagCompound tag=new NBTTagCompound();settings.write(tag);LargeCanopySettings result=new LargeCanopySettings();result.read(tag);return result;}
    static void run(EntityPlayerMP player) {
        World world=player.world;boolean sneak=player.isSneaking();ItemStack held=player.getHeldItemMainhand();float yaw=player.rotationYaw,pitch=player.rotationPitch;Vec3d originalPosition=player.getPositionVector();
        try {
            player.setSneaking(false);farUses=0;
            CanopyMesh.Face probe=new CanopyMesh.Face();probe.vertices=new Vec3d[]{new Vec3d(1.5,3,1)};probe.uv=new double[][]{{0,0}};probe.material=probe.baseMaterial="glass";probe.group="shell";
            LargeCanopySettings top=new LargeCanopySettings();top.openable=true;top.hinge=2;
            require(CanopyPose.faces(Collections.singletonList(probe),"front_canopy_glass",3,top,.5).get(0).vertices[0].y>3,"top hinge lifts the projecting front away from the cabin");
            for(String kind:new String[]{"front_canopy_glass","large_front_canopy_glass"})for(EnumFacing side:EnumFacing.values()) {
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
                PlacementGuard guard=new PlacementGuard(inside);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(guard);
                try{
                    net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock denied=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,anchor,front.sideHit,front.hitVec);
                    new CanopyInteractions().placeInside(denied);
                    require(guard.seen && denied.getCancellationResult()==EnumActionResult.FAIL && world.isAirBlock(inside) && player.getHeldItemMainhand().getCount()==2,"outside placement honors Forge cancellation and restores the held stack");
                }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(guard);}
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,anchor,front.sideHit,front.hitVec);
                new CanopyInteractions().placeInside(click);
                require(click.isCanceled() && click.getCancellationResult()==EnumActionResult.SUCCESS && world.getBlockState(inside).getBlock()==Blocks.IRON_BLOCK,"outside click places an actual cabin block");
                require(player.getHeldItemMainhand().getCount()==(player.capabilities.isCreativeMode?2:1),"outside placement consumes items only in survival");
                next.shade=12;next.tint=2;require(LargeCanopyOwnership.apply(tile,player,next),"finish edit preserves cabin blocks");
                for(int hinge=0;hinge<4;hinge++) {
                    next.hinge=hinge;require(LargeCanopyOwnership.apply(tile,player,next),"hinge setting");
                    animatedFrontSurface(tile);
                    tile.toggle();for(int tick=0;tick<16;tick++){tile.update();if(tick%4==3)animatedFrontSurface(tile);}require(tile.progress==1 && !tile.boxes().equals(CanopyMesh.collision(tile.faces(0),"posed",tile.width(),0,tile.facing(),anchor)),"hinge changes mounted collision");
                    if(!useFarSurface(tile,player))tile.toggle();for(int tick=0;tick<16;tick++){tile.update();if(tick%4==3)animatedFrontSurface(tile);}require(tile.progress==0,"hinge returns closed kind="+kind+" mount="+side+" hinge="+hinge+" target="+tile.targetOpen+" progress="+tile.progress+" removing="+tile.removing+" draws="+tile.draws());
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
            require(farUses>0,"opened tips beyond the anchor interaction limit are exercised");
            require(!new LargeCanopySettings().slideForward,"direction defaults backward");
            opening(world,player);backToBack(world,player);CanopySurfaceRuntimeChecks.run();copyFinishes(world,player);slidingTrapdoors(world,player);button(world,player);seat(world);
        }finally{clear(world);player.setHeldItem(EnumHand.MAIN_HAND,held);player.setSneaking(sneak);player.rotationYaw=yaw;player.rotationPitch=pitch;player.setPosition(originalPosition.x,originalPosition.y,originalPosition.z);}
        System.out.println("[vandorlabs][reprolab] canopy-2.0 PASS six-face large mounts hinges=4 outside-placement exact-footprint removal opening-modes slide-directions angles ceiling joins channels copy floor-button pilot-armrests ordinary-sliding-selection far-surface-use="+farUses);
    }
    private static void animatedFrontSurface(TileEntityCanopy tile){
        Vec3d center=null,normal=null;double largest=0;
        for(CanopyMesh.Face face:tile.faces())if(face.glazing)for(int i=1;i<face.vertices.length-1;i++){
            Vec3d a=face.vertices[0],b=face.vertices[i],c=face.vertices[i+1],cross=b.subtract(a).crossProduct(c.subtract(a));
            if(cross.lengthSquared()<=largest)continue;largest=cross.lengthSquared();
            center=a.add(b).add(c).scale(1D/3);normal=cross.normalize();
        }
        require(center!=null,"animated front retains a nondegenerate panel");
        Vec3d start=CanopyMesh.rotate(center.add(normal.scale(.01)),tile.facing()).add(new Vec3d(tile.getPos()));
        Vec3d end=CanopyMesh.rotate(center.subtract(normal.scale(.01)),tile.facing()).add(new Vec3d(tile.getPos()));
        Vec3d expected=CanopyMesh.rotate(center,tile.facing()).add(new Vec3d(tile.getPos()));
        RayTraceResult hit=tile.trace(start,end);
        String detail=tile.block().kind+" mount="+tile.large.mount+" facing="+tile.facing()+" hinge="+tile.large.hinge+" progress="+tile.progress;
        require(hit!=null && hit.hitVec.squareDistanceTo(expected)<1e-12,"animated front pick matches panel "+detail);
        require(tile.boxes().stream().anyMatch(box->box.grow(1e-6).contains(expected)),"animated front collision follows panel "+detail);
        require(tile.getRenderBoundingBox().grow(1e-6).contains(expected),"animated front stays inside render bounds "+detail);
        List<CanopyMesh.Face> rendered=new CanopyRenderGeometry(tile.baseFaces()).posed(tile.block().kind,tile.width(),tile.large,tile.progress);
        RayTraceResult drawn=CanopyMesh.trace(rendered,"posed",tile.width(),0,tile.facing(),tile.getPos(),start,end);
        require(drawn!=null && drawn.hitVec.squareDistanceTo(expected)<1e-12,"animated front rendered surface matches picking "+detail);
        List<CanopyMesh.Face> closed=tile.faces(0),posed=tile.faces();
        require(closed.size()==posed.size(),"hinge retains face topology "+detail);
        for(int f=0;f<posed.size();f++)for(int v=1;v<posed.get(f).vertices.length;v++){
            double before=closed.get(f).vertices[0].squareDistanceTo(closed.get(f).vertices[v]),after=posed.get(f).vertices[0].squareDistanceTo(posed.get(f).vertices[v]);
            require(Math.abs(before-after)<1e-8,"hinge keeps frame rigid "+detail);
        }
    }
    private static void backToBack(World world,EntityPlayerMP player){
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy");
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int mount=0;mount<2;mount++)for(double x:new double[]{.25,.75}){
            clear(world);IBlockState state=block.getDefaultState().withProperty(BlockCanopy.FACING,facing);world.setBlockState(BASE,state,2);
            ItemStack item=new ItemStack(block,2);NBTTagCompound tag=new NBTTagCompound();tag.setInteger("CanopyMount",mount);item.setTagCompound(tag);
            block.onBlockPlacedBy(world,BASE,state,player,item);
            Vec3d eye=CanopyMesh.rotate(new Vec3d(x,.5,3.5),facing).add(new Vec3d(BASE));
            Vec3d target=CanopyMesh.rotate(new Vec3d(x,.5,2),facing).add(new Vec3d(BASE)),look=target.subtract(eye).normalize();
            player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);player.rotationYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));player.rotationPitch=0;
            player.setHeldItem(EnumHand.MAIN_HAND,item);
            TileEntityCanopy first=TileEntityCanopy.at(world,BASE);RayTraceResult hit=first.trace(eye,target.add(look));require(hit!=null,"rear glass can be selected");
            Vec3d local=hit.hitVec.subtract(new Vec3d(hit.getBlockPos()));
            EnumActionResult result=player.interactionManager.processRightClickBlock(player,world,item,EnumHand.MAIN_HAND,hit.getBlockPos(),hit.sideHit,(float)local.x,(float)local.y,(float)local.z);
            TileEntityCanopy second=TileEntityCanopy.at(world,BASE.offset(facing.getOpposite(),3));
            require(result==EnumActionResult.SUCCESS && second!=null && second.facing()==facing.getOpposite(),"rear click places opposite canopy "+facing+" mount="+mount);
            require(second.large.mount==mount,"back-to-back canopy retains ceiling mount");
            require(!first.targetOpen,"placing a canopy does not toggle its support");
            require((first.mask()&8)!=0 && (second.mask()&8)!=0,"back-to-back rear glazing has an open seam");
        }
        System.out.println("[vandorlabs][reprolab] canopy-back-to-back PASS real interaction facings=4 mounts=2 rear-points=2");
    }
    private static boolean useFarSurface(TileEntityCanopy tile,EntityPlayerMP player){
        for(CanopyMesh.Face face:tile.faces())for(int i=1;i<face.vertices.length-1;i++){
            Vec3d a=CanopyMesh.rotate(face.vertices[0],tile.facing()).add(new Vec3d(tile.getPos())),b=CanopyMesh.rotate(face.vertices[i],tile.facing()).add(new Vec3d(tile.getPos())),c=CanopyMesh.rotate(face.vertices[i+1],tile.facing()).add(new Vec3d(tile.getPos()));
            Vec3d normal=b.subtract(a).crossProduct(c.subtract(a));if(normal.lengthSquared()<1e-12)continue;normal=normal.normalize();Vec3d center=a.add(b).add(c).scale(1D/3);
            for(int side:new int[]{1,-1}){
                Vec3d eye=center.add(normal.scale(2*side)),look=center.subtract(eye).normalize();
                player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);
                if(player.getDistanceSqToCenter(tile.getPos())<=64)continue;
                player.rotationYaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z));player.rotationPitch=(float)-Math.toDegrees(Math.asin(MathHelper.clamp(look.y,-1,1)));player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
                require(!com.vandorlabs.network.MessageCanopyUse.interact(player,tile.getPos(),EnumHand.MAIN_HAND,eye.add(look.scale(50))),"far-surface packet cannot extend player reach");
                boolean before=tile.targetOpen;boolean used=com.vandorlabs.network.MessageCanopyUse.interact(player,tile.getPos(),EnumHand.MAIN_HAND);
                require(used || tile.targetOpen==before,"far-surface use changed the target without reporting success");
                if(used){
                    require(!tile.targetOpen,"far visible surface closes its stationary owner");
                    require(new com.vandorlabs.container.ContainerLargeCanopy(tile).canInteractWith(player),"far visible surface retains a reachable configuration container");farUses++;return true;
                }
            }
        }
        return false;
    }
    private static void rearCrossbar(TileEntityCanopy tile,LargeCanopySettings settings,int width){
        Vec3d origin=new Vec3d(tile.getPos());
        for(double height:new double[]{.5,.99}){
            Vec3d start=CanopyMesh.rotate(CanopyPose.vertex(new Vec3d(width/2D,height,2.2),"shell","canopy",width,settings,tile.progress),tile.facing()).add(origin);
            Vec3d end=CanopyMesh.rotate(CanopyPose.vertex(new Vec3d(width/2D,height,1.8),"shell","canopy",width,settings,tile.progress),tile.facing()).add(origin);
            require((tile.trace(start,end)!=null)==(height>.9),"joined rear aperture stays open and roof crossbar stays selectable height="+height+" angle="+settings.angle+" width="+width+" mount="+settings.mount+" motion="+settings.motion+" progress="+tile.progress+" mask="+tile.mask());
        }
    }
    private static void opening(World world,EntityPlayerMP player){
        BlockCanopy block=(BlockCanopy)Block.getBlockFromName("vandorlabs:canopy"),glass=(BlockCanopy)Block.getBlockFromName("vandorlabs:regular_canopy_glass");
        for(int mount=0;mount<2;mount++)for(int angle=0;angle<3;angle++)for(int motion=0;motion<2;motion++)for(int width=1;width<=3;width++)for(int direction=0;direction<(motion==0?2:1);direction++) {
            clear(world);LargeCanopySettings settings=new LargeCanopySettings();settings.mount=mount;settings.angle=angle;settings.motion=motion;settings.slideForward=direction==1;
            ItemStack stack=new ItemStack(block);NBTTagCompound tag=new NBTTagCompound();settings.write(tag);stack.setTagCompound(tag);
            for(int column=0;column<width;column++){BlockPos pos=BASE.east(column);world.setBlockState(pos,block.getDefaultState(),2);block.onBlockPlacedBy(world,pos,block.getDefaultState(),player,stack);}
            TileEntityCanopy tile=TileEntityCanopy.at(world,BASE);require(tile.width()==width,"configured shells join");
            BlockPos rear=BASE.south(2);ItemStack rearStack=new ItemStack(glass);rearStack.setTagCompound(tag.copy());world.setBlockState(rear,glass.getDefaultState(),2);glass.onBlockPlacedBy(world,rear,glass.getDefaultState(),player,rearStack);
            require((tile.mask()&8)!=0 && TileEntityCanopy.at(world,rear).mask()!=0,"rear glass joins opening shell");
            rearCrossbar(tile,settings,width);
            List<CanopyMesh.Face> closedFaces=tile.faces(0);
            tile.toggle();for(int tick=0;tick<16;tick++)tile.update();require(tile.progress==1,"configured shell opens");
            rearCrossbar(tile,settings,width);
            if(motion==0){
                List<CanopyMesh.Face> openFaces=tile.faces();boolean moved=false,stationary=false;
                for(int f=0;f<closedFaces.size();f++)for(int v=0;v<closedFaces.get(f).vertices.length;v++){
                    Vec3d delta=openFaces.get(f).vertices[v].subtract(closedFaces.get(f).vertices[v]);
                    if(delta.lengthSquared()<1e-12){stationary=true;continue;}
                    require(Math.abs(delta.z-(settings.slideForward?-2:2))<1e-8 && Math.abs(delta.y-(mount==0?1:-1)/16D)<1e-8,"shell moves selected direction with one-pixel mounted lift");moved=true;
                }
                require(moved && stationary,"shell moves while mounts stay fixed");
                LargeCanopySettings changed=copy(settings);changed.slideForward=!changed.slideForward;
                require(!LargeCanopyOwnership.apply(tile,player,changed),"direction cannot change while open");
                TileEntityCanopy saved=new TileEntityCanopy();saved.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(saved.large.slideForward==settings.slideForward,"direction survives tile save");
                for(EnumFacing facing:EnumFacing.HORIZONTALS){
                    Vec3d center=CanopyMesh.rotate(new Vec3d(.5,.5,1.5+(settings.slideForward?-2:2)),facing).add(new Vec3d(BASE));
                    require(CanopyMesh.trace(openFaces,"posed",width,0,facing,BASE,center.addVector(0,3,0),center.addVector(0,-3,0))!=null,"moved roof remains selectable in every facing");
                }
            }
            closeCoveredShell(world,player,tile);
            for(int tick=0;tick<16;tick++)tile.update();require(tile.progress==0,"configured shell closes");
            if(motion==0){LargeCanopySettings changed=copy(settings);changed.slideForward=!changed.slideForward;require(LargeCanopyOwnership.apply(tile,player,changed),"closed joined direction changes together mount="+mount+" angle="+angle+" width="+width+" direction="+direction+" actual="+tile.large.key()+" progress="+tile.progress+" draws="+tile.draws());for(int column=0;column<width;column++)require(TileEntityCanopy.at(world,BASE.east(column)).large.slideForward==changed.slideForward,"joined constituent direction synchronized");require(LargeCanopyOwnership.apply(tile,player,settings),"restore chosen direction");}
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
        for(String kind:new String[]{"angled_front_1x1","angled_front_1x2","canopy"})for(EnumFacing facing:EnumFacing.HORIZONTALS) {
            clear(world);BlockCanopy nose=(BlockCanopy)Block.getBlockFromName("vandorlabs:"+kind);world.setBlockState(BASE,Blocks.STONE.getDefaultState(),3);player.rotationYaw=facing.getOpposite().getHorizontalAngle();player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(nose));
            require(player.getHeldItemMainhand().getItem().onItemUse(player,world,BASE,EnumHand.MAIN_HAND,facing,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"long nose can mount against a wall");
        }
    }
    private static void closeCoveredShell(World world,EntityPlayerMP player,TileEntityCanopy tile){
        Map<BlockPos,IBlockState> covered=new HashMap<>();Set<BlockPos> reserved=new HashSet<>(tile.cells());
        try{
            // Cover the moved shell with bodywork, leaving its original footprint accessible.
            for(CanopyMesh.Face face:tile.faces())if(face.group.equals("shell")){
                AxisAlignedBB bounds=null;
                for(Vec3d v:face.vertices){Vec3d p=CanopyMesh.rotate(v,tile.facing()).add(new Vec3d(tile.getPos()));AxisAlignedBB point=new AxisAlignedBB(p,p);bounds=bounds==null?point:bounds.union(point);}
                for(BlockPos pos:BlockPos.getAllInBox(new BlockPos(bounds.minX,bounds.minY,bounds.minZ),new BlockPos(bounds.maxX,bounds.maxY,bounds.maxZ))){
                    if(reserved.contains(pos) || world.getBlockState(pos).getBlock() instanceof BlockCanopy)continue;
                    BlockPos fixed=pos.toImmutable();if(!covered.containsKey(fixed))covered.put(fixed,world.getBlockState(fixed));world.setBlockState(fixed,Blocks.STONE.getDefaultState(),2);
                }
            }
            require(!covered.isEmpty(),"bodywork covers moved canopy surfaces");
            player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);player.setSneaking(false);
            for(CanopyMesh.Face face:tile.faces())if(face.group.equals("mount"))for(int i=1;i<face.vertices.length-1;i++){
                Vec3d a=face.vertices[0],b=face.vertices[i],c=face.vertices[i+1],normal=b.subtract(a).crossProduct(c.subtract(a));
                if(normal.lengthSquared()<1e-12)continue;
                Vec3d center=CanopyMesh.rotate(a.add(b).add(c).scale(1D/3),tile.facing()).add(new Vec3d(tile.getPos()));
                Vec3d direction=CanopyMesh.rotate(normal.normalize().addVector(.5,0,.5),tile.facing()).subtract(new Vec3d(.5,0,.5));
                for(int side:new int[]{1,-1}){
                    Vec3d eye=center.add(direction.scale(.75*side));if(!world.isAirBlock(new BlockPos(eye)))continue;
                    player.setPosition(eye.x,eye.y-player.getEyeHeight(),eye.z);
                    if(com.vandorlabs.network.MessageCanopyUse.interact(player,tile.getPos(),EnumHand.MAIN_HAND,center)){
                        require(!tile.targetOpen,"fixed mount closes canopy with covered shell");return;
                    }
                }
            }
            throw new IllegalStateException("covered canopy has no reachable closing mount: "+tile.large.key()+" width="+tile.width());
        }finally{for(Map.Entry<BlockPos,IBlockState> entry:covered.entrySet())world.setBlockState(entry.getKey(),entry.getValue(),2);}
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
    private static void slidingTrapdoors(World world,EntityPlayerMP player){
        for(EnumFacing facing:EnumFacing.HORIZONTALS){
            clear(world);TileEntityProgrammableTrapdoor leaf=TrapdoorGallery.place(world,BASE,false,0,0,true,facing);leaf.setHingeFacing(facing);leaf.requestOpen(true);
            AxisAlignedBB bounds=OffsetTrapdoorInteractions.bounds(leaf);Vec3d center=bounds.getCenter(),above=center.addVector(0,2,0),below=center.addVector(0,-2,0);
            require(!new AxisAlignedBB(BASE).contains(center),"ordinary sliding leaf moves outside its owning cell");
            RayTraceResult hit=OffsetTrapdoorInteractions.trace(world,above,below);require(hit!=null && hit.getBlockPos().equals(BASE),"broad moved sliding surface remains selectable from above");
            require(world.getCollisionBoxes(null,new AxisAlignedBB(center,center).grow(.04)).stream().anyMatch(box->box.intersects(bounds)),"moved sliding leaf retains collision");
            BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)world.getBlockState(BASE).getBlock();block.onBlockActivated(world,BASE,world.getBlockState(BASE),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
            require(!world.getBlockState(BASE).getValue(net.minecraft.block.BlockTrapDoor.OPEN),"moved selection closes the original trapdoor owner");
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
