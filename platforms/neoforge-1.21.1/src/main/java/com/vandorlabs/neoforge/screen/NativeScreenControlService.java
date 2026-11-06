package com.vandorlabs.neoforge.screen;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.persistence.SignalControlData;
import com.vandorlabs.neoforge.redstone.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;
/** Loaded standalone row sources. Cosmetic rendering never polls or serializes these mutable authorities. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeScreenControlService {
 private static final Map<ServerLevel,Set<NativeScreenControlService>> ACTIVE=new IdentityHashMap<>();
 private static final Set<ServerLevel> CLOSED=Collections.newSetFromMap(new WeakHashMap<>());
 private final NativeScreenEntity owner;private final List<List<Row>> rows=new ArrayList<>(2);private final NativeScreenControls[] headers=new NativeScreenControls[2];private final Set<Row> registered=Collections.newSetFromMap(new IdentityHashMap<>());private boolean initialized;
 NativeScreenControlService(NativeScreenEntity owner){this.owner=owner;for(int slot=0;slot<2;slot++){headers[slot]=owner.controls(slot);rows.add(readRows(slot,headers[slot]));}}
 private List<Row> readRows(int slot,NativeScreenControls data){List<Row> result=new ArrayList<>();for(var row:data.rows())result.add(new Row(slot,row));return result;}
 private ServerLevel level(){if(!(owner.getLevel() instanceof ServerLevel world))return null;if(!world.getServer().isSameThread())throw new IllegalStateException("Screen controls require server thread");return CLOSED.contains(world)||!NativeRedstoneChannels.open(world)?null:world;}
 private boolean live(){return level()!=null&&owner.isChannelMemberActive();}
 void savedChanged(){boolean resume=initialized;List<Row> old=new ArrayList<>(registered);for(int slot=0;slot<2;slot++){headers[slot]=owner.controls(slot);rows.set(slot,readRows(slot,headers[slot]));}if(resume&&live())mutate(()->{registerCurrent();withdraw(old);});else{withdraw(old);initialized=false;}if(resume&&live())owner.reconcileControlOutput();arm();}
 void finishLoading(){if(!live())return;initialized=true;mutate(()->{registerCurrent();owner.reconcileControlOutput();});arm();}
 private void mutate(Runnable operation){ServerLevel world=level();if(world!=null)NativeRedstoneChannels.batch(world,operation);}
 private void registerCurrent(){for(int slot=0;slot<2;slot++)if(headers[slot].enabled())for(Row row:rows.get(slot)){registered.add(row);NativeRedstoneChannels.channelsChanged(row);}}
 private void withdraw(Collection<Row> old){for(Row row:old){registered.remove(row);if(level()!=null)NativeRedstoneChannels.unregister(row);}}
 void stop(){initialized=false;disarm();withdraw(new ArrayList<>(registered));}
 private void publish(int slot,boolean notify){List<NativeScreenControls.Row> values=new ArrayList<>();for(Row row:rows.get(slot))values.add(row.snapshot());NativeScreenControls next=new NativeScreenControls(headers[slot].enabled(),headers[slot].title(),values);headers[slot]=next;owner.installControlData(slot,next,notify);}
 private boolean valid(int slot,int index){return live()&&slot>=0&&slot<2&&headers[slot].enabled()&&index>=0&&index<rows.get(slot).size();}
 boolean toggle(int slot,int index){if(!valid(slot,index))return false;Row row=rows.get(slot).get(index);if(row.channels.isEmpty()||row.slider)return false;if(row.pulse)return pulse(slot,index);boolean all=true;ServerLevel world=level();for(int i=0;i<row.channels.size();i++)if(NativeRedstoneChannels.level(world,owner.getBlockPos(),row.channels.get(i))==0){all=false;break;}NativeRedstoneChannels.latchLevelChanged(row,all?0:15);return true;}
 boolean select(int slot,int index,int segment){if(!valid(slot,index)||segment<0||segment>3)return false;Row row=rows.get(slot).get(index);if(!row.slider)return false;NativeRedstoneChannels.latchLevelChanged(row,SignalControlData.levelForStep(segment,row.min,row.max));return true;}
 boolean pulse(int slot,int index){if(!valid(slot,index))return false;Row row=rows.get(slot).get(index);if(!row.pulse||row.channels.isEmpty())return false;row.until=level().getGameTime()+20;mutate(()->{publish(slot,true);NativeRedstoneChannels.outputChanged(row);});arm();return true;}
 boolean setEnabled(int slot,boolean enabled){if(!live()||slot<0||slot>1)return false;if(headers[slot].enabled()==enabled)return true;var old=headers[slot];headers[slot]=new NativeScreenControls(enabled,old.title(),old.rows());mutate(()->{publish(slot,false);if(enabled)registerCurrent();else withdraw(new ArrayList<>(registered.stream().filter(row->row.slot==slot).toList()));publish(slot,true);});arm();return true;}
 boolean expire(long now){if(!live())return false;boolean[] pending={false};mutate(()->{for(int slot=0;slot<2;slot++)if(headers[slot].enabled())for(Row row:rows.get(slot))if(row.until>0){if(now>=row.until){row.until=0;publish(slot,true);NativeRedstoneChannels.outputChanged(row);}else pending[0]=true;}});return pending[0];}
 private boolean hasPulses(){for(int slot=0;slot<2;slot++)if(headers[slot].enabled())for(Row row:rows.get(slot))if(row.pulse&&row.until>0)return true;return false;}
 private void disarm(){if(owner.getLevel() instanceof ServerLevel world){Set<NativeScreenControlService> active=ACTIVE.get(world);if(active!=null){active.remove(this);if(active.isEmpty())ACTIVE.remove(world);}}}
 private void arm(){ServerLevel world=level();if(world!=null&&initialized&&owner.isChannelMemberActive()&&hasPulses())ACTIVE.computeIfAbsent(world,unused->Collections.newSetFromMap(new IdentityHashMap<>())).add(this);else disarm();}
 static int activeCount(ServerLevel world){Set<NativeScreenControlService> active=ACTIVE.get(world);return active==null?0:active.size();}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;Set<NativeScreenControlService> services=ACTIVE.get(world);if(services==null||services.isEmpty())return;for(var service:new ArrayList<>(services))if(!service.initialized||!service.owner.isChannelMemberActive()||!service.expire(world.getGameTime()))service.disarm();}
 @SubscribeEvent public static void load(LevelEvent.Load event){if(event.getLevel() instanceof ServerLevel world)CLOSED.remove(world);}
 @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world){CLOSED.add(world);ACTIVE.remove(world);}}
 private final class Row implements NativeChannelLatch {
  final int slot;final String label;final ChannelList channels;final boolean slider,pulse;final int min,max;final Map<Integer,Integer> levels;ChannelList latched;int value;boolean active;long until;
  Row(int slot,NativeScreenControls.Row saved){this.slot=slot;label=saved.label();channels=saved.channels();slider=saved.slider();pulse=saved.pulse();min=saved.min();max=saved.max();levels=new HashMap<>(saved.levels());latched=saved.latched();value=saved.level();active=saved.active();until=saved.pulseUntil();}
  NativeScreenControls.Row snapshot(){return new NativeScreenControls.Row(label,channels,slider,pulse,min,max,value,levels,latched,active,until);}
  public BlockEntity channelBlockEntity(){return owner;}public ChannelList getRedstoneChannels(){return channels;}public ChannelList transmittedChannels(){return channels;}public int transmissionRange(){return owner.transmission().range();}
  public boolean isChannelMemberActive(){return owner.isChannelMemberActive()&&registered.contains(this)&&headers[slot].enabled();}
  public int localSignalLevel(int channel){return pulse?until>0&&owner.getLevel().getGameTime()<until?15:0:latchedLevel(channel);}
  public boolean isChannelLatch(){return !pulse;}public boolean latchOn(){return !channels.isEmpty()&&latched.containsAll(channels);}public ChannelList latchedChannels(){return latched;}public int latchedLevel(int channel){return levels.getOrDefault(channel,latched.contains(channel)?15:0);}
  public void applyLinkedLatch(boolean on){applyLinkedChannels(on?channels:ChannelList.EMPTY);}public void applyLinkedChannels(ChannelList next){if(!latched.equals(next)){latched=next;publish(slot,false);}}
  public void applyLinkedLevels(Map<Integer,Integer> next){ChannelList active=NativeChannelLatch.activeChannels(next);if(!levels.equals(next)||!latched.equals(active)){levels.clear();levels.putAll(next);latched=active;publish(slot,false);}}
  public void setChannelLevel(int level){if(!rows.get(slot).contains(this))return;boolean changed=false;if(slider&&value!=level){value=level;changed=true;}boolean all=!channels.isEmpty();ServerLevel world=NativeScreenControlService.this.level();if(world==null)return;for(int i=0;i<channels.size();i++)if(NativeRedstoneChannels.level(world,owner.getBlockPos(),channels.get(i))==0){all=false;break;}if(active!=all){active=all;changed=true;}if(changed)publish(slot,true);}
 }
}
