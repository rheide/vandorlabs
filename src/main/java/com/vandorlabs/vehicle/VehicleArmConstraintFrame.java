package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.BlockCoupler;
import com.vandorlabs.gear.*;
import com.vandorlabs.physics.CouplerGraphConstraint;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One explicit current-config projection, not a revision-keyed cache or a gameplay/publication permit. */
public final class VehicleArmConstraintFrame {
 private final CouplerGraphConstraint.Graph graph;private final VehicleArmConstraintModel model;private final List<BlockPos> roots;private final int[] sectionBody;
 private VehicleArmConstraintFrame(CouplerGraphConstraint.Graph graph,VehicleArmConstraintModel model,List<BlockPos> roots,int[] sectionBody){this.graph=graph;this.model=model;this.roots=Collections.unmodifiableList(new ArrayList<>(roots));this.sectionBody=sectionBody.clone();}
 public CouplerGraphConstraint.Graph graph(){return graph;}public VehicleArmConstraintModel model(){return model;}public int arms(){return roots.size();}public BlockPos arm(int index){return roots.get(index);}public int sectionBody(int section){return sectionBody[section];}
 /** Call again for changed settings/controls; carried motionRevision alone is not a freshness key. */
 public static VehicleArmConstraintFrame prepare(VehicleStructure source,VehicleWorld view,VehicleCouplerTopology topology,List<BlockPos> armRoots){
  if(source==null||view==null||view.structure!=source||topology==null||armRoots==null||topology.sections.isEmpty()||topology.sections.size()>CouplerGraphConstraint.MAX_BODIES||topology.links.size()>CouplerGraphConstraint.MAX_LINKS||armRoots.size()>CouplerGraphConstraint.MAX_ARMS)throw new IllegalArgumentException("Constraint frame bounds/identity");
  World physical=view;int nesting=0;while(physical instanceof VehicleWorld){if(++nesting>8)throw new IllegalArgumentException("Nested constraint view bound");physical=((VehicleWorld)physical).parent;}if(physical==null||physical.isRemote||physical.getMinecraftServer()==null||!physical.getMinecraftServer().isCallingFromMinecraftThread())throw new IllegalStateException("Constraint frame requires owning server thread");
  return project(source,view,topology,armRoots);
 }
 /** Actual scalar projection seam for CPU references; not owning-thread or world admission. */
 static VehicleArmConstraintFrame project(VehicleStructure source,VehicleWorld view,VehicleCouplerTopology topology,List<BlockPos> armRoots){
  if(source==null||view==null||view.structure!=source||topology==null||armRoots==null||topology.sections.isEmpty()||topology.sections.size()>CouplerGraphConstraint.MAX_BODIES||topology.links.size()>CouplerGraphConstraint.MAX_LINKS||armRoots.size()>CouplerGraphConstraint.MAX_ARMS)throw new IllegalArgumentException("Constraint frame bounds/identity");
  int[] union=new int[topology.sections.size()];for(int i=0;i<union.length;i++)union[i]=i;
  for(VehicleCouplerTopology.Link link:topology.links){if(link.headA==null||link.headB==null)throw new IllegalArgumentException("Constraint frame needs stable head addresses");if(!link.articulation){if(link.headA.address.arm!=null||link.headB.address.arm!=null)throw new IllegalArgumentException("Nonarticulated arm constraint unsupported");union[find(union,link.second)]=find(union,link.first);}}
  Map<Integer,Integer> ids=new LinkedHashMap<>();int[] body=new int[union.length];for(int i=0;i<body.length;i++){int component=find(union,i);Integer id=ids.get(component);if(id==null){id=ids.size();ids.put(component,id);}body[i]=id;}
  List<BlockPos> roots=new ArrayList<>();Map<BlockPos,Integer> variables=new HashMap<>();VehicleArmConstraintModel.Arm[] arms=new VehicleArmConstraintModel.Arm[armRoots.size()];
  for(BlockPos root:armRoots){if(root==null||variables.containsKey(root))throw new IllegalArgumentException("Duplicate/missing arm root");BlockPos immutable=root.toImmutable();TileEntity tile=view.tiles.get(immutable);if(!(tile instanceof GearTileEntity)||tile.isInvalid()||tile.getWorld()!=view||!immutable.equals(((GearTileEntity)tile).owner())||!topology.section.containsKey(immutable))throw new IllegalArgumentException("Constraint arm owner changed");int index=roots.size();variables.put(immutable,index);roots.add(immutable);arms[index]=VehicleArmConstraintModel.Arm.capture((GearTileEntity)tile);}
  List<CouplerGraphConstraint.Link> links=new ArrayList<>();VehicleArmConstraintModel.Endpoint[] endpoints=new VehicleArmConstraintModel.Endpoint[topology.links.size()*2];int index=0;
  for(VehicleCouplerTopology.Link link:topology.links){VehicleArmCouplers.Head[] heads={link.headA,link.headB};for(int side=0;side<2;side++){VehicleArmCouplers.Head head=heads[side];Integer section=topology.section.get(head.carrier);if(section==null||section!=(side==0?link.first:link.second))throw new IllegalArgumentException("Constraint head carrier changed");
    VehicleStructure.Cell cell;int variable=-1;if(head.address.arm==null){cell=source.at(head.address.cell);}else{Integer found=variables.get(head.address.arm);if(found==null)throw new IllegalArgumentException("Constraint arm is absent from explicit control frame");variable=found;GearTileEntity owner=(GearTileEntity)view.tiles.get(head.address.arm);cell=owner.payload().blocks.at(head.address.cell);}
    if(cell==null||!(cell.state.getBlock() instanceof BlockCoupler))throw new IllegalArgumentException("Original constraint Coupler unavailable");IBlockState state=cell.state;EnumFacing facing=state.getValue(BlockCoupler.FACING);double x=cell.pos.getX()+.5+facing.getFrontOffsetX()*.5,z=cell.pos.getZ()+.5+facing.getFrontOffsetZ()*.5,y=cell.pos.getY()+(facing.getAxis()==EnumFacing.Axis.Y?.5+facing.getFrontOffsetY()*.5:BlockCoupler.couplingHeight(state));endpoints[index*2+side]=new VehicleArmConstraintModel.Endpoint(variable,x,y,z);
   }links.add(new CouplerGraphConstraint.Link(body[link.first],body[link.second]));index++;}
  return new VehicleArmConstraintFrame(new CouplerGraphConstraint.Graph(ids.size(),body[topology.root],links),new VehicleArmConstraintModel(arms,endpoints),roots,body);
 }
 private static int find(int[] parents,int i){while(parents[i]!=i)i=parents[i];return i;}
}
