package com.vandorlabs.neoforge.builders.client;
import com.vandorlabs.neoforge.builders.NativeBuilderEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Cosmetic four-tick easing; repeated publications cannot restart motion or consume inputs. */
public final class NativeBuilderProgressMotion {
 public static final class State {
  private NativeBuilderEntity.Visual publication;private java.util.UUID identity;private int mode,building;private boolean initialized,processing,tail;private float observed,from,target;private double started;private ItemStack input=ItemStack.EMPTY,template=ItemStack.EMPTY;
  public float progress;public boolean visible;public ItemStack input(){return input;}public ItemStack template(){return template;}
  private float at(double now){float t=(float)Math.clamp((now-started)/4,0,1);t=t*t*(3-2*t);return from+(target-from)*t;}
  public void observe(NativeBuilderEntity.Visual visual,java.util.UUID key,double now){float next=Math.clamp(visual.progress(),0,1);boolean fresh=publication!=visual;boolean changed=!initialized||!Objects.equals(identity,key)||mode!=visual.mode()||building!=visual.building()||fresh&&visual.processing()&&(!processing||!ItemStack.isSameItemSameComponents(input,visual.input())||!ItemStack.isSameItemSameComponents(template,visual.template())||next+.001F<observed);
   if(changed){identity=key;mode=visual.mode();building=visual.building();from=target=next;started=now;input=visual.input().copy();template=visual.template().copy();initialized=true;tail=false;}
   else if(processing&&!visual.processing()&&mode==1&&visual.input().isEmpty()){from=at(now);target=1;started=now;tail=true;}
   else if(Float.floatToIntBits(next)!=Float.floatToIntBits(observed)&&visual.processing()){from=at(now);target=next;started=now;tail=false;}
   else if(fresh&&!visual.processing()&&(!tail||!visual.input().isEmpty())){tail=false;from=target=next;started=now;input=visual.input().copy();template=visual.template().copy();}
   progress=at(now);visible=visual.processing()||visual.progress()>0||mode==1&&!visual.input().isEmpty()||tail&&now-started<4;processing=visual.processing();observed=next;publication=visual;
  }
 }
 private static final Map<Object,LinkedHashMap<net.minecraft.core.BlockPos,State>> WORLDS=new WeakHashMap<>();
 public static State sample(NativeBuilderEntity tile,NativeBuilderEntity.Visual visual,double now){var states=WORLDS.computeIfAbsent(tile.getLevel(),ignored->new LinkedHashMap<>(16,.75F,true));var state=states.get(tile.getBlockPos());if(state==null){state=new State();states.put(tile.getBlockPos().immutable(),state);if(states.size()>256)states.remove(states.keySet().iterator().next());}state.observe(visual,tile.key(),now);return state;}
 public static void clear(){WORLDS.clear();}
 private NativeBuilderProgressMotion(){}
}
