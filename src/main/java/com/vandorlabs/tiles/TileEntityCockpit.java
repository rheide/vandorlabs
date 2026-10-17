package com.vandorlabs.tiles;

import com.vandorlabs.cockpit.CockpitData;
import com.vandorlabs.redstone.*;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** One persistent controller owns every display and independent touch/throttle output. */
public class TileEntityCockpit extends TileEntityVehicleController {
    private String design="cockpit_a";
    public final List<Display> displays=new ArrayList<>();
    public final List<Control> controls=new ArrayList<>();
    public TileEntityCockpit(){initializeControls();}
    public TileEntityCockpit(String design){this();this.design=design;prepare();}
    public String design(){return design;}
    public CockpitData data(){return CockpitData.get(design);}
    public EnumFacing facing(){return world==null?EnumFacing.NORTH:world.getBlockState(pos).getValue(com.vandorlabs.blocks.BlockVandorDirectional.FACING);}
    public double seatHeight(){return data().riderY+.725;}
    private void prepare(){
        if(!displays.isEmpty())return;
        for(CockpitData.Surface surface:data().surfaces){
            Display display=new Display(surface.id,surface.role);displays.add(display);
            if("touch".equals(surface.role))for(int row=0;row<3;row++){Control c=new Control(surface.id+"/"+row);controls.add(c);}
        }
        for(String part:data().controls){Control c=new Control(part);c.kind=1;controls.add(c);}
    }
    public Control control(String id){prepare();for(Control c:controls)if(c.id.equals(id))return c;return null;}
    public Display display(String id){prepare();for(Display d:displays)if(d.id.equals(id))return d;return null;}
    public void changed(){markDirty();if(world!=null)world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}
    private boolean ordinary(){return world!=null && !world.isRemote && !(world instanceof VehicleWorld);}
    @Override public void onLoad(){super.onLoad();prepare();if(ordinary())for(Control c:controls)RedstoneChannels.register(c);}
    @Override public void invalidate(){if(ordinary())for(Control c:controls)RedstoneChannels.unregister(c);super.invalidate();}
    @Override public void onChunkUnload(){if(ordinary())for(Control c:controls)RedstoneChannels.unregister(c);super.onChunkUnload();}
    public boolean expire(long now){boolean changed=false;for(Control c:controls)if(c.until>0 && now>=c.until){c.until=0;c.level=0;if(ordinary())RedstoneChannels.inputChanged(c);changed=true;}if(changed)changed();return changed;}
    public void updatePower(){boolean dirty=false;for(Display d:displays){boolean active=d.gate==0 || (RedstoneChannels.level(world,d.gate)>0)!=d.inverted;if(active!=d.active){d.active=active;dirty=true;}}if(dirty)changed();}
    @Override public void update(){prepare();if(world==null || world.isRemote)return;expire(world.getTotalWorldTime());updatePower();if(data().seat!=null)super.update();}
    public boolean use(Vec3d start,Vec3d end){
        prepare();start=CockpitData.unrotate(start,facing());end=CockpitData.unrotate(end,facing());
        CockpitData.Surface nearest=null;double[] uv=null;
        for(CockpitData.Surface surface:data().surfaces)if("touch".equals(surface.role) && display(surface.id).active){double[] hit=surface.hit(start,end);if(hit!=null && (uv==null || hit[2]<uv[2])){nearest=surface;uv=hit;}}
        String throttle=null;double distance=uv==null?Double.POSITIVE_INFINITY:start.squareDistanceTo(start.add(end.subtract(start).scale(uv[2])));
        for(Map.Entry<String,Vec3d> part:data().pivots.entrySet()){
            Vec3d p=part.getValue();RayTraceResult hit=new AxisAlignedBB(p.x-.11,p.y-.05,p.z-.26,p.x+.11,p.y+.24,p.z+.26).calculateIntercept(start,end);
            if(hit!=null && start.squareDistanceTo(hit.hitVec)<distance){distance=start.squareDistanceTo(hit.hitVec);throttle=part.getKey();}
        }
        if(throttle!=null){Control c=control(throttle);c.activate(((c.level/5+1)%4)/3D);return true;}
        if(nearest!=null){Control c=control(nearest.id+"/"+Math.min(2,(int)((1-uv[1])*3)));c.activate(uv[0]);return true;}
        return false;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag){
        super.writeToNBT(tag);tag.setString("CockpitDesign",design);prepare();
        NBTTagList screens=new NBTTagList();for(Display d:displays)screens.appendTag(d.write());tag.setTag("CockpitDisplays",screens);
        NBTTagList outputs=new NBTTagList();for(Control c:controls)outputs.appendTag(c.write());tag.setTag("CockpitControls",outputs);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag){
        if(ordinary())for(Control c:controls)RedstoneChannels.unregister(c);
        super.readFromNBT(tag);design=tag.hasKey("CockpitDesign",8)?tag.getString("CockpitDesign"):design;CockpitData.get(design);
        displays.clear();controls.clear();prepare();
        NBTTagList screens=tag.getTagList("CockpitDisplays",10);for(int i=0;i<Math.min(32,screens.tagCount());i++){NBTTagCompound saved=screens.getCompoundTagAt(i);Display d=display(saved.getString("Id"));if(d!=null)d.read(saved);}
        NBTTagList outputs=tag.getTagList("CockpitControls",10);for(int i=0;i<Math.min(64,outputs.tagCount());i++){NBTTagCompound saved=outputs.getCompoundTagAt(i);Control c=control(saved.getString("Id"));if(c!=null)c.read(saved);}
        initializeControls();if(ordinary() && world.getTileEntity(pos)==this)for(Control c:controls)RedstoneChannels.register(c);
    }
    @Override public AxisAlignedBB getRenderBoundingBox(){return CockpitData.rotate(data().bounds,facing()).offset(pos).grow(.05);}
    public final class Control implements RedstoneChannelMember {
        public final String id;
        public String label="";
        public ChannelList channels=ChannelList.EMPTY;
        public int kind,level;
        public long until;
        Control(String id){this.id=id;label=id.substring(id.lastIndexOf('/')+1);}
        public TileEntity channelTile(){return TileEntityCockpit.this;}
        public int getRedstoneChannel(){return channels.first();}
        public ChannelList getRedstoneChannels(){return channels;}
        public void setRedstoneChannel(int channel){setRedstoneChannels(ChannelList.of(channel));}
        public void setRedstoneChannels(ChannelList next){ChannelList before=channels;channels=next;if(ordinary())RedstoneChannels.channelChanged(this,before);changed();}
        public boolean hasLocalRedstoneSignal(){return level>0;}
        public int localSignalLevel(int channel){return level;}
        public void setChannelSignal(boolean ignored){}
        public void activate(double column){
            level=kind==1?Math.max(0,Math.min(15,(int)Math.round(column*3)*5)):kind==2?15:level==0?15:0;
            until=kind==2?world.getTotalWorldTime()+20:0;if(ordinary())RedstoneChannels.inputChanged(this);changed();
        }
        public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("Id",id);tag.setString("Label",label);ChannelData.write(tag,channels);tag.setInteger("Kind",kind);tag.setInteger("Level",level);tag.setLong("Until",until);return tag;}
        public void read(NBTTagCompound tag){label=tag.getString("Label");if(label.length()>32)label=label.substring(0,32);channels=ChannelData.read(tag,0);kind=Math.max(0,Math.min(2,tag.getInteger("Kind")));level=Math.max(0,Math.min(15,tag.getInteger("Level")));until=Math.max(0,tag.getLong("Until"));}
    }
    public static final class Display {
        public final String id,role;
        public int texture=-1,speed=1,gate;
        public boolean animated=true,inverted,active=true;
        Display(String id,String role){this.id=id;this.role=role;}
        public NBTTagCompound write(){NBTTagCompound tag=new NBTTagCompound();tag.setString("Id",id);tag.setInteger("Texture",texture);tag.setInteger("Speed",speed);tag.setInteger("Gate",gate);tag.setBoolean("Animated",animated);tag.setBoolean("Inverted",inverted);tag.setBoolean("Active",active);return tag;}
        public void read(NBTTagCompound tag){texture=tag.getInteger("Texture");if(texture<-1 || texture>=ScreenHousingTextures.IDS.length)texture=-1;speed=Math.max(1,Math.min(16,tag.getInteger("Speed")));gate=Math.max(0,Math.min(65535,tag.getInteger("Gate")));animated=tag.getBoolean("Animated");inverted=tag.getBoolean("Inverted");active=gate==0 || tag.getBoolean("Active");}
    }
}
