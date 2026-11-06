package com.vandorlabs.neoforge.mixin;
import net.neoforged.neoforge.attachment.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Exact platform serialization capability; preflight never executes third-party attachment codecs. */
@Mixin(value=AttachmentType.class,remap=false)
public interface NativeAttachmentTypeAccess {
 @Accessor("serializer") IAttachmentSerializer<?,?> vandorlabs$serializer();
}
