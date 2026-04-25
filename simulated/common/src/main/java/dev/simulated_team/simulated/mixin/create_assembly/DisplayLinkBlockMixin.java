package dev.simulated_team.simulated.mixin.create_assembly;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DisplayLinkBlock.class, priority = 2000)
public class DisplayLinkBlockMixin {

    @Inject(method = "afterMove", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulated$keepOriginalTargetOffset(final ServerLevel oldLevel,
                                                    final ServerLevel newLevel,
                                                    final BlockState state,
                                                    final BlockPos oldPos,
                                                    final BlockPos newPos,
                                                    final CallbackInfo ci) {
        ci.cancel();
    }
}
