package com.vandorlabs.neoforge.gear;

import com.vandorlabs.gear.core.GearTopology;
import com.vandorlabs.neoforge.transfer.NativeRawCell;
import com.vandorlabs.redstone.ChannelList;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Pure after-image preparation for a separately authorized legacy maintenance transaction. No installation or journal authority. */
public final class NativeGearLegacyDestination {
    public record Column(BlockPos rootPos,Direction facing,boolean large,int height,boolean deployed,int trigger,ChannelList channels,UUID identity){
        public Column {rootPos=Objects.requireNonNull(rootPos).immutable();Objects.requireNonNull(facing);Objects.requireNonNull(channels);Objects.requireNonNull(identity);if(!facing.getAxis().isHorizontal()||height<1||height>4||trigger<0||trigger>2||identity.equals(new UUID(0,0)))throw new IllegalArgumentException("Legacy destination column bounds");}
    }
    private record Seed(BlockState state,CompoundTag raw,UUID identity){}
    private static final Direction[] SIDES={Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static final int TILE_LIMIT=1024*1024;
    private static final class Budget {long bytes;void admit(CompoundTag raw){int size=raw.sizeInBytes();require(size<=TILE_LIMIT,"Legacy destination individual tile-byte bound");bytes=Math.addExact(bytes,size);require(bytes<=8*1024*1024,"Legacy destination total tile-byte bound");}}
    private static void require(boolean yes,String why){if(!yes)throw new IllegalArgumentException(why);}
    private static void loaded(ServerLevel world,BlockPos pos){require(NativeGearFormation.loaded(world,pos),"Load the complete legacy destination and boundary");}
    private static UUID identity(UUID column,BlockPos relative){return UUID.nameUUIDFromBytes(("legacy-landing/"+column+"/"+relative.getX()+"/"+relative.getY()+"/"+relative.getZ()).getBytes(StandardCharsets.UTF_8));}
    private static CompoundTag fresh(HolderLookup.Provider registries,BlockPos pos,BlockState state){var tile=((NativeGearBlock)state.getBlock()).newBlockEntity(pos,state);require(tile instanceof NativeGearEntity,"Native landing destination factory");return tile.saveWithFullMetadata(registries);}
    private static void add(HolderLookup.Provider registries,Map<BlockPos,Seed> cells,Budget budget,BlockPos pos,BlockState state,UUID id){require(!cells.containsKey(pos),"Legacy destination columns overlap");var raw=fresh(registries,pos,state);budget.admit(raw);cells.put(pos,new Seed(state,raw,id));}
    private static void neighbor(ServerLevel world,Column input,NativeGearEntity root){
        require(root!=null&&!root.inert()&&root.controller()&&root.definition()==(input.large?NativeGearDefinition.LARGE_EXTENDER:NativeGearDefinition.EXTENDER)&&root.mount()==Direction.DOWN,"Adjacent gear is not a compatible native legacy Extender");
        var settings=root.settings();require(settings.legacyAnyChannels()&&settings.trigger()==input.trigger&&settings.channels().equals(input.channels)&&settings.footFacing()==input.facing.get2DDataValue(),"Adjacent migrated gear input or facing differs");
        require(root.assembly()!=null&&root.assembly().kind()==GearTopology.FootKind.WHEEL&&root.assembly().columnHeight()==input.height&&root.progress()==(input.deployed?1:0)&&root.target()==input.deployed,"Adjacent migrated gear endpoint differs");
        require(root.payload()==null&&!root.hasStoredSections()&&!root.ownsCaptured()&&!NativeGearJournal.busy(root)&&root.completeMembers(),"Adjacent migrated gear retains transfer ownership or incomplete members");
        for(var member:root.members()){loaded(world,member);require(!com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,member),"Adjacent gear is reserved by another transfer");}
    }
    public static List<NativeRawCell> prepare(ServerLevel world,List<Column> inputs){
        require(world.getServer().isSameThread(),"Legacy destination preparation requires server thread");require(NativeGearBindings.enabled()&&!inputs.isEmpty()&&inputs.size()<=64,"Legacy destination column count");
        var input=initial(world.registryAccess(),inputs);var first=input.first;var seeds=input.seeds;var columns=input.columns;var feet=input.feet;var budget=input.budget;
        for(var pos:seeds.keySet())loaded(world,pos);
        var existing=new LinkedHashMap<UUID,NativeGearEntity>();Integer direction=null;
        // Each admitted native neighbor already owns a complete connected group. Discover from all new boundary feet.
        for(var pos:List.copyOf(feet))for(var side:SIDES){var next=pos.relative(side);if(seeds.containsKey(next))continue;loaded(world,next);var tile=NativeGearFormation.at(world,next);if(tile==null)continue;var root=tile.controllerEntity();neighbor(world,first,root);var seen=existing.putIfAbsent(root.identity(),root);if(seen!=null){require(seen==root,"Duplicate neighboring root identity");continue;}
            if(direction==null)direction=root.settings().direction();else require(direction==root.settings().direction(),"Adjacent migrated gear fold directions differ");
            for(var column:root.columns())columns.add(root.getBlockPos().offset(column));
            for(var foot:root.assembly().feet())feet.add(root.getBlockPos().offset(foot.x,foot.y,foot.z));
            for(var member:root.members()){require(!seeds.containsKey(member),"New column overlaps an existing native owner");var current=NativeGearFormation.at(world,member);require(current!=null&&!current.inert(),"Adjacent gear member changed");var raw=current.saveWithFullMetadata(world.registryAccess());budget.admit(raw);seeds.put(member,new Seed(current.getBlockState(),raw,current.identity()));}
        }
        var result=build(first,seeds,columns,feet,direction);
        var before=new ArrayList<NativeRawCell>();for(var cell:result){var tile=world.getBlockEntity(cell.pos);before.add(new NativeRawCell(cell.pos,world.getBlockState(cell.pos),tile==null?null:tile.saveWithFullMetadata(world.registryAccess()),8*1024*1024));}
        validate(world.registryAccess(),inputs,before,result);
        for(var cell:result)cell.stage(world,tile->tile instanceof NativeGearEntity gear&&!gear.inert(),NativeGearLegacyDestination::same);
        return result;
    }
    private record Initial(Column first,TreeMap<BlockPos,Seed> seeds,TreeSet<BlockPos> columns,TreeSet<BlockPos> feet,Budget budget){}
    private static Initial initial(HolderLookup.Provider registries,List<Column> inputs){
        require(NativeGearBindings.enabled()&&!inputs.isEmpty()&&inputs.size()<=64,"Legacy destination column count");
        var budget=new Budget();var first=inputs.getFirst();var sourceIds=new HashSet<UUID>();var seeds=new TreeMap<BlockPos,Seed>();var columns=new TreeSet<BlockPos>();var feet=new TreeSet<BlockPos>();
        for(var input:inputs){
            require(input.facing==first.facing&&input.large==first.large&&input.height==first.height&&input.deployed==first.deployed&&input.trigger==first.trigger&&input.channels.equals(first.channels)&&sourceIds.add(input.identity),"Legacy columns do not share one compatible endpoint/input");require(columns.add(input.rootPos),"Duplicate legacy column");
            var arm=NativeGearBindings.BLOCKS.get((input.large?NativeGearDefinition.LARGE_EXTENDER:NativeGearDefinition.EXTENDER).id);var wheel=NativeGearBindings.BLOCKS.get((input.large?NativeGearDefinition.LARGE_WHEEL:NativeGearDefinition.WHEEL).id);require(arm!=null&&wheel!=null,"Native modular destination unavailable");
            for(int depth=0;depth<input.height;depth++){var pos=input.rootPos.below(depth);add(registries,seeds,budget,pos,arm.defaultBlockState().setValue(NativeGearBlock.FACING,input.facing).setValue(NativeGearBlock.ASSEMBLED,true),depth==0?input.identity:identity(input.identity,pos.subtract(input.rootPos)));}
            var root=input.rootPos.below(input.height);for(int part=0;part<(input.large?4:1);part++){var pos=root.offset(NativeGearComponents.offset(Direction.DOWN,input.facing,part&1,part>>1));var state=wheel.defaultBlockState().setValue(NativeGearBlock.FACING,input.facing).setValue(NativeGearBlock.ASSEMBLED,true).setValue(NativeGearBlock.PART,part);add(registries,seeds,budget,pos,state,identity(input.identity,pos.subtract(input.rootPos)));feet.add(pos);}
        }
        return new Initial(first,seeds,columns,feet,budget);
    }
    private static List<NativeRawCell> build(Column first,TreeMap<BlockPos,Seed> seeds,TreeSet<BlockPos> columns,TreeSet<BlockPos> feet,Integer direction){
        require(seeds.size()<=512,"Legacy destination cell bound");var primary=columns.getFirst();var relativeFeet=feet.stream().map(pos->new GearTopology.Cell(pos.getX()-primary.getX(),pos.getY()-primary.getY(),pos.getZ()-primary.getZ())).toList();var topology=GearTopology.validate(first.height,GearTopology.FootKind.WHEEL,relativeFeet);require(topology.isValid(),topology.error());
        var choices=NativeGearSettings.defaults().editableChoices();choices.putInt("Trigger",first.trigger);choices.putString("Channels",first.channels.toString());choices.putBoolean("LegacyAnyChannels",true);choices.putInt("Direction",direction==null?0:direction);choices.putInt("FootFacing",first.facing.get2DDataValue());var settings=NativeGearSettings.readChoices(choices);
        var encodedFeet=new ListTag();for(var foot:topology.assembly().feet())encodedFeet.add(new IntArrayTag(new int[]{foot.x,foot.y,foot.z}));var encodedColumns=new ListTag();for(var column:columns){var p=column.subtract(primary);encodedColumns.add(new IntArrayTag(new int[]{p.getX(),p.getY(),p.getZ()}));}
        var wheelRoots=new ListTag();if(first.large)for(var pos:feet)if(seeds.get(pos).state.getValue(NativeGearBlock.PART)==0){var p=pos.subtract(primary);wheelRoots.add(new IntArrayTag(new int[]{p.getX(),p.getY(),p.getZ()}));}
        var result=new ArrayList<NativeRawCell>();var identities=new HashSet<UUID>();long retained=0;
        for(var entry:seeds.entrySet()){
            var pos=entry.getKey();var seed=entry.getValue();require(identities.add(seed.identity),"Duplicate destination tile identity");var raw=seed.raw.copy();retained=Math.addExact(retained,raw.sizeInBytes());require(retained<=8*1024*1024,"Legacy destination total tile-byte bound");settings.writeSaved(raw);raw.putInt("NativeGearSchema",1);raw.putUUID("NativeGearIdentity",seed.identity);raw.putString("ArmMountDirection",Direction.DOWN.name());raw.putDouble("GearProgress",first.deployed?1:0);raw.putDouble("NativeGearLastEndpoint",first.deployed?1:0);raw.putBoolean("GearTargetDeployed",first.deployed);raw.putBoolean("GearBlocked",false);raw.putString("GearError","");raw.putBoolean("GearSignalKnown",false);raw.putBoolean("GearLastSignal",false);raw.putInt("GearRevision",Math.addExact(raw.getInt("GearRevision"),1));raw.putBoolean("GearConfigured",true);raw.putInt("GearFootFacing",settings.footFacing());raw.putLong("GearOwnerOffset",com.vandorlabs.persistence.LegacyBlockPosition.pack(primary.getX()-pos.getX(),primary.getY()-pos.getY(),primary.getZ()-pos.getZ()));raw.putBoolean("GearController",pos.equals(primary));raw.putBoolean("GearOperational",pos.equals(primary));
            for(var key:List.of("GearHeight","GearKind","GearFeet","GearWheelRoots","NativeTrackFootView","ArmRetractingStroke"))raw.remove(key);
            if(pos.equals(primary)){raw.putInt("GearHeight",first.height);raw.putString("GearKind",GearTopology.FootKind.WHEEL.name());raw.put("GearFeet",encodedFeet.copy());raw.put("GearColumns",encodedColumns.copy());if(first.large)raw.put("GearWheelRoots",wheelRoots.copy());}
            else{var singleton=new ListTag();singleton.add(new IntArrayTag(new int[]{0,0,0}));raw.put("GearColumns",singleton);}
            var cell=new NativeRawCell(pos,seed.state,raw,TILE_LIMIT);result.add(cell);
        }
        return List.copyOf(result);
    }
    /** Only vanilla/NeoForge host metadata is optional; every Gear/opaque own field stays exact. */
    public static boolean same(CompoundTag before,CompoundTag after){if(Objects.equals(before,after))return true;if(before==null||after==null)return false;var a=before.copy();var b=after.copy();for(var tag:List.of(a,b)){if(tag.contains("keepPacked",Tag.TAG_BYTE)&&tag.getByte("keepPacked")==0)tag.remove("keepPacked");if(tag.contains("NeoForgeData",Tag.TAG_COMPOUND)&&tag.getCompound("NeoForgeData").isEmpty())tag.remove("NeoForgeData");}return a.equals(b);}
    private static boolean bit(CompoundTag raw,String key){require(raw.contains(key,Tag.TAG_BYTE)&&(raw.getByte(key)==0||raw.getByte(key)==1),"Gear boolean field "+key);return raw.getBoolean(key);}
    private static List<BlockPos> vectors(CompoundTag raw,String key,int maximum){require(raw.get(key) instanceof ListTag,"Gear vector field "+key);var list=(ListTag)raw.get(key);require(list.size()>0&&list.size()<=maximum&&list.getElementType()==Tag.TAG_INT_ARRAY,"Gear vector bounds "+key);var result=new LinkedHashSet<BlockPos>();for(var item:list){int[] v=((IntArrayTag)item).getAsIntArray();require(v.length==3&&Math.abs((long)v[0])<=64&&Math.abs((long)v[1])<=64&&Math.abs((long)v[2])<=64,"Gear vector coordinate bounds");require(result.add(new BlockPos(v[0],v[1],v[2])),"Duplicate Gear vector");}return List.copyOf(result);}
    private static BlockPos owner(BlockPos pos,CompoundTag raw){require(raw.contains("GearOwnerOffset",Tag.TAG_LONG),"Missing Gear owner offset");long v=raw.getLong("GearOwnerOffset");return pos.offset(com.vandorlabs.persistence.LegacyBlockPosition.x(v),com.vandorlabs.persistence.LegacyBlockPosition.y(v),com.vandorlabs.persistence.LegacyBlockPosition.z(v));}
    private static Map<BlockPos,NativeRawCell> indexed(List<NativeRawCell> cells){require(!cells.isEmpty()&&cells.size()<=1024,"Gear record cell bounds");var result=new LinkedHashMap<BlockPos,NativeRawCell>();for(var cell:cells)require(result.putIfAbsent(cell.pos,cell)==null,"Duplicate Gear record position");return result;}
    private static CompoundTag nativeBefore(NativeRawCell cell){require(cell.hasTile()&&cell.state.getBlock() instanceof NativeGearBlock,"Missing native Gear preimage");var raw=cell.tile();require(raw.contains("NativeGearSchema",Tag.TAG_INT)&&raw.getInt("NativeGearSchema")==1&&raw.hasUUID("NativeGearIdentity")&&!raw.getUUID("NativeGearIdentity").equals(new UUID(0,0))&&!raw.getBoolean("NativeGearInert"),"Unsupported native Gear preimage identity/schema");require(raw.contains("x",Tag.TAG_INT)&&raw.contains("y",Tag.TAG_INT)&&raw.contains("z",Tag.TAG_INT)&&raw.getInt("x")==cell.pos.getX()&&raw.getInt("y")==cell.pos.getY()&&raw.getInt("z")==cell.pos.getZ()&&raw.getString("id").equals("vandorlabs:modular_gear"),"Native Gear preimage factory/position");for(var key:List.of("NativeGearPayload","NativeGearStoredSections","NativeGearPublicView","ArmPayload","ArmStoredSections","GearParkingOriginal","GearTaxi","GearTaxiState","TaxiArm"))require(!raw.contains(key),"Native Gear preimage retains physical ownership");return raw;}
    /**
     * World-free Gear portion of a durable legacy conversion recipe. Returns every validated Gear target.
     * The family must separately prove all other changed cells are its exact Legacy-to-AIR retirement.
     */
    public static Set<BlockPos> validate(HolderLookup.Provider registries,Column input,List<NativeRawCell> before,List<NativeRawCell> after){return validate(registries,List.of(input),before,after);}
    public static Set<BlockPos> validate(HolderLookup.Provider registries,List<Column> inputs,List<NativeRawCell> before,List<NativeRawCell> after){
        var old=indexed(before);var next=indexed(after);require(old.keySet().equals(next.keySet()),"Gear recipe before/after coverage differs");var plan=initial(registries,inputs);var first=plan.first;var freshFeet=Set.copyOf(plan.feet);
        for(var pos:plan.seeds.keySet())require(old.containsKey(pos)&&!(old.get(pos).state.getBlock() instanceof NativeGearBlock),"New Gear column replaces another native owner or lacks a preimage");
        var nativeCells=new LinkedHashMap<BlockPos,NativeRawCell>();for(var cell:before)if(cell.state.getBlock() instanceof NativeGearBlock)nativeCells.put(cell.pos,cell);
        var assigned=new HashSet<BlockPos>();Integer direction=null;
        for(var cell:nativeCells.values()){
            var raw=nativeBefore(cell);if(!bit(raw,"GearController"))continue;var root=cell.pos;require(owner(root,raw).equals(root),"Native Gear controller owner mismatch");var def=((NativeGearBlock)cell.state.getBlock()).definition();require(def==(first.large?NativeGearDefinition.LARGE_EXTENDER:NativeGearDefinition.EXTENDER)&&raw.getString("ArmMountDirection").equals("DOWN")&&raw.getString("GearKind").equals("WHEEL")&&raw.contains("GearHeight",Tag.TAG_INT)&&raw.getInt("GearHeight")==first.height&&bit(raw,"GearOperational"),"Incompatible native neighbor assembly");
            var settings=NativeGearSettings.readSaved(raw);require(settings.legacyAnyChannels()&&settings.trigger()==first.trigger&&settings.channels().equals(first.channels)&&settings.footFacing()==first.facing.get2DDataValue(),"Incompatible native neighbor input/facing");require(raw.contains("GearProgress",Tag.TAG_DOUBLE)&&raw.getDouble("GearProgress")== (first.deployed?1:0)&&bit(raw,"GearTargetDeployed")==first.deployed,"Native neighbor endpoint mismatch");
            if(direction==null)direction=settings.direction();else require(direction==settings.direction(),"Native neighbor directions differ");
            var members=new LinkedHashSet<BlockPos>();for(var relative:vectors(raw,"GearColumns",64)){require(relative.getY()==0,"Native column is outside the common plane");var column=root.offset(relative);plan.columns.add(column);for(int n=0;n<first.height;n++)members.add(column.below(n));}
            boolean touches=false;var wheelCells=new HashSet<BlockPos>();for(var relative:vectors(raw,"GearFeet",64)){require(relative.getY()==-first.height,"Native foot outside the common plane");var foot=root.offset(relative);wheelCells.add(foot);plan.feet.add(foot);members.add(foot);for(var side:SIDES)touches|=freshFeet.contains(foot.relative(side));}require(touches,"Unrelated native neighbor group in recipe");
            require(GearTopology.validate(first.height,GearTopology.FootKind.WHEEL,wheelCells.stream().map(pos->new GearTopology.Cell(pos.getX()-root.getX(),pos.getY()-root.getY(),pos.getZ()-root.getZ())).toList()).isValid(),"Malformed native neighbor wheel topology");
            if(first.large){var roots=new HashSet<BlockPos>();for(var foot:wheelCells){var saved=nativeCells.get(foot);require(saved!=null&&saved.state.getBlock() instanceof NativeGearBlock block&&block.definition()==NativeGearDefinition.LARGE_WHEEL,"Missing Large Wheel cell");var origin=NativeGearComponents.root(foot,saved.state,Direction.DOWN);roots.add(origin.subtract(root));var facing=saved.state.getValue(NativeGearBlock.FACING);for(int part=0;part<4;part++){var at=origin.offset(NativeGearComponents.offset(Direction.DOWN,facing,part&1,part>>1));var other=nativeCells.get(at);require(wheelCells.contains(at)&&other!=null&&other.state.getBlock()==saved.state.getBlock()&&other.state.getValue(NativeGearBlock.FACING)==facing&&other.state.getValue(NativeGearBlock.PART)==part,"Incomplete native Large Wheel footprint");}}require(roots.equals(new HashSet<>(vectors(raw,"GearWheelRoots",64))),"Large Wheel root ownership differs");}
            for(var pos:members){var member=nativeCells.get(pos);require(member!=null&&assigned.add(pos),"Incomplete or multiply-owned native neighbor members");var own=nativeBefore(member);require(own.getString("ArmMountDirection").equals("DOWN")&&owner(pos,own).equals(root)&&bit(own,"GearController")==pos.equals(root),"Native member controller role mismatch");var type=((NativeGearBlock)member.state.getBlock()).definition();require(type==(wheelCells.contains(pos)?(first.large?NativeGearDefinition.LARGE_WHEEL:NativeGearDefinition.WHEEL):def),"Native neighbor component kind mismatch");plan.budget.admit(own);require(plan.seeds.putIfAbsent(pos,new Seed(member.state,own,own.getUUID("NativeGearIdentity")))==null,"Overlapping native neighbor after-images");}
        }
        require(assigned.equals(nativeCells.keySet()),"Unclaimed native members in recipe");var expected=build(first,plan.seeds,plan.columns,plan.feet,direction);var positions=new LinkedHashSet<BlockPos>();
        for(var cell:expected){var actual=next.get(cell.pos);require(actual!=null&&cell.state==actual.state&&same(cell.tile(),actual.tile()),"Unexpected native Gear after-image at "+cell.pos);positions.add(cell.pos);}
        for(var cell:after)if(cell.state.getBlock() instanceof NativeGearBlock)require(positions.contains(cell.pos),"Extra native Gear publication outside the typed destination");return Set.copyOf(positions);
    }
    private NativeGearLegacyDestination(){}
}
