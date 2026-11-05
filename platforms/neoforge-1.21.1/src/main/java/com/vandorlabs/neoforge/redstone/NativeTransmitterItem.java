package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** One registry ID, four persisted tier variants; channels use the unchanged int-array spelling. */
public final class NativeTransmitterItem extends Item {
    public static final String TIER="TransmitterTier",CHANNELS="RedstoneChannels";
    public static final int[] RANGES={16,64,256,0};
    private static final String[] NAMES={"Short","Medium","Long","Infinite"};
    public NativeTransmitterItem(){super(new Properties().stacksTo(1));}
    private static CompoundTag tag(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    public static int tier(ItemStack stack){if(stack.isEmpty()||!(stack.getItem() instanceof NativeTransmitterItem))return -1;CompoundTag tag=tag(stack);int tier=tag.getInt(TIER);return (!tag.contains(TIER)||tag.contains(TIER,net.minecraft.nbt.Tag.TAG_INT))&&tier>=0&&tier<4?tier:-1;}
    public static boolean valid(ItemStack stack){return tier(stack)>=0;}
    public static int range(ItemStack stack){int tier=tier(stack);return tier<0?-1:RANGES[tier];}
    public static ChannelList channels(ItemStack stack){if(!valid(stack))return ChannelList.EMPTY;try{return ChannelList.of(tag(stack).getIntArray(CHANNELS));}catch(IllegalArgumentException invalid){return ChannelList.EMPTY;}}
    public static ItemStack variant(Item item,int tier){if(tier<0||tier>3)throw new IllegalArgumentException("Invalid tier");ItemStack stack=new ItemStack(item);CompoundTag tag=new CompoundTag();tag.putInt(TIER,tier);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));stack.set(DataComponents.CUSTOM_MODEL_DATA,new net.minecraft.world.item.component.CustomModelData(tier));return stack;}
    public static void configure(ItemStack stack,ChannelList channels){if(!valid(stack))throw new IllegalArgumentException("Invalid transmitter");CompoundTag tag=tag(stack);tag.putIntArray(CHANNELS,channels.toArray());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
    @Override public Component getName(ItemStack stack){int tier=tier(stack);ChannelList channels=channels(stack);return Component.literal(NAMES[tier<0?0:tier]+" Redstone Transmitter"+(channels.isEmpty()?"":" ["+channels+"]"));}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> lines,TooltipFlag flags){int range=range(stack);lines.add(Component.literal(range==0?"Range: entire dimension":"Range: "+range+" blocks (sphere)"));lines.add(Component.literal("Shift-right-click: select channels"));lines.add(Component.literal("Right-click: toggle nearby linked controls"));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){return useOn(context);}
    @Override public InteractionResult useOn(UseOnContext context){Player player=context.getPlayer();if(player!=null)use(context.getLevel(),player,context.getHand());return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);if(player instanceof ServerPlayer server&&valid(stack)){
            if(player.isShiftKeyDown())server.openMenu(new SimpleMenuProvider((id,inventory,owner)->new TransmitterMenu(id,inventory,hand),Component.literal("Transmitter Channels")),buffer->buffer.writeEnum(hand));
            else NativeRedstoneChannels.manualToggle(server,stack);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
