package com.vandorlabs.gear;

import com.vandorlabs.gear.core.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.coupler.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Independent roots evaluated through the actual released payload/gear transforms. */
public final class ArmParkingCandidatesChecks {
    private static long checks;private static int profiles,intermediate;
    private static Constructor<ArmPayload> constructor;
    private static final class Probe extends GearTileEntity {
        EnumFacing mount;int size;boolean swing;net.minecraft.block.Block block;
        @Override public net.minecraft.block.Block getBlockType(){return block==null?super.getBlockType():block;}
        @Override public EnumFacing mountDirection(){return mount;}
        @Override public int armSize(){return size;}
        @Override public boolean isSwingArmController(){return swing;}
    }
    public static final class CountedTile extends TileEntity {static int constructions;public CountedTile(){constructions++;}}
    private static final class CountedTag extends NBTTagCompound {static int copies;@Override public NBTTagCompound copy(){copies++;return super.copy();}}
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Arm parking: "+why);}
    private static void set(GearTileEntity arm,String key,Object value)throws Exception{Field field=GearTileEntity.class.getDeclaredField(key);field.setAccessible(true);field.set(arm,value);}
    private static ArmPayload payload(List<VehicleStructure.Cell> cells,int height)throws Exception{return constructor.newInstance(VehicleStructure.display(cells),new BlockPos(2,-height,1),false,BlockPos.ORIGIN);}
    private static Probe arm(GearMotion.Mode mode,EnumFacing mount,int size,GearMotion.Direction direction,int height,double first,double last,ArmPayload payload)throws Exception{
        Probe arm=new Probe();arm.setPos(new BlockPos(11,97,-17));arm.mount=mount;arm.size=size;arm.swing=mode!=GearMotion.Mode.EXTEND;
        set(arm,"assembly",GearTopology.validate(height,GearTopology.FootKind.NONE,Collections.singleton(new GearTopology.Cell(0,-height,0))).assembly());set(arm,"operational",true);set(arm,"mode",mode);set(arm,"direction",direction);set(arm,"activeAngle",first);set(arm,"startAngle",last);set(arm,"payload",payload);set(arm,"progress",.4D);return arm;
    }
    private static String key(ArmGridMapping map){StringBuilder out=new StringBuilder(map.cell(BlockPos.ORIGIN).toString());for(int x:map.basis())out.append('/').append(x);return out.toString();}
    private static String key(ArmParkingCandidates.Candidate c){StringBuilder out=new StringBuilder(new BlockPos(c.x,c.y,c.z).toString());for(int row=0;row<3;row++)for(int col=0;col<3;col++)out.append('/').append(c.basis(row,col));return out.toString();}
    private static double coordinate(Vec3d point,int axis){return axis==0?point.x:axis==1?point.y:point.z;}
    private static double root(java.util.function.DoubleUnaryOperator f,double target){double a=f.applyAsDouble(0),b=f.applyAsDouble(1);if(Math.abs(target-a)<1e-12)return 0;if(Math.abs(target-b)<1e-12)return 1;double lo=0,hi=1;for(int i=0;i<60;i++){double mid=(lo+hi)*.5;if((f.applyAsDouble(mid)<target)==(a<b))lo=mid;else hi=mid;}return (lo+hi)*.5;}
    private static Set<String> oracle(Probe arm){
        ArmPayload p=arm.payload();List<Double> phases=new ArrayList<>(Arrays.asList(0D,1D));
        if(arm.mode()==GearMotion.Mode.EXTEND){Vec3d a=p.transform(arm,0,0,0,0),b=p.transform(arm,1,0,0,0);int axis=0;for(int i=1;i<3;i++)if(Math.abs(coordinate(b,i)-coordinate(a,i))>Math.abs(coordinate(b,axis)-coordinate(a,axis)))axis=i;final int selected=axis;double min=Math.min(coordinate(a,axis),coordinate(b,axis)),max=Math.max(coordinate(a,axis),coordinate(b,axis));for(int n=(int)Math.ceil(min);n<=Math.floor(max);n++)phases.add(root(v->coordinate(p.transform(arm,v,0,0,0),selected),n));}
        else if(arm.mode()==GearMotion.Mode.ROTATE){double a=arm.pose(0).angleDegrees(),b=arm.pose(1).angleDegrees();for(int n=(int)Math.ceil(Math.min(a,b)/90);n<=Math.floor(Math.max(a,b)/90);n++)phases.add(root(v->arm.pose(v).angleDegrees(),n*90));}
        else{
            // Source arm-only socket radius is height-9/16. The integer circle equation
            // for heights1..8 has no nonzero lattice translation: 8*(x*x+y*y)=(16*h-9)*y.
            int r=16*arm.assembly().columnHeight()-9;for(int x=-16;x<=16;x++)for(int y=0;y<=16;y++)if((16*x)*(16*x)+(16*y-r)*(16*y-r)==r*r)need(x==0&&y==0,"Independent Source leveling circle has only its deployed grid mapping");
            double a=arm.pose(0).angleDegrees(),b=arm.pose(1).angleDegrees();if(Math.min(a,b)<=0&&Math.max(a,b)>=0)phases.add(root(v->arm.pose(v).angleDegrees(),0));
        }
        Set<String> out=new HashSet<>();for(double phase:phases){ArmGridMapping mapping=p.gridMapping(arm,phase);if(mapping!=null)out.add(key(mapping));}return out;
    }
    private static void profile(Probe arm)throws Exception{
        NBTTagCompound before=arm.payload().write();double progress=arm.progress();ArmParkingCandidates.Result result=ArmParkingCandidates.inspect(arm);need(result.completeForDistinctGridMappings(),"Complete supported arm-only curve");Set<String> found=new HashSet<>();
        for(ArmParkingCandidates.Candidate candidate:result.candidates){ArmGridMapping mapping=arm.payload().gridMapping(arm,candidate.phase);need(mapping!=null&&key(mapping).equals(key(candidate)),"Scalar metadata equals actual Source grid mapping");found.add(key(candidate));if(candidate.phase>0&&candidate.phase<1)intermediate++;}
        need(found.equals(oracle(arm)),"Complete independent actual-transform root set mode="+arm.mode()+" mount="+arm.mount+" first="+arm.pose(0).angleDegrees()+" last="+arm.pose(1).angleDegrees()+" found="+found);
        need(result.testedPhases<=68&&result.transformQueries<=4*result.testedPhases+3,"Geometry cost bound");need(result.stateChecks<=24*arm.payload().blocks.cells.size(),"One typed scan per distinct proper basis");need(progress==arm.progress()&&before.equals(arm.payload().write()),"Read-only original snapshot and live phase");profiles++;
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(CountedTile.class,new ResourceLocation("test:parking_counter"));constructor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);constructor.setAccessible(true);
        List<VehicleStructure.Cell> stone=Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));
        for(EnumFacing mount:EnumFacing.values())for(int size:new int[]{1,2})for(GearMotion.Direction direction:GearMotion.Direction.values())for(int height:new int[]{1,3,8}){
            for(boolean retract:new boolean[]{false,true}){Probe arm=arm(GearMotion.Mode.EXTEND,mount,size,direction,height,90,0,payload(stone,height));set(arm,"armRetractingStroke",retract);profile(arm);}
            for(GearMotion.Mode mode:new GearMotion.Mode[]{GearMotion.Mode.ROTATE,GearMotion.Mode.ROTATE_LEVEL})for(double[] range:new double[][]{{90,0},{180,0},{-180,180},{45,-75},{15,15}})profile(arm(mode,mount,size,direction,height,range[0],range[1],payload(stone,height)));
        }
        typedChecks(stone);storedChecks(stone);cost(stone);need(intermediate>100,"Actual valid intermediate mappings exercised");System.out.println("Arm parking actual Source PASS profiles="+profiles+" intermediate="+intermediate+" checks="+checks+" all6mounts regular/Large/eased/typed/deferred/retained/readOnly; no gameplay binding");
    }
    private static void typedChecks(List<VehicleStructure.Cell> stone)throws Exception{
        CountedTag tag=new CountedTag();tag.setString("id","test:parking_counter");tag.setString("Unknown","opaque");NBTTagCompound cap=new NBTTagCompound();cap.setByteArray("private",new byte[200]);tag.setTag("ForgeCaps",cap);
        ArmPayload p=payload(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),tag)),3);Field raw=VehicleStructure.Cell.class.getDeclaredField("tile");raw.setAccessible(true);raw.set(p.blocks.cells.get(0),tag);Probe arm=arm(GearMotion.Mode.EXTEND,EnumFacing.DOWN,1,GearMotion.Direction.NORTH,3,90,0,p);CountedTag.copies=0;int created=CountedTile.constructions;
        ArmParkingCandidates.Result result=ArmParkingCandidates.inspect(arm);need(!result.candidates.isEmpty()&&result.tileValidationDeferred(),"Identity tile ownership remains available with explicit chosen-candidate validation");need(CountedTag.copies==0&&created==CountedTile.constructions,"No NBT clone/factory during translation trials");Field view=ArmPayload.class.getDeclaredField("view");view.setAccessible(true);need(view.get(p)==null,"Enumeration never installs a payload/capability view");
        set(arm,"mode",GearMotion.Mode.ROTATE);arm.swing=true;set(arm,"activeAngle",135D);set(arm,"startAngle",45D);need(ArmParkingCandidates.inspect(arm).candidates.isEmpty(),"Unknown tile rotation refused without guessing");need(CountedTag.copies==0&&created==CountedTile.constructions,"Refusal leaves original unknown envelope untouched");
        Probe missing=arm(GearMotion.Mode.EXTEND,EnumFacing.DOWN,1,GearMotion.Direction.NORTH,3,90,0,payload(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),null)),3));need(ArmParkingCandidates.inspect(missing).status==ArmParkingCandidates.Status.UNSUPPORTED,"Missing own tile NBT refused");
        for(BlockCoupler block:new BlockCoupler[]{new BlockCoupler(),new BlockCouplerSlab()})for(EnumFacing mount:EnumFacing.values())for(GearMotion.Direction direction:GearMotion.Direction.values()){
            CountedTag data=new CountedTag();data.setString("id","vandorlabs:square_coupler");data.setTag("ForgeCaps",cap);ArmPayload coupler=payload(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,block.getDefaultState(),data)),3);raw.set(coupler.blocks.cells.get(0),data);Probe root=arm(GearMotion.Mode.ROTATE,mount,2,direction,3,180,0,coupler);NBTTagCompound before=coupler.write();CountedTag.copies=0;ArmParkingCandidates.Result candidates=ArmParkingCandidates.inspect(root);need(CountedTag.copies==0,"Typed Coupler orientation trials never clone NBT");
            for(ArmParkingCandidates.Candidate candidate:candidates.candidates){ArmGridMapping map=coupler.gridMapping(root,candidate.phase);need(!map.rotates()||ArmGridTiles.state(coupler.blocks.cells.get(0),map.basis())!=null,"Exact existing typed Coupler orientation policy");}need(before.equals(coupler.write()),"Typed Coupler bytes preserved");
        }
        Probe mixed=arm(GearMotion.Mode.ROTATE,EnumFacing.DOWN,1,GearMotion.Direction.NORTH,3,90,0,payload(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null),new VehicleStructure.Cell(new BlockPos(1,0,0),Blocks.STONE.getDefaultState(),tag)),3));
        ArmParkingCandidates.Result mixedResult=ArmParkingCandidates.inspect(mixed);need(mixedResult.candidates.size()==1&&mixedResult.tileValidationDeferred(),"Tile-free state memo never authorizes a tile-bearing cell with the same state");need(mixedResult.typedStateQueries==2,"Tile-bearing cells retain individual typed validation");
        Probe missingAfterPresent=arm(GearMotion.Mode.EXTEND,EnumFacing.DOWN,1,GearMotion.Direction.NORTH,3,90,0,payload(Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.CHEST.getDefaultState(),tag),new VehicleStructure.Cell(new BlockPos(1,0,0),Blocks.CHEST.getDefaultState(),null)),3));need(ArmParkingCandidates.inspect(missingAfterPresent).status==ArmParkingCandidates.Status.UNSUPPORTED,"Every cell keeps its own missing-NBT preflight");
        Field journal=ArmPayload.class.getDeclaredField("ownershipJournal");journal.setAccessible(true);((Map<Integer,BlockPos>)journal.get(p)).put(0,BlockPos.ORIGIN);need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"Unreconciled ownership refused");((Map<?,?>)journal.get(p)).clear();set(arm,"payload",null);set(arm,"failedPayload",tag);need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"Retained failed snapshot refused");need(ArmParkingCandidates.enumerate(null).status==ArmParkingCandidates.Status.INCOMPLETE,"No detached public query");
    }
    @SuppressWarnings("unchecked") private static void storedChecks(List<VehicleStructure.Cell> stone)throws Exception{
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        GearBlock[] blocks={new GearBlock("parking_regular_section",GearBlock.Kind.EXTENDER),new LargeGearBlock("parking_large_section",GearBlock.Kind.EXTENDER)};
        for(GearBlock block:blocks)net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        Constructor<ArmStoredSections> make=ArmStoredSections.class.getDeclaredConstructor(List.class);make.setAccessible(true);Field raw=VehicleStructure.Cell.class.getDeclaredField("tile");raw.setAccessible(true);Field members=GearTileEntity.class.getDeclaredField("members");members.setAccessible(true);Field columns=GearTileEntity.class.getDeclaredField("columns");columns.setAccessible(true);Field rows=ArmStoredSections.class.getDeclaredField("cells");rows.setAccessible(true);
        for(EnumFacing mount:EnumFacing.values())for(int size:new int[]{1,2}){
            Probe arm=arm(GearMotion.Mode.EXTEND,mount,size,GearMotion.Direction.NORTH,3,90,0,payload(stone,3));BlockPos relative=ArmOrientation.vector(mount,new BlockPos(0,-1,0));
            ((Set<BlockPos>)members.get(arm)).add(arm.getPos());((Set<BlockPos>)members.get(arm)).add(arm.getPos().add(relative));((List<BlockPos>)columns.get(arm)).add(BlockPos.ORIGIN);
            CountedTag data=new CountedTag();data.setString("id","vandorlabs:modular_gear");data.setInteger("x",relative.getX());data.setInteger("y",relative.getY());data.setInteger("z",relative.getZ());data.setLong("GearOwnerOffset",BlockPos.ORIGIN.subtract(relative).toLong());NBTTagCompound opaque=new NBTTagCompound();opaque.setString("owner","foreign:section");data.setTag("ForgeCaps",opaque);
            VehicleStructure.Cell cell=new VehicleStructure.Cell(relative,blocks[size-1].getDefaultState(),data);arm.storedSections=make.newInstance(Collections.singletonList(cell));raw.set(cell,data);ArmStoredSections original=arm.storedSections;NBTTagCompound before=original.write();CountedTag.copies=0;
            ArmParkingCandidates.Result result=ArmParkingCandidates.inspect(arm);need(result.completeForDistinctGridMappings()&&!result.candidates.isEmpty()&&result.storedSectionCount==1&&result.storedSectionValidationDeferred(),"Normal displaced native sections remain parkable on every mount/size");for(ArmParkingCandidates.Candidate candidate:result.candidates)need(candidate.storedSectionValidationDeferred,"Chosen candidate defers separate section restoration");need(CountedTag.copies==0&&arm.storedSections==original,"Stored section query neither clones nor transfers ownership");need(before.equals(original.write()),"Original section envelope unchanged");
            arm.block=blocks[size-1];ArmParkingCandidates.Candidate zero=null,middle=null;for(ArmParkingCandidates.Candidate candidate:result.candidates){if(candidate.phase==0)zero=candidate;if(candidate.phase>0&&candidate.phase<1)middle=candidate;}need(zero!=null&&middle!=null,"Actual legal end/intermediate candidates available for displacement policy");
            BlockPos nativePos=arm.getPos().add(relative);NBTTagCompound nativeData=data.copy();nativeData.setInteger("x",nativePos.getX());nativeData.setInteger("y",nativePos.getY());nativeData.setInteger("z",nativePos.getZ());VehicleStructure.Cell nativeSection=new VehicleStructure.Cell(nativePos,blocks[size-1].getDefaultState(),nativeData);
            need(ArmParkingMaterialization.mayDisplace(arm,zero,nativeSection),"New native own-section displacement uses exact phase-zero Source policy");need(!ArmParkingMaterialization.mayDisplace(arm,middle,nativeSection),"Intermediate NEW displacement remains unavailable pending lifecycle proof");need(ArmParkingMaterialization.mayRetainStored(arm,middle,cell),"Existing proven stored ownership may remain at an intermediate chosen pose");
            long owner=data.getLong("GearOwnerOffset");data.setLong("GearOwnerOffset",0);CountedTag.copies=0;need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE&&CountedTag.copies==0,"Foreign section owner refused read-only");data.setLong("GearOwnerOffset",owner);
            data.setInteger("GearOwnerOffset",(int)owner);need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"Wrong owner-offset NBT type refused");data.setLong("GearOwnerOffset",owner);
            data.setString("ArmPayload","malformed retained owner");need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"Nested retained ownership refused regardless of envelope type");data.removeTag("ArmPayload");
            data.setTag(GearTaxiState.KEY,new NBTTagCompound());need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"A retained Taxi owner cannot hide inside a native arm section");data.removeTag(GearTaxiState.KEY);
            rows.set(original,Collections.unmodifiableList(Arrays.asList(cell,cell)));need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"Duplicate section ownership refused");rows.set(original,Collections.singletonList(cell));
            data.setString("id","foreign:gear");need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE,"Unknown section tile kind refused without factories");data.setString("id","vandorlabs:modular_gear");
            arm.storedSections=null;set(arm,"failedStoredSections",data);CountedTag.copies=0;need(ArmParkingCandidates.inspect(arm).status==ArmParkingCandidates.Status.INCOMPLETE&&CountedTag.copies==0,"Failed retained sections remain unavailable and untouched");
        }
    }
    private static void cost(List<VehicleStructure.Cell> stone)throws Exception{
        List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=0;x<32;x++)for(int y=0;y<32;y++)for(int z=0;z<32;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,y,z),Blocks.STONE.getDefaultState(),null));Probe arm=arm(GearMotion.Mode.ROTATE,EnumFacing.DOWN,2,GearMotion.Direction.EAST,8,90,0,payload(cells,8));
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();ArmParkingCandidates.Result result=ArmParkingCandidates.inspect(arm);long coldNanos=System.nanoTime()-start,coldBytes=bean.getThreadAllocatedBytes(thread)-before;need(result.completeForDistinctGridMappings()&&result.stateChecks<=24*32768&&result.typedStateQueries==1,"Worst payload bounded typed state passes with one tile-free state mapping");
        Probe warm=arm(GearMotion.Mode.EXTEND,EnumFacing.SOUTH,1,GearMotion.Direction.NORTH,8,90,0,payload(stone,8));for(int i=0;i<2000;i++)ArmParkingCandidates.inspect(warm);before=bean.getThreadAllocatedBytes(thread);start=System.nanoTime();for(int i=0;i<1000;i++)ArmParkingCandidates.inspect(warm);long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-before;
        System.out.println("Arm parking cost coldCells=32768 phases="+result.testedPhases+" transformQueries="+result.transformQueries+" stateChecks="+result.stateChecks+" typedStateQueries="+result.typedStateQueries+" coldNanos="+coldNanos+" coldBytes="+coldBytes+" warmCalls=1000 nanos="+nanos+" allocatedBytes="+bytes+" NBTclones=0 factories=0 (outputs allocate)");
    }
    private ArmParkingCandidatesChecks(){}
}
