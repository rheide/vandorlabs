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

/** Right touch panel, retaining the first three released Cockpit row positions. */
public final class NativeCockpitTouch implements NativeScreenControlService.Host {
    static final int CAPACITY=3;
    static final String KEY="NativeCockpitTouchRows";
    private static final NativeScreenControls EMPTY=new NativeScreenControls(false,"",List.of());
    private static final Map<Direction,NativeYokeGeometry.Face> FACES=faces();
    private final NativeChairEntity owner;
    private final NativeScreenControlService service;
    private NativeScreenControls data=new NativeScreenControls(true,"",List.of());
    private boolean loaded;

    NativeCockpitTouch(NativeChairEntity owner){
        this.owner=owner;
        service=new NativeScreenControlService(this);
    }

    public NativeScreenControls controls(int slot){return slot==0?data:EMPTY;}
    @Override public int rowCapacity(int slot){return slot==0?CAPACITY:0;}
    @Override public boolean supportsRowSlot(int slot){return slot==0;}
    @Override public int rowTransmissionRange(){return TransmissionSettings.DEFAULT_RANGE;}
    @Override public BlockEntity channelBlockEntity(){return owner;}
    @Override public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}
    @Override public void setChannelLevel(int ignored){}
    @Override public boolean isChannelMemberActive(){return NativeScreenControlService.Host.super.isChannelMemberActive()&&!owner.inert();}

    @Override public void installControlData(int slot,NativeScreenControls next,boolean notify){
        if(slot!=0)return;
        data=next;
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
        if(tag.contains(KEY)&&!tag.contains(KEY,Tag.TAG_COMPOUND))return false;
        if(tag.contains(KEY,Tag.TAG_COMPOUND)){
            var raw=tag.getCompound(KEY);
            if(!(raw.get("Rows") instanceof net.minecraft.nbt.ListTag rows)
                ||!rows.isEmpty()&&rows.getElementType()!=Tag.TAG_COMPOUND
                ||rows.size()>CAPACITY||!NativeScreenControls.validConfiguration(raw))return false;
            data=NativeScreenControls.read(raw,CAPACITY);
        }else data=new NativeScreenControls(true,"",List.of());
        if(!fromClientUpdate)service.savedChanged();
        return true;
    }

    void write(CompoundTag tag,boolean item){
        if(item)tag.put(KEY,data.configuration());
        else{
            var saved=tag.getCompound(KEY).copy();
            data.write(saved);
            tag.put(KEY,saved);
        }
    }

    public boolean configure(NativeScreenRowEdit edit){finishLoading();return service.configure(0,edit);}
    public boolean editing(){return service.editing();}
    void use(int row,int segment){
        if(row<0||row>=data.rows().size())return;
        finishLoading();
        if(data.rows().get(row).slider())service.select(0,row,segment);
        else service.toggle(0,row);
    }

    public static NativeYokeGeometry.Face face(Direction facing){return FACES.get(facing);}
    record Pick(BlockHitResult hit,int row,int segment){}
    static Pick pick(BlockPos root,Direction facing,BlockPos clicked,Vec3 start,Vec3 end,int rows){
        Vec3 origin=Vec3.atLowerCornerOf(root);
        var sample=face(facing).sample(start.subtract(origin),end.subtract(origin),Math.max(1,rows));
        if(sample==null)return null;
        var point=start.lerp(end,sample.time());
        if(!BlockPos.containing(point).equals(clicked))return null;
        return new Pick(new BlockHitResult(point,Direction.UP,clicked,false),rows==0?-1:sample.row(),sample.sliderStep());
    }

    private static Map<Direction,NativeYokeGeometry.Face> faces(){
        String path="/assets/vandorlabs/cockpits/cockpit/surfaces.json";
        try(var stream=NativeCockpitTouch.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing authored Cockpit surfaces");
            var list=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("surfaces");
            for(var entry:list){
                var face=entry.getAsJsonObject();
                if(!face.get("id").getAsString().equals("touch_right_keyboard"))continue;
                var result=new EnumMap<Direction,NativeYokeGeometry.Face>(Direction.class);
                for(var direction:Direction.Plane.HORIZONTAL)result.put(direction,NativeYokeGeometry.authoredFace(face,direction));
                return result;
            }
            throw new IllegalStateException("Missing right Cockpit touch panel");
        }catch(java.io.IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}
