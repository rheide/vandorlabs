package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.credentials.CredentialCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Stable ID and stack components; data-chip programming/vehicle behavior remains separate. */
public final class NativeCredentialItem extends Item {
    private final boolean chip;
    public NativeCredentialItem(boolean chip){super(new Properties());this.chip=chip;}
    public boolean chip(){return chip;}
    @Override public Component getName(ItemStack stack){if(chip){String type=NativeChipTypes.type(stack);if(type.equals("catalog"))return Component.literal("Catalog Chip (Master)");if(type.equals("navigation"))return Component.literal("Navigation Chip (blank)");if(type.equals("vehicle"))return Component.literal("Vehicle Data Chip");if(type.equals("journal"))return Component.literal("Journal Data Chip");}return Component.translatable("item.vandorlabs."+(chip?"data_chip":"access_card")+"."+CredentialCatalog.companyName(NativeCredentialItems.company(stack)));}
}
