package com.vandorlabs.items;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.redstone.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraftforge.fml.relauncher.*;
import java.util.List;

/** The item owns channel selection; a host owns the actual signal and its timing. */
public final class ItemRedstoneTransmitter extends Item {
    public static final String[] VARIANTS={"short","medium","long","infinite"};
    public static final int[] RANGES={16,64,256,0};
    public ItemRedstoneTransmitter(){setRegistryName(VandorLabs.MODID,"redstone_transmitter");setUnlocalizedName("vandorlabs.redstone_transmitter");setHasSubtypes(true);setMaxStackSize(1);setCreativeTab(VandorLabs.VANDOR_LABS_TAB);}
    public static boolean valid(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof ItemRedstoneTransmitter&&stack.getMetadata()>=0&&stack.getMetadata()<RANGES.length;}
    public static int range(ItemStack stack){return valid(stack)?RANGES[stack.getMetadata()]:-1;}
    public static ChannelList channels(ItemStack stack){return valid(stack)&&stack.hasTagCompound()?ChannelData.read(stack.getTagCompound(),0):ChannelList.EMPTY;}
    public static void configure(ItemStack stack,ChannelList channels){if(!valid(stack))throw new IllegalArgumentException("Invalid transmitter");if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());ChannelData.write(stack.getTagCompound(),channels);}
    @Override public void getSubItems(net.minecraft.creativetab.CreativeTabs tab,NonNullList<ItemStack> list){if(isInCreativeTab(tab))for(int i=0;i<4;i++)list.add(new ItemStack(this,1,i));}
    @Override public String getUnlocalizedName(ItemStack stack){int meta=stack.getMetadata();return "item.vandorlabs.redstone_transmitter."+VARIANTS[meta>=0&&meta<4?meta:0];}
    @Override public String getItemStackDisplayName(ItemStack stack){ChannelList list=channels(stack);return super.getItemStackDisplayName(stack)+(list.isEmpty()?"":" ["+list+"]");}
    @Override @SideOnly(Side.CLIENT) public void addInformation(ItemStack stack,World world,List<String> lines,ITooltipFlag flag){int range=range(stack);lines.add(range==0?"Range: entire dimension":"Range: "+range+" blocks (sphere)");lines.add("Shift-right-click: select channels");lines.add("Right-click: toggle nearby linked controls");}
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player,World world,net.minecraft.util.math.BlockPos pos,EnumFacing face,float x,float y,float z,EnumHand hand){onItemRightClick(world,player,hand);return EnumActionResult.SUCCESS;}
    @Override public ActionResult<ItemStack> onItemRightClick(World world,EntityPlayer player,EnumHand hand){
        ItemStack stack=player.getHeldItem(hand);
        if(!world.isRemote){if(player.isSneaking())player.openGui(VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TRANSMITTER_ITEM,world,hand==EnumHand.MAIN_HAND?0:1,0,0);else RedstoneChannels.manualToggle(player,stack);}
        return new ActionResult<>(EnumActionResult.SUCCESS,stack);
    }
}
