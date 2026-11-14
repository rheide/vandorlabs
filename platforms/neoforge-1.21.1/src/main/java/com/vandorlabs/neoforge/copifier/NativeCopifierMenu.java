package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.items.*;
import com.vandorlabs.neoforge.NativeHeldToolMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.component.CustomData;

/** Inactive options session: immediate settings edits, exact held object and original selected slot. */
public final class NativeCopifierMenu extends NativeHeldToolMenu {
    private static final DuplifierOptionMask MASK=new DuplifierOptionMask(DuplifierOptionCatalog.size());
    public NativeCopifierMenu(MenuType<NativeCopifierMenu> type,int id,Inventory inventory){super(type,id,inventory,InteractionHand.MAIN_HAND,stack->!stack.isEmpty()&&stack.getItem() instanceof NativeCopifierItem);}
    public boolean configure(ServerPlayer player,long[] words,boolean connected,boolean ignore){return configure(player,words,connected,ignore,false);}
    public boolean configure(ServerPlayer player,long[] words,boolean connected,boolean ignore,boolean respect){
        if(!player.getServer().isSameThread()||player.containerMenu!=this||!stillValid(player)||!MASK.valid(words))return false;
        var root=heldStack().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();root=NativeCopifierOptions.withMask(root,words);root=NativeCopifierOptions.withConnected(root,connected);root=NativeCopifierOptions.withIgnoreDirectionality(root,ignore);root=NativeCopifierOptions.withRespectJoins(root,respect);heldStack().set(DataComponents.CUSTOM_DATA,CustomData.of(root));synchronizeTool(player);return true;
    }
}
