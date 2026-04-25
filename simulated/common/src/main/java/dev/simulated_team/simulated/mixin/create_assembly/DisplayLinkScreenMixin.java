package dev.simulated_team.simulated.mixin.create_assembly;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkScreen;
import dev.simulated_team.simulated.mixin.accessor.DisplayLinkBlockEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisplayLinkScreen.class)
public class DisplayLinkScreenMixin {
    @Shadow private DisplayLinkBlockEntity blockEntity;

    @Inject(method = "init", at = @At("TAIL"))
    private void simulated$printDebugInfo(final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        final Level blockEntityLevel = this.blockEntity.getLevel();
        final Level clientLevel = minecraft.level;
        final BlockPos targetOffset = ((DisplayLinkBlockEntityAccessor) this.blockEntity).simulated$getTargetOffset();
        final BlockPos targetPos = this.blockEntity.getTargetPosition();

        player.displayClientMessage(Component.literal("[DL Debug] pos=%s offset=%s target=%s"
                .formatted(this.blockEntity.getBlockPos(), targetOffset, targetPos)), false);
        player.displayClientMessage(Component.literal("[DL Debug] beLevel=%s dim=%s sublevel=%s"
                .formatted(levelName(blockEntityLevel), dimensionName(blockEntityLevel), isSubLevelName(blockEntityLevel))), false);
        player.displayClientMessage(Component.literal("[DL Debug] mcLevel=%s dim=%s"
                .formatted(levelName(clientLevel), dimensionName(clientLevel))), false);
        player.displayClientMessage(Component.literal("[DL Debug] beLevel block=%s"
                .formatted(blockName(blockEntityLevel, targetPos))), false);
        player.displayClientMessage(Component.literal("[DL Debug] mcLevel block=%s"
                .formatted(blockName(clientLevel, targetPos))), false);
    }

    private static String levelName(final Level level) {
        return level == null ? "null" : level.getClass().getName();
    }

    private static String dimensionName(final Level level) {
        if (level == null) {
            return "null";
        }

        final ResourceKey<Level> dimension = level.dimension();
        return dimension == null ? "null" : dimension.location().toString();
    }

    private static String blockName(final Level level, final BlockPos pos) {
        if (level == null) {
            return "null-level";
        }

        final BlockState state = level.getBlockState(pos);
        return state.getBlock().builtInRegistryHolder().key().location().toString();
    }

    private static boolean isSubLevelName(final Level level) {
        return level != null && level.getClass().getName().contains("SubLevel");
    }
}
