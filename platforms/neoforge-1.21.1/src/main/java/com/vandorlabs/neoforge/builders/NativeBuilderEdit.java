package com.vandorlabs.neoforge.builders;
import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
/** Only bounded production and receiving choices; item ownership uses ordinary menu slots. */
public record NativeBuilderEdit(int window,BlockPos pos,int operation,int value,ChannelList channels){
 public NativeBuilderEdit{if(window<0||pos==null||channels==null||operation<0||operation>1||value<0||value>(operation==0?Math.max(NativeBuilderProducts.MAX_TOWER_PRODUCT,NativeBuilderProducts.GANTRY.size()-1):2)||operation==0&&!channels.isEmpty())throw new IllegalArgumentException("Builder choices");pos=pos.immutable();}
}
