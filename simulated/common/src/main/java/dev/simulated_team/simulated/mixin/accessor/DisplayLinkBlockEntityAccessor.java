package dev.simulated_team.simulated.mixin.accessor;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DisplayLinkBlockEntity.class)
public interface DisplayLinkBlockEntityAccessor {
    @Accessor("targetOffset")
    BlockPos simulated$getTargetOffset();
}
