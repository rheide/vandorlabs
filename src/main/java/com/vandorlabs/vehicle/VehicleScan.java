package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Bounded, server-thread flood fill. Gear cells terminate only their own branch. */
public final class VehicleScan {
    private final World world;
    private final boolean attachment;
    private final Set<BlockPos> excluded;
    private final int maxBlocks=com.vandorlabs.config.BlockAvailability.vehicleMaxBlocks();
    private final BlockPos start;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> scheduled = new HashSet<>(), visited = new HashSet<>(), gears = new HashSet<>(), trackSupports=new HashSet<>();
    private final Map<BlockPos, VehicleStructure.Cell> cells = new LinkedHashMap<>();
    private final Set<BlockPos> rampSections=new HashSet<>(),overhangContacts=new HashSet<>(),gearAssemblies=new HashSet<>();
    private final Map<BlockPos,Double> supportBottoms=new HashMap<>(),trackBottoms=new HashMap<>();
    private final Set<BlockPos> supportCells=new HashSet<>();
    private final Set<BlockPos> expandedHigherSupports=new HashSet<>();
    private final Map<BlockPos,BlockPos> supportOwners=new HashMap<>();
    private final Map<BlockPos,Set<BlockPos>> contactLinks=new HashMap<>();
    private int contactChecks;
    private boolean membershipOnly;
    private Set<BlockPos> primaryPositions;
    private int minX, minY, minZ, maxX, maxY, maxZ, seats,autopilots,controllers;
    private BlockPos pilot;
    private Double gearBottomY;
    public BlockPos origin;
    public VehicleStructure result;
    public final Set<BlockPos> airBoundary=new HashSet<>();
    public String error;
    public VehicleScan(World world, BlockPos start) {this(world,start,false,Collections.emptySet());}
    /** Scan an exact world fragment using ordinary vehicle boundaries and complete-assembly checks.
     * Excluded cells belong to the existing craft and may never be captured as part of this fragment. */
    public static VehicleScan attachment(World world,BlockPos seed,Set<BlockPos> excluded){
        if(excluded==null||excluded.size()>VehicleStructure.MAX_BLOCKS)throw new IllegalArgumentException("Invalid attachment exclusion set");
        if(excluded.contains(seed))throw new IllegalArgumentException("The opposing coupler is inside the existing vehicle");
        return new VehicleScan(world,seed,true,excluded);
    }
    private VehicleScan(World world,BlockPos start,boolean attachment,Set<BlockPos> excluded){
        this.world=world;this.attachment=attachment;Set<BlockPos> immutable=new HashSet<>();for(BlockPos at:excluded)immutable.add(at.toImmutable());this.excluded=Collections.unmodifiableSet(immutable);
        this.start=start.toImmutable();enqueue(this.start);
        minX=maxX=start.getX();minY=maxY=start.getY();minZ=maxZ=start.getZ();
    }
    private void enqueue(BlockPos pos){BlockPos key=pos.toImmutable();if(!excluded.contains(key)&&scheduled.add(key))queue.add(key);}
    private void requiredMember(BlockPos pos){require(!excluded.contains(pos),"Attachment cuts an existing vehicle assembly at "+pos);}
    private void enqueueMember(BlockPos pos){requiredMember(pos);enqueue(pos);}
    public BlockPos startPosition(){return start;}
    public int supportCount(){return Math.max(gears.size(),trackSupports.size());}
    public int trackSupportCount(){return trackSupports.size();}
    public boolean hasRequiredSupports(){return gears.size()>=3||trackSupports.size()>=2;}
    private void require(boolean test, String reason) { if(!test) throw new IllegalArgumentException(reason); }
    private void support(BlockLandingGear block,BlockPos root) {
        double bottom=block.vehicleBottomY(world,root);
        require(Double.isFinite(bottom),"Cannot determine landing gear bottom.");
        supportBottoms.put(root,bottom);
        gearBottomY=gearBottomY==null?bottom:Math.min(gearBottomY,bottom);
    }
    private void supportCells(Collection<BlockPos> members,BlockPos owner){for(BlockPos at:members){supportCells.add(at);supportOwners.put(at,owner);}}
    private void upperConnection(BlockPos at){if(!attachment&&world.isBlockLoaded(at.up())&&touches(world,at,world.getBlockState(at),EnumFacing.UP))enqueue(at.up());}
    /** Once a lower boundary is known, higher feet are ordinary attachment surfaces. */
    private boolean expandHigherSupports(){
        if(gearBottomY==null)return false;
        for(BlockPos at:supportCells){
            BlockPos owner=supportOwners.get(at);Double bottom=supportBottoms.get(owner);
            if(bottom==null&&world.getBlockState(owner).getBlock() instanceof BlockLandingGear)bottom=((BlockLandingGear)world.getBlockState(owner).getBlock()).vehicleBottomY(world,owner);
            if(bottom==null||bottom<=gearBottomY+.001||!expandedHigherSupports.add(at))continue;
            for(EnumFacing side:EnumFacing.values())if(touches(world,at,world.getBlockState(at),side))enqueue(at.offset(side));
        }
        return !queue.isEmpty();
    }
    private boolean supportCut(BlockPos a,BlockPos b){
        BlockPos top=a.getY()>b.getY()?a:b,bottom=top==a?b:a;
        if(top.getY()!=bottom.getY()+1||top.getX()!=bottom.getX()||top.getZ()!=bottom.getZ()||!supportCells.contains(top))return false;
        return !supportOwners.containsKey(bottom)||!Objects.equals(supportOwners.get(top),supportOwners.get(bottom));
    }
    private static final class RoleGroup {
        final Set<BlockPos> positions=new HashSet<>();
        final List<VehicleStructure.Cell> cells=new ArrayList<>();
        BlockPos pilot,controller;int controllers,autopilots,pilots;
        int feet,tracks;double bottom=Double.POSITIVE_INFINITY;
    }
    /** Landing contacts separate child controls from the requested hull, without changing saved tile data. */
    private List<VehicleStructure.Cell> roles(){
        List<RoleGroup> groups=new ArrayList<>();Map<BlockPos,RoleGroup> owners=new HashMap<>();
        for(BlockPos seed:cells.keySet())if(!owners.containsKey(seed)){
            RoleGroup group=new RoleGroup();groups.add(group);ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(seed);owners.put(seed,group);
            while(!pending.isEmpty()){
                BlockPos at=pending.removeFirst();group.positions.add(at);VehicleStructure.Cell cell=cells.get(at);group.cells.add(cell);Block block=cell.state.getBlock();
                if(block instanceof BlockVehicleController){group.controllers++;group.controller=at;}
                if(block instanceof BlockPilotSeat&&!cell.state.getValue(BlockPilotSeat.UPPER)){group.pilots++;if(group.pilot==null||at.equals(start))group.pilot=at;}
                TileEntity tile=world.getTileEntity(at);if(tile instanceof TileEntityAutopilot&&!(tile instanceof TileEntityWaypoint))group.autopilots++;
                Set<BlockPos> next=new LinkedHashSet<>();for(EnumFacing side:EnumFacing.values()){BlockPos other=at.offset(side);if(cells.containsKey(other)&&!supportCut(at,other)&&(touches(world,at,cell.state,side)||membershipOnly&&ownedCoupler(cell.state,cells.get(other).state,side)))next.add(other);}next.addAll(contactLinks.getOrDefault(at,Collections.emptySet()));
                for(BlockPos other:next)if(cells.containsKey(other)&&!owners.containsKey(other)){owners.put(other,group);pending.add(other);}
            }
        }
        for(Map.Entry<BlockPos,Double> support:supportBottoms.entrySet()){RoleGroup group=owners.get(support.getKey());if(group!=null){group.feet++;group.bottom=Math.min(group.bottom,support.getValue());}}
        for(Map.Entry<BlockPos,Double> support:trackBottoms.entrySet()){RoleGroup group=owners.get(support.getKey());if(group!=null){group.tracks++;group.bottom=Math.min(group.bottom,support.getValue());}}
        RoleGroup primary=owners.get(start);require(primary!=null,"The selected vehicle component is unavailable.");
        primaryPositions=Collections.unmodifiableSet(new HashSet<>(primary.positions));
        if(!membershipOnly){
        require(primary.controllers<=1,"A craft can contain at most one Vehicle Controller.");require(primary.autopilots<=1,"A craft can contain at most one Autopilot block.");
        if(!attachment)require(primary.pilot!=null,"The selected vehicle needs its own Pilot Seat.");
        for(RoleGroup group:groups)if(group!=primary&&(group.controllers>0||group.pilots>0||group.autopilots>0)){
            require(primary.controller!=null,"The lower vehicle needs its own Vehicle Controller before carrying another complete vehicle.");
            require(group.controllers==1&&group.pilots>=1&&group.autopilots<=1,"Only a complete parked child vehicle may carry additional Pilot Seats or controllers.");
            require((group.feet>=3||group.tracks>=2)&&gearBottomY!=null&&group.bottom>gearBottomY+.001,"A parked child vehicle needs its own higher landing supports.");
        }
        }
        if(primary.pilot!=null)pilot=primary.pilot;
        if(gearBottomY!=null){final double lowest=gearBottomY;gears.removeIf(at->supportBottoms.containsKey(at)&&Math.abs(supportBottoms.get(at)-lowest)>.001);trackSupports.removeIf(at->trackBottoms.containsKey(at)&&Math.abs(trackBottoms.get(at)-lowest)>.001);}
        // Each component starts with its controller. The primary component comes
        // first, retaining canonical control selection through the existing codec.
        List<VehicleStructure.Cell> ordered=new ArrayList<>();List<RoleGroup> orderedGroups=new ArrayList<>();orderedGroups.add(primary);for(RoleGroup group:groups)if(group!=primary)orderedGroups.add(group);
        for(RoleGroup group:orderedGroups){if(group.controller!=null)ordered.add(cells.get(group.controller));for(VehicleStructure.Cell cell:group.cells)if(!cell.pos.equals(group.controller))ordered.add(cell);}
        return ordered;
    }
    /** Pure bounded ownership graph over a complete immutable snapshot; never captures or mutates. */
    static Set<BlockPos> activeCells(VehicleWorld view){
        VehicleStructure structure=view.structure;
        VehicleScan graph=new VehicleScan(view,structure.seat,true,Collections.emptySet());graph.membershipOnly=true;
        for(VehicleStructure.Cell cell:structure.cells)graph.cells.put(cell.pos,cell);
        for(VehicleStructure.Cell cell:structure.cells){
            TileEntity tile=view.getTileEntity(cell.pos);Block block=cell.state.getBlock();
            if(tile instanceof com.vandorlabs.gear.GearTileEntity){
                com.vandorlabs.gear.GearTileEntity root=((com.vandorlabs.gear.GearTileEntity)tile).loadedController();
                if(root!=null&&root.isLandingAssembly()&&root.mountDirection()==EnumFacing.DOWN)graph.supportCells(root.members(),root.getPos());
                else if(block instanceof com.vandorlabs.gear.track.TrackBlock)graph.supportCells(Collections.singleton(cell.pos),cell.pos);
            }else if(block instanceof BlockPropulsionLight&&cell.state.getValue(BlockPropulsionLight.FACING)==EnumFacing.DOWN){
                graph.supportCells(Collections.singleton(cell.pos),cell.pos);
            }else if(block instanceof BlockWheel){
                BlockWheel wheel=(BlockWheel)block;BlockPos owner=wheel.root(cell.pos,cell.state);graph.supportCells(wheel.members(owner,cell.state),owner);
            }else if(block instanceof BlockLandingGear){
                BlockPos owner=tile instanceof TileEntityLandingGear?((TileEntityLandingGear)tile).owner():null;
                graph.supportCells(Collections.singleton(cell.pos),owner==null?cell.pos:owner);
            }
            if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=((TileEntityCanopy)tile).constituent();if(canopy.draws())graph.geometryContacts(canopy.getPos(),canopy.boxes());}
            if(block instanceof BlockProgrammableWall&&((BlockProgrammableWall)block).isDiagonalShape()){
                List<net.minecraft.util.math.AxisAlignedBB> boxes=new ArrayList<>();cell.state.addCollisionBoxToList(view,cell.pos,new net.minecraft.util.math.AxisAlignedBB(cell.pos).grow(2),boxes,null,false);graph.geometryContacts(cell.pos,boxes);
            }
        }
        graph.roles();return graph.primaryPositions;
    }
    private static boolean ownedCoupler(IBlockState a,IBlockState b,EnumFacing direction){
        if(!(a.getBlock() instanceof com.vandorlabs.coupler.BlockCoupler)||!(b.getBlock() instanceof com.vandorlabs.coupler.BlockCoupler))return false;
        EnumFacing first=a.getValue(com.vandorlabs.coupler.BlockCoupler.FACING),second=b.getValue(com.vandorlabs.coupler.BlockCoupler.FACING);
        return first==direction&&second==direction.getOpposite()&&Math.abs(com.vandorlabs.coupler.BlockCoupler.couplingHeight(a)-com.vandorlabs.coupler.BlockCoupler.couplingHeight(b))<.001;
    }
    private void linked(BlockPos p, Block expected) {
        requiredMember(p);
        require(world.isBlockLoaded(p), "A component crosses an unloaded chunk.");
        require(world.getBlockState(p).getBlock()==expected, "Incomplete "+expected.getLocalizedName()+": expected a matching part at "+p.getX()+", "+p.getY()+", "+p.getZ()+" but found "+world.getBlockState(p).getBlock().getLocalizedName()+". Replace or complete this component.");
        enqueue(p);
    }
    /** A fragment cannot borrow a reserved owner or an inventory from the excluded hull. */
    private void attachmentComponents(BlockPos p,IBlockState state,TileEntity tile){
        if(!attachment)return;
        Block block=state.getBlock();
        if(block instanceof net.minecraft.block.BlockChest)for(EnumFacing side:EnumFacing.HORIZONTALS){BlockPos next=p.offset(side);require(world.isBlockLoaded(next),"Chest attachment touches an unloaded chunk");if(world.getBlockState(next).getBlock()==block)linked(next,block);}
        if(block instanceof net.minecraft.block.BlockBed)linked(p.offset(state.getValue(net.minecraft.block.BlockBed.PART)==net.minecraft.block.BlockBed.EnumPartType.FOOT?state.getValue(net.minecraft.block.BlockBed.FACING):state.getValue(net.minecraft.block.BlockBed.FACING).getOpposite()),block);
        if(block instanceof net.minecraft.block.BlockDoublePlant)linked(state.getValue(net.minecraft.block.BlockDoublePlant.HALF)==net.minecraft.block.BlockDoublePlant.EnumBlockHalf.UPPER?p.down():p.up(),block);
        if(block instanceof net.minecraft.block.BlockPistonBase&&state.getValue(net.minecraft.block.BlockPistonBase.EXTENDED))linked(p.offset(state.getValue(net.minecraft.block.BlockPistonBase.FACING)),net.minecraft.init.Blocks.PISTON_HEAD);
        if(block instanceof net.minecraft.block.BlockPistonExtension){BlockPos owner=p.offset(state.getValue(net.minecraft.block.BlockPistonExtension.FACING).getOpposite());requiredMember(owner);require(world.isBlockLoaded(owner)&&world.getBlockState(owner).getBlock() instanceof net.minecraft.block.BlockPistonBase,"Incomplete piston attachment");enqueueMember(owner);}
        if(state.getBlock() instanceof com.vandorlabs.programmer.ProgrammerBlock){
            com.vandorlabs.programmer.ProgrammerTile root=com.vandorlabs.programmer.ProgrammerBlock.at(world,p);
            require(root!=null,"Incomplete programmer in attached section");
            for(int part=0;part<4;part++){
                BlockPos member=com.vandorlabs.programmer.ProgrammerBlock.cell(root.getPos(),root.getProgrammerFacing(),part);requiredMember(member);
                require(world.isBlockLoaded(member),"Programmer attachment touches an unloaded chunk");IBlockState other=world.getBlockState(member);
                require(other.getBlock()==state.getBlock()&&other.getValue(com.vandorlabs.programmer.ProgrammerBlock.PART)==part&&other.getValue(com.vandorlabs.programmer.ProgrammerBlock.FACING)==root.getProgrammerFacing(),"Incomplete programmer in attached section");enqueueMember(member);
            }
        }
        if(tile instanceof TileEntityConstructorPart){
            BlockPos owner=((TileEntityConstructorPart)tile).owner();requiredMember(owner);require(world.isBlockLoaded(owner),"Core attachment touches an unloaded chunk");TileEntity raw=world.getTileEntity(owner);
            require(raw instanceof TileEntityConstructor&&((TileEntityConstructor)raw).formed(),"Orphaned Core reservation in attached section");enqueueMember(owner);
        }
        if(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).formed()){
            TileEntityConstructor root=(TileEntityConstructor)tile;BlockConstructorCore core=root.core();
            for(int x=0;x<core.width;x++)for(int y=0;y<core.height;y++)for(int z=0;z<core.depth;z++){
                BlockPos member=root.cell(x,y,z);requiredMember(member);require(world.isBlockLoaded(member),"Core attachment touches an unloaded chunk");
                if(!member.equals(p)){TileEntity raw=world.getTileEntity(member);require(raw instanceof TileEntityConstructorPart&&((TileEntityConstructorPart)raw).owner().equals(p),"Incomplete Core reservation in attached section");}
                enqueueMember(member);
            }
        }
    }
    private static boolean slab(Block block){return block instanceof net.minecraft.block.BlockSlab || block instanceof BlockProgrammableSlab || block instanceof BlockProgrammableLightSlab || block instanceof BlockRampControllerSlab || block instanceof com.vandorlabs.coupler.BlockCouplerSlab;}
    /** Empty half-block faces do not bridge the craft to the hangar floor. */
    public static boolean touches(World world,BlockPos p,IBlockState state,EnumFacing face){
        BlockPos other=p.offset(face);if(!world.isBlockLoaded(other))return true;
        IBlockState next=world.getBlockState(other);
        if(com.vandorlabs.coupler.BlockCoupler.separates(state,next))return false;
        if(next.getBlock() instanceof BlockControlledRamp)return false;
        if(next.getBlock().isAir(next,world,other))return true;
        // Only slabs have a guaranteed empty half-cell. Non-solid decorative components
        // retain the existing six-neighbour attachment contract.
        if(face.getAxis()==EnumFacing.Axis.Y){
            // Only a slab's own empty half disconnects it. Decorative walls and
            // canopy bounds may stop short of their cell while remaining attached.
            if(slab(state.getBlock())){
                net.minecraft.util.math.AxisAlignedBB a=state.getBoundingBox(world,p);
                if(face==EnumFacing.UP?a.maxY<1-1e-6:a.minY>1e-6)return false;
            }
            if(slab(next.getBlock())){
                net.minecraft.util.math.AxisAlignedBB b=next.getBoundingBox(world,other);
                if(face==EnumFacing.UP?b.minY>1e-6:b.maxY<1-1e-6)return false;
            }
        }
        return true;
    }
    /** Overhanging walls and shells can support blocks in diagonally adjacent cells. */
    private void geometryContacts(BlockPos owner,List<net.minecraft.util.math.AxisAlignedBB> surfaces){
        if(!overhangContacts.add(owner))return;
        Map<BlockPos,List<net.minecraft.util.math.AxisAlignedBB>> shapes=new HashMap<>();
        for(net.minecraft.util.math.AxisAlignedBB box:surfaces){
            net.minecraft.util.math.AxisAlignedBB contact=box.grow(1e-6);
            require(Double.isFinite(contact.minX)&&Double.isFinite(contact.maxX)&&Double.isFinite(contact.minY)&&Double.isFinite(contact.maxY)&&Double.isFinite(contact.minZ)&&Double.isFinite(contact.maxZ)&&contact.maxX-contact.minX<=64&&contact.maxY-contact.minY<=64&&contact.maxZ-contact.minZ<=64,"Invalid attachment geometry.");
            for(BlockPos cursor:BlockPos.getAllInBox(new BlockPos(contact.minX,contact.minY,contact.minZ),new BlockPos(contact.maxX,contact.maxY,contact.maxZ))){
                require(++contactChecks<=maxBlocks*128,"Attachment discovery limit exceeded.");
                if(excluded.contains(cursor)||scheduled.contains(cursor))continue;
                require(world.isBlockLoaded(cursor),"Overhanging geometry touches an unloaded chunk.");
                IBlockState state=world.getBlockState(cursor);if(state.getBlock().isAir(state,world,cursor))continue;
                BlockPos at=cursor.toImmutable();List<net.minecraft.util.math.AxisAlignedBB> boxes=shapes.get(at);
                if(boxes==null){boxes=new ArrayList<>();state.addCollisionBoxToList(world,at,new net.minecraft.util.math.AxisAlignedBB(at).grow(2),boxes,null,false);shapes.put(at,boxes);}
                for(net.minecraft.util.math.AxisAlignedBB shape:boxes)if(surfaceContact(box,shape)){contactLinks.computeIfAbsent(owner,k->new HashSet<>()).add(at);contactLinks.computeIfAbsent(at,k->new HashSet<>()).add(owner);enqueue(at);break;}
            }
        }
    }
    private static boolean surfaceContact(net.minecraft.util.math.AxisAlignedBB a,net.minecraft.util.math.AxisAlignedBB b){
        double x=Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX),y=Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY),z=Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ);
        return x>=-1e-6&&y>=-1e-6&&z>=-1e-6&&(x>1e-7?1:0)+(y>1e-7?1:0)+(z>1e-7?1:0)>=2;
    }
    /** Search only beneath a foot, within a small loaded connected attachment graph.
     * Nothing is enqueued unless another complete lower landing assembly is found. */
    private List<BlockPos> nestedLandingPath(com.vandorlabs.gear.GearTileEntity upper,BlockPos foot){
        BlockPos first=foot.down();
        ArrayDeque<BlockPos> pending=new ArrayDeque<>();Map<BlockPos,BlockPos> previous=new HashMap<>();
        pending.add(first);previous.put(first,foot);
        while(!pending.isEmpty()&&previous.size()<=256){
            BlockPos p=pending.removeFirst();if(excluded.contains(p)||!world.isBlockLoaded(p))continue;
            IBlockState state=world.getBlockState(p);Block block=state.getBlock();
            if(block.isAir(state,world,p)||state.getMaterial().isLiquid()||state.getBlockHardness(world,p)<0)continue;
            TileEntity raw=world.getTileEntity(p);
            if(raw instanceof com.vandorlabs.gear.GearTileEntity){
                com.vandorlabs.gear.GearTileEntity lower=((com.vandorlabs.gear.GearTileEntity)raw).loadedController();
                boolean terminal=raw instanceof com.vandorlabs.gear.track.TrackTile&&lower==null;
                if(terminal||lower!=null&&lower!=upper&&lower.isOperational()&&lower.isLandingAssembly()&&lower.mountDirection()==EnumFacing.DOWN&&lower.landingDeployed()){
                    List<BlockPos> path=new ArrayList<>();for(BlockPos at=p;!at.equals(foot);at=previous.get(at))path.add(at);return path;
                }
                if(lower==upper)continue;
            }
            for(EnumFacing side:EnumFacing.values()){
                if(side==EnumFacing.UP)continue;
                BlockPos next=p.offset(side);
                if(next.getY()<foot.getY()-16||Math.abs(next.getX()-foot.getX())>8||Math.abs(next.getZ()-foot.getZ())>8||previous.containsKey(next)||excluded.contains(next))continue;
                if(touches(world,p,state,side)){previous.put(next,p);pending.add(next);}
            }
        }
        return Collections.emptyList();
    }
    public boolean step(int budget) {
        if(error!=null || result!=null) return true;
        try {
            for(int i=0;i<budget && !queue.isEmpty();i++) {
                BlockPos p=queue.removeFirst(); if(!visited.add(p))continue;
                require(visited.size()<=maxBlocks*7, "Discovery limit exceeded; separate the craft from its surroundings.");
                require(world.isBlockLoaded(p), "The craft touches an unloaded chunk.");
                IBlockState state=world.getBlockState(p); Block block=state.getBlock();
                if(block instanceof BlockControlledRamp && !rampSections.contains(p))continue;
                if(block.isAir(state,world,p)){airBoundary.add(p);continue;}
                require(!state.getMaterial().isLiquid() && block!=net.minecraft.init.Blocks.BEDROCK
                        && (state.getBlockHardness(world,p)>=0 || block instanceof BlockControlledRamp), "Unsupported block: "+block.getLocalizedName());
                TileEntity tile=world.getTileEntity(p);
                attachmentComponents(p,state,tile);
                for(BlockPos required:VehicleForeignCompat.required(world,p))enqueueMember(required);
                if(tile instanceof TileEntityTurntableController)require(((TileEntityTurntableController)tile).captureId==null&&((TileEntityTurntableController)tile).layout==null,"Restore the turntable contents and detach its controller before vehicle assembly.");
                if(tile instanceof TileEntityTurntableSurface)require(((TileEntityTurntableSurface)tile).owner==null,"Detach the turntable surface before vehicle assembly.");
                require(block.getRegistryName()!=null, "Unregistered block at "+p);
                require(!block.hasTileEntity(state) || tile!=null, "Missing component data at "+p);
                require(cells.size()<maxBlocks, "Craft exceeds the configured "+maxBlocks+" block limit; check for connections to the ground.");
                minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ());
                maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ());
                require(maxX-minX<64 && maxY-minY<64 && maxZ-minZ<64, "Craft exceeds 64 blocks along an axis.");
                NBTTagCompound data=tile==null?null:tile.writeToNBT(new NBTTagCompound());
                if(VehicleEscalators.active(tile))data=VehicleEscalators.paused(data);
                if(tile instanceof TileEntityControlledRamp) {
                    TileEntity owner=world.getTileEntity(((TileEntityControlledRamp)tile).controller);
                    require(owner instanceof TileEntityRampController && ((TileEntityRampController)owner).owns((TileEntityControlledRamp)tile),"Ramp ownership changed; inspect again.");
                    data=VehicleEscalators.paused(data);data.setDouble("EscalatorDistance",((TileEntityRampController)owner).beltDistance(0));
                }
                cells.put(p,new VehicleStructure.Cell(p,state,data));
                if(tile instanceof TileEntityCockpit){
                    TileEntityCockpit cockpit=(TileEntityCockpit)tile;
                    for(BlockPos member:cockpit.data().cells(p,cockpit.facing()))if(!member.equals(p)){
                        require(world.isBlockLoaded(member),"Cockpit touches an unloaded chunk.");TileEntity part=world.getTileEntity(member);
                        require(part instanceof TileEntityCockpitPart && com.vandorlabs.cockpit.CockpitAssembly.root(world,member)==cockpit,"Incomplete cockpit reservation.");enqueueMember(member);
                    }
                }else if(tile instanceof TileEntityCockpitPart){TileEntityCockpit cockpit=com.vandorlabs.cockpit.CockpitAssembly.root(world,p);require(cockpit!=null,"Orphaned cockpit reservation.");enqueueMember(cockpit.getPos());}
                if(block instanceof BlockPilotSeat) {
                    boolean upper=state.getValue(BlockPilotSeat.UPPER);
                    if(!upper){seats++;if(pilot==null || p.equals(start))pilot=p;}
                    if(block instanceof BlockCockpit){}
                    else if(!upper && BlockPilotSeat.canopyAbove(world,p))enqueue(p.up());
                    else linked(upper?p.down():p.up(),block);
                } else if(block instanceof BlockBridgeChair || block instanceof BlockConnectedSeat) {
                    linked(state.getValue(BlockBridgeChair.UPPER)?p.down():p.up(),block);
                } else if(block instanceof BlockLargeProgrammableDoor) {
                    TileEntityLargeProgrammableDoor root=((BlockLargeProgrammableDoor)block).root(world,p);
                    require(root!=null && ((BlockLargeProgrammableDoor)block).complete(world,root), "Incomplete large door.");
                    for(int x=0;x<3;x++)for(int y=0;y<3;y++)linked(root.getPos().offset(state.getValue(BlockVandorDoor.FACING).rotateYCCW(),x).up(y),block);
                } else if(block instanceof BlockVandorDoor || block instanceof BlockDoor) {
                    linked(state.getValue(BlockVandorDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER?p.down():p.up(),block);
                }
                if(tile instanceof TileEntityRampController) {
                    TileEntityRampController ramp=(TileEntityRampController)tile;
                    require(!ramp.isMoving(),"Wait for the ramp to stop moving before assembly.");
                    {
                        for(BlockPos source:ramp.vehicleSources()){
                            requiredMember(source);
                            require(world.isBlockLoaded(source),"Load all ramp source chunks before assembly.");
                            TileEntity part=world.getTileEntity(source);require(world.isAirBlock(source) || part instanceof TileEntityControlledRamp && ramp.owns((TileEntityControlledRamp)part),"Ramp source changed; inspect again.");
                        }
                        for(BlockPos section:ramp.vehicleCells()) {
                            requiredMember(section);
                            require(world.isBlockLoaded(section),"Load all ramp section chunks before assembly.");
                            TileEntity part=world.getTileEntity(section);
                            require(part instanceof TileEntityControlledRamp && ramp.owns((TileEntityControlledRamp)part),"Ramp section changed; inspect again.");
                            rampSections.add(section);airBoundary.remove(section);visited.remove(section);scheduled.add(section);queue.add(section);
                        }
                    }
                }
                if(tile instanceof TileEntityCanopy) {
                    TileEntityCanopy canopy=((TileEntityCanopy)tile).constituent();
                    require(Math.abs(canopy.progress-(canopy.targetOpen?1:0))<.001, "Wait for the canopy to stop moving.");
                    for(BlockPos member:canopy.reservedCells())linked(member,block);
                    if(canopy.pair!=null)linked(canopy.pair,block);
                    TileEntityCanopy owner=canopy.owner();if(owner.draws())geometryContacts(owner.getPos(),owner.boxes());
                }
                if(block instanceof BlockProgrammableWall&&((BlockProgrammableWall)block).isDiagonalShape()){
                    List<net.minecraft.util.math.AxisAlignedBB> surfaces=new ArrayList<>();state.addCollisionBoxToList(world,p,new net.minecraft.util.math.AxisAlignedBB(p).grow(2),surfaces,null,false);geometryContacts(p,surfaces);
                }
                if(tile instanceof TileEntityProgrammableTrapdoor && data!=null) {
                    for(String key:new String[]{"TrapdoorPartner","TrapdoorSquare"})
                        if(data.hasKey(key,4))linked(BlockPos.fromLong(data.getLong(key)),block);
                    net.minecraft.nbt.NBTTagList assembly=data.getTagList("TrapdoorAssembly",4);
                    for(int n=0;n<assembly.tagCount();n++)linked(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong)assembly.get(n)).getLong()),block);
                }
                if(tile instanceof com.vandorlabs.gear.track.TrackTile&&((com.vandorlabs.gear.track.TrackTile)tile).loadedController()==null){
                    com.vandorlabs.gear.track.TrackTile track=(com.vandorlabs.gear.track.TrackTile)tile;
                    BlockPos leader=track.leader()==null?p:track.leader();
                    trackSupports.add(p);if(gears.add(p))support((BlockLandingGear)block,p);
                    trackBottoms.put(p,((BlockLandingGear)block).vehicleBottomY(world,p));supportCells(Collections.singleton(p),p);upperConnection(p);
                    if(world.isBlockLoaded(leader)&&world.getTileEntity(leader) instanceof com.vandorlabs.gear.track.TrackTile)
                        for(BlockPos member:((com.vandorlabs.gear.track.TrackTile)world.getTileEntity(leader)).trackMembers())linked(member,block);
                    // A side-mounted track may be connected only to a slab beside its bracket.
                    for(EnumFacing side:EnumFacing.HORIZONTALS){BlockPos next=p.offset(side);if(world.isBlockLoaded(next)&&slab(world.getBlockState(next).getBlock())&&touches(world,p,state,side))enqueue(next);}
                    continue;
                }
                if(block instanceof com.vandorlabs.gear.GearBlock){
                    com.vandorlabs.gear.GearTileEntity gear=tile instanceof com.vandorlabs.gear.GearTileEntity?((com.vandorlabs.gear.GearTileEntity)tile).loadedController():null;
                    require(gear!=null&&gear.isOperational(),"Complete the connected arm assembly before vehicle capture.");
                    if(!gearAssemblies.add(gear.getPos()))continue;
                    boolean landing=gear.isLandingAssembly()&&gear.mountDirection()==EnumFacing.DOWN;
                    if(landing)supportCells(gear.members(),gear.getPos());
                    require(!landing||gear.landingDeployed(),"Deploy the modular landing gear before assembly.");
                    if(landing)for(BlockPos column:gear.columns()){
                        BlockPos at=gear.getPos().add(column),foot=at.down(gear.assembly().columnHeight());
                        List<BlockPos> lower=nestedLandingPath(gear,foot);
                        if(gears.add(at))support((BlockLandingGear)world.getBlockState(at).getBlock(),at);
                        for(BlockPos next:lower)enqueue(next);
                    }
                    if(landing)for(BlockPos member:gear.members())if(world.isBlockLoaded(member)&&world.getBlockState(member).getBlock() instanceof com.vandorlabs.gear.track.TrackBlock){trackSupports.add(member);trackBottoms.put(member,((BlockLandingGear)world.getBlockState(member).getBlock()).vehicleBottomY(world,member));}
                    for(BlockPos member:gear.members()){
                        require(world.isBlockLoaded(member)&&world.getBlockState(member).getBlock() instanceof com.vandorlabs.gear.GearBlock,"Incomplete modular arm.");enqueueMember(member);
                        if(landing)upperConnection(member);
                        for(EnumFacing side:EnumFacing.values()){
                            BlockPos at=member.offset(side);if(!world.isBlockLoaded(at))continue;
                            TileEntity cover=world.getTileEntity(at);
                            if(!landing&&touches(world,member,world.getBlockState(member),side)
                                    ||cover instanceof TileEntityProgrammableTrapdoor&&((TileEntityProgrammableTrapdoor)cover).isCover()&&at.offset(world.getBlockState(at).getValue(BlockProgrammableTrapdoor.FACING)).equals(member))enqueue(at);
                        }
                    }
                    continue;
                }
                if(block instanceof BlockPropulsionLight && state.getValue(BlockPropulsionLight.FACING)==EnumFacing.DOWN){gears.add(p);supportBottoms.put(p,(double)p.getY());gearBottomY=gearBottomY==null?(double)p.getY():Math.min(gearBottomY,p.getY());supportCells(Collections.singleton(p),p);upperConnection(p);continue;}
                if(block instanceof BlockWheel){
                    BlockWheel wheel=(BlockWheel)block;BlockPos root=wheel.root(p,state);
                    require(world.isBlockLoaded(root)&&world.getBlockState(root).getBlock()==block&&world.getBlockState(root).getValue(BlockWheel.PART)==0,"Incomplete wheel.");
                    if(gears.add(root)){support(wheel,root);List<BlockPos> members=wheel.members(root,world.getBlockState(root));
                        supportCells(members,root);for(BlockPos member:members)upperConnection(member);
                        for(int n=0;n<members.size();n++){BlockPos member=members.get(n);linked(member,block);
                            require(world.getBlockState(member).getValue(BlockWheel.PART)==n&&world.getBlockState(member).getValue(BlockWheel.FACING)==state.getValue(BlockWheel.FACING),"Incomplete wheel ownership.");}}
                    continue;
                }
                if(block instanceof BlockNonWheeledLandingGear){
                    // Follow runner/pad connections, while ordinary terrain still terminates here.
                    for(EnumFacing side:EnumFacing.values()){
                        BlockPos next=p.offset(side);if(world.isBlockLoaded(next) && world.getBlockState(next).getBlock() instanceof BlockNonWheeledLandingGear)enqueue(next);
                    }
                }
                if(block instanceof BlockTelescopicLandingGear) {
                    BlockPos control=block instanceof BlockNonWheeledLandingGear?((BlockNonWheeledLandingGear)block).connectedRoot(world,p):p;
                    TileEntityLandingGear root=((BlockTelescopicLandingGear)block).root(world,control);
                    require(root!=null, "Orphaned landing gear.");
                    if(gears.add(root.getPos())) {
                        boolean extended=world.getBlockState(root.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED);
                        float target=extended?root.getExtensionPixels()/16F:0;
                        require(Math.abs(root.progress-target)<.001, "Wait for landing gear to stop moving.");
                        support((BlockLandingGear)block,root.getPos());
                        linked(root.getPos(),block);
                        int size=root.getSize(), radius=size>=3?1:0, count=(int)Math.ceil(target)+(size>=3?1:0);
                        for(int y=0;y<=count;y++)for(int x=size==4?0:-radius;x<=radius;x++)for(int z=size==4?0:-radius;z<=radius;z++) {
                            BlockPos member=root.getPos().add(x,-y,z); linked(member,block);
                            supportCells(Collections.singleton(member),root.getPos());upperConnection(member);
                            TileEntity raw=world.getTileEntity(member);
                            require(member.equals(root.getPos()) || raw instanceof TileEntityLandingGear && root.getPos().equals(((TileEntityLandingGear)raw).owner()), "Invalid landing gear ownership.");
                            for(EnumFacing side:EnumFacing.HORIZONTALS) {
                                BlockPos coverPos=member.offset(side);
                                if(!world.isBlockLoaded(coverPos))continue;
                                TileEntity cover=world.getTileEntity(coverPos);
                                if(cover instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)cover).isCover()
                                        && coverPos.offset(world.getBlockState(coverPos).getValue(BlockProgrammableTrapdoor.FACING)).equals(member))enqueue(coverPos);
                            }
                        }
                    }
                    continue;
                }
                if(block instanceof BlockLandingGear) { if(gears.add(p))support((BlockLandingGear)block,p);supportCells(Collections.singleton(p),p);upperConnection(p);continue; }
                if(tile instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)tile).isCover()) {
                    BlockPos gear=p.offset(state.getValue(BlockProgrammableTrapdoor.FACING));
                    require(world.isBlockLoaded(gear) && (world.getBlockState(gear).getBlock() instanceof BlockTelescopicLandingGear||world.getBlockState(gear).getBlock() instanceof com.vandorlabs.gear.GearBlock),"A landing gear cover has no gear owner.");
                    enqueue(gear);continue;
                }
                if(!(block instanceof BlockControlledRamp))for(EnumFacing face:EnumFacing.values())if(touches(world,p,state,face))enqueue(p.offset(face));
            }
            if(queue.isEmpty()&&expandHigherSupports())return false;
            if(queue.isEmpty()) {
                require(!cells.isEmpty(),"The opposing coupler section contains no blocks.");
                List<VehicleStructure.Cell> ordered=roles();
                if(!attachment){require(seats>=1, "A craft must contain at least one Pilot Seat.");
                    require(hasRequiredSupports(), "A craft needs at least three landing gear or wheel supports, or two track blocks.");}
                origin=new BlockPos(minX,minY,minZ);
                List<VehicleStructure.Cell> local=new ArrayList<>();
                for(VehicleStructure.Cell cell:ordered)local.add(new VehicleStructure.Cell(cell.pos.subtract(origin),cell.state,
                        cell.tileData()==null?null:VehicleTileData.translated(cell.tileData(),BlockPos.ORIGIN.subtract(origin))));
                if(attachment)result=VehicleStructure.display(local);
                else{result=new VehicleStructure(local,pilot.subtract(origin),world.getBlockState(pilot).getValue(BlockPilotSeat.FACING),supportCount());
                    result=VehicleControlSettings.migratePlaced(world,origin,result);}
                VehicleWorld view=new VehicleWorld(world,result);view.origin=origin;
                MovingBlockSupport.validate(result,view);
                result.encode(); // Enforce the byte limit before a preview or mutation.
            }
        } catch(Exception e) { result=null;error=e.getMessage()==null?"Cannot capture this structure.":e.getMessage(); }
        return error!=null || result!=null;
    }
}
