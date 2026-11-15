package com.vandorlabs.neoforge.gear.client;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.gear.core.*;
import com.vandorlabs.neoforge.gear.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** One renderer per assembled controller, using the original authored parts and shared motion kernel. */
public final class NativeGearRenderer implements BlockEntityRenderer<NativeGearEntity> {
    public NativeGearRenderer(BlockEntityRendererProvider.Context context){}
    public static boolean visible(NativeGearEntity tile){var visual=tile.visual();return tile.getLevel()!=null&&(visual.owner()==null||visual.owner().equals(tile.getBlockPos())||tile.controllerEntity()==null)&&tile.getBlockState().getValue(NativeGearBlock.PART)==0;}
    @Override public void render(NativeGearEntity tile,float partial,PoseStack stack,MultiBufferSource buffers,int light,int overlay){if(!visible(tile))return;var mesh=NativeGearResources.bake();if(mesh==null)return;var publication=tile.visual();double phase=publication.inert()?1:NativeGearVisualClock.pose(tile);emit(tile,phase,(name,transform,cache)->mesh.draw(name,transform,cache,stack.last(),buffers.getBuffer(RenderType.cutout()),light,overlay));NativeGearPayloadRenderer.draw(tile,phase,partial,stack,buffers,light,overlay);}
    public interface Draw {void part(String name,NativeGearAffine transform,boolean cache);}
    public static void emit(NativeGearEntity tile,double phase,Draw output){var definition=tile.definition();var assembly=tile.assembly();var mounting=NativeGearAffine.mount(tile.mount());var fixed=NativeGearAffine.quarterTurn(turns(tile.getBlockState().getValue(NativeGearBlock.FACING)));boolean cache=phase==0||phase==1;
        Draw draw=(name,transform,reuse)->output.part(name,mounting.compose(transform),reuse);
        if(assembly==null||tile.inert()){String model=switch(definition.kind){case EXTENDER->definition.size==2?"extender_large_icon":"extender";case ROTATING->definition.size==2?"swing_arm_large_icon":"swing_arm";case MOUNT->definition.size==2?"fixed_mount_large_icon":"fixed_mount";case PLATE->"plate_0";case SKI->"ski_0";case WHEEL->definition.size==2?"wheel_large":"wheel";case TRACK->"empty";};draw.part(model,fixed,true);return;}
        var pose=tile.pose(phase);var moving=NativeGearAffine.fromPose(pose,false);var feet=NativeGearAffine.fromPose(pose,true);boolean swing=definition.kind==NativeGearDefinition.Kind.ROTATING,mount=definition.kind==NativeGearDefinition.Kind.MOUNT;int width=definition.size;double shift=(width-1)*.5;
        for(BlockPos column:tile.columns()){var canonical=NativeGearOrientation.inverse(tile.mount(),column);var offset=NativeGearAffine.translation(canonical.getX(),canonical.getY(),canonical.getZ());var columnTile=NativeGearFormation.at(tile.getLevel(),tile.getBlockPos().offset(column));var columnFixed=columnTile==null?fixed:NativeGearAffine.quarterTurn(turns(columnTile.getBlockState().getValue(NativeGearBlock.FACING)));var size=NativeGearAffine.translation(-shift,0,-shift).compose(NativeGearAffine.scaleXZ(width));var bar=NativeGearAffine.scaleXZ(swing?(pose.direction().axisX()!=0?width:1):width,swing?(pose.direction().axisZ()!=0?width:1):width);bar=NativeGearAffine.translation(.5,0,.5).compose(bar.compose(NativeGearAffine.translation(-.5,0,-.5)));var arm=moving.compose(bar);var foot=feet.compose(size);
            draw.part(mount?(width==2?"fixed_mount_compact":"fixed_mount"):"mount",offset.compose(columnFixed),true);if(!mount)draw.part("sleeve",offset.compose(columnFixed.compose(size)),true);
            if(swing){var yaw=NativeGearAffine.quarterTurn(tile.settings().direction());var stretch=NativeGearAffine.stretchArm(assembly.columnHeight()-tile.socketOffset());
                if(tile.settings().hingeMode()==ArmHinge.Mode.DOUBLE||tile.settings().kinkDirection()!=tile.settings().direction()){
                    var kinkYaw=NativeGearAffine.quarterTurn(tile.settings().kinkDirection());var upper=NativeGearAffine.fromUpperPose(pose);
                    draw.part("swing_linkage_upper_body",offset.compose(upper.compose(bar.compose(stretch.compose(kinkYaw)))),cache);
                    var fold=GearMotion.Direction.values()[tile.settings().direction()];var kink=GearMotion.Direction.values()[tile.settings().kinkDirection()];
                    var elbow=upper.compose(NativeGearAffine.translation(ArmHinge.kinkOffsetX(fold,kink,width),0,ArmHinge.kinkOffsetZ(fold,kink,width)).compose(bar.compose(stretch.compose(yaw))));
                    draw.part("swing_linkage_elbow",offset.compose(elbow),cache);
                    draw.part("swing_linkage_lower",offset.compose(arm.compose(stretch.compose(kinkYaw))),cache);
                }else{
                    if(tile.settings().middleHinge())draw.part("swing_linkage_upper",offset.compose(bar.compose(stretch.compose(yaw))),true);
                    draw.part(tile.settings().middleHinge()?"swing_linkage_lower":"swing_linkage",offset.compose(arm.compose(stretch.compose(yaw))),cache);
                }
                draw.part("swivel",offset.compose(foot.compose(NativeGearAffine.translation(0,-assembly.columnHeight()+tile.socketOffset()-(assembly.kind()==GearTopology.FootKind.TRACK?1/64D:0),0).compose(yaw))),cache);
            }
            else {if(!mount||pose.rodBottomY()+tile.socketOffset()<-.0001)draw.part("rod",offset.compose(arm.compose(NativeGearAffine.scaleY(pose.rodTopY()-pose.rodBottomY()-tile.socketOffset(),pose.rodBottomY()+tile.socketOffset()))),cache);if(assembly.kind()!=GearTopology.FootKind.NONE||!mount)draw.part("socket",offset.compose(foot.compose(NativeGearAffine.translation(0,-assembly.columnHeight()+tile.socketOffset(),0).compose(assembly.kind()!=GearTopology.FootKind.NONE?NativeGearAffine.scaleY(1.25,-1/16D):NativeGearAffine.identity()))),cache);}
        }
        if(assembly.kind()==GearTopology.FootKind.TRACK){var tracks=tile.trackDescriptor();if(tracks==null)return;for(var entry:tracks.feet()){var cell=entry.cell();var at=feet.compose(NativeGearAffine.translation(cell.x,cell.y,cell.z));com.vandorlabs.gear.track.core.TrackMeshPolicy.emit(tracks.style(),Math.floorMod(tile.settings().footFacing()+2,4),entry.appearance(),part->draw.part("track/"+part.name,at.compose(NativeGearAffine.quarterTurn(part.quarterTurns)),cache));}return;}
        int turns=assembly.kind()==GearTopology.FootKind.PLATE?0:Math.floorMod(tile.settings().footFacing()+2,4);var orientation=NativeGearAffine.quarterTurn(turns);
        for(var cell:assembly.feet()){if(assembly.kind()==GearTopology.FootKind.NONE)break;var at=feet.compose(NativeGearAffine.translation(cell.x,cell.y,cell.z));if(assembly.kind()==GearTopology.FootKind.WHEEL){var source=tile.getBlockPos().offset(NativeGearOrientation.vector(tile.mount(),new BlockPos(cell.x,cell.y,cell.z)));if(!tile.wheelRoot(cell))continue;String name=tile.definition().size==2?"wheel_large":"wheel";draw.part(name+"_bracket",at.compose(orientation),cache);draw.part(name+"_tyre",at.compose(orientation),cache);double cy=name.equals("wheel_large")?13/16D:9.375/13D;var pivot=orientationPoint(orientation,.5,cy,.5);draw.part(name+"_connector",connector(pose,cell.x+pivot.x,cell.y+pivot.y,cell.z+pivot.z).compose(NativeGearAffine.translation(cell.x,cell.y,cell.z).compose(orientation)),cache);}else {int mask=0,world=assembly.connectionMask(cell);for(int local=0;local<4;local++)if((world&1<<((local+turns)&3))!=0)mask|=1<<local;draw.part((assembly.kind()==GearTopology.FootKind.PLATE?"plate_":"ski_")+mask,at.compose(orientation),cache);}}
    }
    private static GearMotion.Vec orientationPoint(NativeGearAffine a,double x,double y,double z){return new GearMotion.Vec(a.xx*x+a.xy*y+a.xz*z+a.tx,a.yx*x+a.yy*y+a.yz*z+a.ty,a.zx*x+a.zy*y+a.zz*z+a.tz);}
    private static NativeGearAffine connector(GearMotion.Pose p,double cx,double cy,double cz){var o=p.transformConnectorVertex(0,0,0,cx,cy,cz);var x=p.transformConnectorVertex(1,0,0,cx,cy,cz);var y=p.transformConnectorVertex(0,1,0,cx,cy,cz);var z=p.transformConnectorVertex(0,0,1,cx,cy,cz);return new NativeGearAffine(x.x-o.x,y.x-o.x,z.x-o.x,x.y-o.y,y.y-o.y,z.y-o.y,x.z-o.z,y.z-o.z,z.z-o.z,o.x,o.y,o.z);}
    private static int turns(Direction face){return switch(face){case EAST->1;case SOUTH->2;case WEST->3;default->0;};}
    @Override public int getViewDistance(){return 256;}
    @Override public AABB getRenderBoundingBox(NativeGearEntity tile){var p=tile.getBlockPos();var a=tile.assembly();double radius=a==null?4:tile.geometryReach();return new AABB(p).inflate(radius);}
}
