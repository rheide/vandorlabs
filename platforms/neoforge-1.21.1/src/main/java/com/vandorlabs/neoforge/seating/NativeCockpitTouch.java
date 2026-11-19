package com.vandorlabs.neoforge.seating;

import com.google.gson.JsonParser;
import com.vandorlabs.neoforge.screen.NativeScreenControlService;
import com.vandorlabs.neoforge.screen.NativeScreenControls;
import com.vandorlabs.neoforge.screen.NativeScreenRowEdit;
import com.vandorlabs.neoforge.screen.NativeYokeGeometry;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.TransmissionSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Stationary cockpit control surfaces, with independent saved banks. */
public final class NativeCockpitTouch implements NativeScreenControlService.Host {
    public static final int TOUCH=0,CENTER=1;
    private static final String[] KEYS={"NativeCockpitTouchRows","NativeCockpitCenterRows"};
    private static final String[] IDS={"touch_right_keyboard","screen_center"};
    private static final double APRON=10.5/16;
    private static final List<Map<Direction,NativeYokeGeometry.Face>> FACES=faces();
    private final NativeChairEntity owner;
    private final NativeScreenControlService service;
    private final NativeScreenControls[] data={empty(),empty()};
    private static NativeScreenControls empty(){return new NativeScreenControls(true,"",List.of());}
    private boolean loaded;

    NativeCockpitTouch(NativeChairEntity owner){
        this.owner=owner;
        service=new NativeScreenControlService(this);
    }

    public NativeScreenControls controls(int slot){return data[slot];}
    @Override public int rowCapacity(int slot){return slot==TOUCH?3:slot==CENTER?6:0;}
    @Override public boolean supportsRowSlot(int slot){return slot==TOUCH||slot==CENTER;}
    @Override public int rowTransmissionRange(){return TransmissionSettings.DEFAULT_RANGE;}
    @Override public BlockEntity channelBlockEntity(){return owner;}
    @Override public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}
    @Override public void setChannelLevel(int ignored){}
    @Override public boolean isChannelMemberActive(){return NativeScreenControlService.Host.super.isChannelMemberActive()&&!owner.inert();}

    @Override public void installControlData(int slot,NativeScreenControls next,boolean notify){
        if(!supportsRowSlot(slot))return;
        data[slot]=next;
        owner.setChanged();
        if(notify&&owner.getLevel()!=null)owner.getLevel().sendBlockUpdated(owner.getBlockPos(),
            owner.getBlockState(),owner.getBlockState(),2);
    }

    void finishLoading(){
        if(!loaded&&owner.getLevel() instanceof net.minecraft.server.level.ServerLevel){
            service.finishLoading();
            loaded=true;
        }
    }
    void stop(){service.stop();loaded=false;}
    boolean read(CompoundTag tag,boolean fromClientUpdate){
        for(int slot=0;slot<KEYS.length;slot++){
            String key=KEYS[slot];
            if(!tag.contains(key)){data[slot]=empty();continue;}
            if(!tag.contains(key,Tag.TAG_COMPOUND))return false;
            var raw=tag.getCompound(key);
            if(!(raw.get("Rows") instanceof net.minecraft.nbt.ListTag rows)
                ||!rows.isEmpty()&&rows.getElementType()!=Tag.TAG_COMPOUND
                ||rows.size()>rowCapacity(slot)||!NativeScreenControls.validConfiguration(raw))return false;
            data[slot]=NativeScreenControls.read(raw,rowCapacity(slot));
        }
        if(!fromClientUpdate)service.savedChanged();
        return true;
    }

    void write(CompoundTag tag,boolean item){
        for(int slot=0;slot<KEYS.length;slot++){
            String key=KEYS[slot];
            if(item)tag.put(key,data[slot].configuration());
            else{
                var saved=tag.getCompound(key).copy();
                data[slot].write(saved);
                tag.put(key,saved);
            }
        }
    }

    public boolean configure(int slot,NativeScreenRowEdit edit){finishLoading();return service.configure(slot,edit);}
    public boolean editing(){return service.editing();}
    void use(int slot,int row,int segment){
        if(row<0||row>=data[slot].rows().size())return;
        finishLoading();
        if(data[slot].rows().get(row).slider())service.select(slot,row,segment);
        else service.toggle(slot,row);
    }

    public static NativeYokeGeometry.Face face(Direction facing){return face(facing,TOUCH,0);}
    public static NativeYokeGeometry.Face face(Direction facing,int slot,double lift){
        var face=FACES.get(slot).get(facing);
        if(slot==TOUCH||lift==0)return face;
        var top=face.topLeft().add(0,-NativeCockpitLift.TRAVEL*lift,0);
        if(top.y<=APRON)return null;
        return new NativeYokeGeometry.Face(top,face.right(),face.down(),face.normal(),face.height());
    }
    public static int visiblePixels(NativeYokeGeometry.Face face,int slot){
        return slot==TOUCH?face.height():Math.min(face.height(),
            Math.max(0,(int)Math.floor(face.height()*(face.topLeft().y-APRON)/-face.down().y)));
    }
    record Pick(BlockHitResult hit,int slot,int row,int segment){}
    static Pick pick(BlockPos root,Direction facing,BlockPos clicked,Vec3 start,Vec3 end,int rows){
        return pick(root,facing,clicked,start,end,TOUCH,rows,0);
    }
    private static Pick pick(BlockPos root,Direction facing,BlockPos clicked,Vec3 start,Vec3 end,int slot,int rows,double lift){
        var face=face(facing,slot,lift);
        if(face==null)return null;
        Vec3 origin=Vec3.atLowerCornerOf(root);
        var sample=face.sample(start.subtract(origin),end.subtract(origin),Math.max(1,rows));
        if(sample==null)return null;
        var point=start.lerp(end,sample.time());
        if(!BlockPos.containing(point).equals(clicked)||slot==CENTER&&point.y-root.getY()<APRON)return null;
        return new Pick(new BlockHitResult(point,Direction.UP,clicked,false),slot,rows==0?-1:sample.row(),sample.sliderStep());
    }

    Pick pick(BlockPos clicked,Vec3 start,Vec3 end){
        var root=owner.getBlockPos();
        var facing=owner.getBlockState().getValue(NativeSeatBlock.FACING);
        var touch=pick(root,facing,clicked,start,end,TOUCH,data[TOUCH].rows().size(),0);
        var center=pick(root,facing,clicked,start,end,CENTER,data[CENTER].rows().size(),owner.liftPosition());
        if(touch==null)return center;
        return center!=null&&center.hit().getLocation().distanceToSqr(start)<touch.hit().getLocation().distanceToSqr(start)?center:touch;
    }

    private static List<Map<Direction,NativeYokeGeometry.Face>> faces(){
        String path="/assets/vandorlabs/cockpits/cockpit/surfaces.json";
        try(var stream=NativeCockpitTouch.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing authored Cockpit surfaces");
            var list=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("surfaces");
            var result=new java.util.ArrayList<Map<Direction,NativeYokeGeometry.Face>>();
            for(String id:IDS){
                var byFacing=new EnumMap<Direction,NativeYokeGeometry.Face>(Direction.class);
                for(var entry:list){
                    var face=entry.getAsJsonObject();
                    if(!face.get("id").getAsString().equals(id))continue;
                    for(var direction:Direction.Plane.HORIZONTAL)byFacing.put(direction,NativeYokeGeometry.authoredFace(face,direction));
                    break;
                }
                if(byFacing.isEmpty())throw new IllegalStateException("Missing Cockpit surface "+id);
                result.add(Map.copyOf(byFacing));
            }
            return List.copyOf(result);
        }catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}
