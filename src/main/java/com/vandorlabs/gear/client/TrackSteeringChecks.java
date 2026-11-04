package com.vandorlabs.gear.client;

import com.vandorlabs.gear.track.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.blocks.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Compare actual emitted body/support vertices in the carried renderer. */
public final class TrackSteeringChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("Track swivel: "+why);}
    public static void run(Minecraft mc,java.io.File output)throws Exception{
        int cases=0;
        for(int style=0;style<3;style++)for(EnumFacing face:EnumFacing.HORIZONTALS)for(TrackAppearance.Mount mount:TrackAppearance.Mount.values())for(boolean joined:new boolean[]{false,true}){
            TrackTile template=new TrackTile();template.setPos(BlockPos.ORIGIN);NBTTagCompound tag=template.writeToNBT(new NBTTagCompound());tag.setInteger("TrackStyle",style);tag.setTag("Appearance",new TrackAppearance(joined?1:0,mount,0,0).write());
            List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,TrackRegistry.COMPACT.getDefaultState().withProperty(TrackBlock.FACING,face),tag));BlockPos seat=new BlockPos(3,0,0);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new VehicleStructure.Cell(seat.west(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
            EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);craft.install(new VehicleStructure(cells,seat,EnumFacing.NORTH,2));
            try{TrackTile track=(TrackTile)craft.view.getTileEntity(BlockPos.ORIGIN);craft.steering=1;boolean swivel=!joined&&(mount==TrackAppearance.Mount.TOP||mount==TrackAppearance.Mount.TOP_HIGH);require((TrackSteering.angle(craft,BlockPos.ORIGIN)!=0)==swivel,"top/single eligibility "+style+face+mount+joined);
                int[] turned=vertices(track);craft.steering=0;int[] straight=vertices(track);require(turned.length==straight.length&&turned.length>0,"mesh count stable");require(!Arrays.equals(turned,straight)==swivel,"actual carried mesh must turn only eligible Track body");
                if(swivel&&face==EnumFacing.NORTH&&mount==TrackAppearance.Mount.TOP){javax.imageio.ImageIO.write(TrackTreadChecks.pixels(mc,straight),"png",new java.io.File(output,"track-swivel-"+style+"-straight.png"));javax.imageio.ImageIO.write(TrackTreadChecks.pixels(mc,turned),"png",new java.io.File(output,"track-swivel-"+style+"-turned.png"));}
                if(swivel){craft.steering=1;require(TrackSteering.angle(craft,BlockPos.ORIGIN)>0,"forward steering follows vehicle turn in Track affine coordinates");int[] support=part("track/"+(mount==TrackAppearance.Mount.TOP?"top_mount":"top_mount_high"),TrackParts.quarterTurns(face));int start=straight.length-support.length;require(start>0,"separate support geometry");for(int i=start;i<straight.length;i++)require(straight[i]==turned[i],"top mount must remain fixed while body turns");
                    craft.steering=-1;require(TrackSteering.angle(craft,BlockPos.ORIGIN)<0,"reverse steering sign");craft.hover=true;require(TrackSteering.angle(craft,BlockPos.ORIGIN)==0,"Hover disables swivel");craft.hover=false;
                    NBTTagList members=new NBTTagList();for(int n=0;n<2;n++){NBTTagCompound member=new NBTTagCompound();member.setLong("Offset",new BlockPos(0,0,n).toLong());members.appendTag(member);}NBTTagCompound grouped=track.writeToNBT(new NBTTagCompound());grouped.setTag("TrackMembers",members);track.readFromNBT(grouped);require(TrackSteering.angle(craft,BlockPos.ORIGIN)==0,"saved multi-Track membership cannot swivel even with missing visual seam");
                }cases++;
            }finally{craft.signals.close();craft.setDead();}
        }
        System.out.println("[vandorlabs][reprolab] track-swivel PASS cases="+cases+" all-three-styles four-facings TOP/TOP_HIGH fixed-support emitted-body-vertices joined/socket/side/Hover negatives saved-membership");
    }
    private static int[] part(String name,int turns){BufferBuilder b=new BufferBuilder(262144);b.begin(7,DefaultVertexFormats.BLOCK);GearModels.drawTrack(name,GearRenderer.Affine.quarterTurn(turns),0,0,0,15728880,b,0);return data(b);}
    private static int[] vertices(TrackTile tile){BufferBuilder b=new BufferBuilder(262144);b.begin(7,DefaultVertexFormats.BLOCK);new GearRenderer().renderUncached(tile,0,0,0,0,b);return data(b);}
    private static int[] data(BufferBuilder b){b.finishDrawing();java.nio.IntBuffer data=b.getByteBuffer().asIntBuffer();int[] out=new int[b.getVertexCount()*b.getVertexFormat().getIntegerSize()];data.get(out);return out;}
    private TrackSteeringChecks(){}
}
