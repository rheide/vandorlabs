package com.vandorlabs.neoforge.mixin;
import com.vandorlabs.neoforge.NativePlatformComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Observes the one actual platform decode only while an explicit guarded block-entity load is active. */
@Mixin(value=AttachmentHolder.class,remap=false)
public abstract class NativeAttachmentDecodeMixin {
 @Redirect(method="deserializeAttachments",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/attachment/IAttachmentSerializer;read(Lnet/neoforged/neoforge/attachment/IAttachmentHolder;Lnet/minecraft/nbt/Tag;Lnet/minecraft/core/HolderLookup$Provider;)Ljava/lang/Object;"))
 private Object vandorlabs$guardRead(IAttachmentSerializer<Tag,?> codec,IAttachmentHolder owner,Tag tag,HolderLookup.Provider registries)throws Exception{return NativePlatformComponents.readAttachment(codec,owner,tag,registries);}
}
