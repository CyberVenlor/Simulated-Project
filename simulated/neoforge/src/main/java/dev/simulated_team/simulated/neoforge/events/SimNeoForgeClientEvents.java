package dev.simulated_team.simulated.neoforge.events;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionEngineSoundManager;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterItemBindHandler;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.neoforge.service.SimpleResourceManagerRegistryService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Simulated.MOD_ID, value = Dist.CLIENT)
public class SimNeoForgeClientEvents {

	@SubscribeEvent
	public static void preClientTick(final ClientTickEvent.Pre event) {
		SimulatedCommonClientEvents.preClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void postClientTick(final ClientTickEvent.Post event) {
		SimulatedCommonClientEvents.postClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void postRenderGui(final RenderGuiEvent.Post event) {
		SimulatedCommonClientEvents.renderOverlays(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
	}

	@SubscribeEvent
	public static void keyInput(final InputEvent.Key event) {
		SimulatedCommonClientEvents.onAfterKeyPress(event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers());
	}

	@SubscribeEvent
	public static void registerClientCommands(final RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("simulated")
				.then(Commands.literal("engine_audio")
						.executes(context -> {
							final AnalogTransmissionEngineSoundManager.AudioProfile profile = AnalogTransmissionEngineSoundManager.getActiveProfile();
							context.getSource().sendSuccess(() -> Component.literal("Analog transmission engine audio: " + profile.displayName()), false);
							return 1;
						})
						.then(Commands.literal("bac_mono").executes(context -> setEngineAudioProfile(context.getSource(), "bac_mono")))
						.then(Commands.literal("ferrari_458").executes(context -> setEngineAudioProfile(context.getSource(), "ferrari_458")))
						.then(Commands.literal("procar").executes(context -> setEngineAudioProfile(context.getSource(), "procar")))));
	}

	private static int setEngineAudioProfile(final CommandSourceStack source, final String profileId) {
		if (!AnalogTransmissionEngineSoundManager.setActiveProfile(profileId)) {
			source.sendFailure(Component.literal("Unknown analog transmission engine audio profile: " + profileId));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Analog transmission engine audio: " + AnalogTransmissionEngineSoundManager.getActiveProfile().displayName()), false);
		return 1;
	}

	@SubscribeEvent
	public static void postMouseButtonInput(final InputEvent.MouseButton.Post event) {
		SimulatedCommonClientEvents.onAfterMouseInput(event.getButton(), event.getModifiers(), event.getAction());
	}

	@SubscribeEvent
	public static void playerInteractRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
		if (event.getEntity().isLocalPlayer()) {
			final InteractionResult res = SimulatedCommonClientEvents.onRightClickBlock(event.getEntity(), event.getHand(), event.getPos(), event.getHitVec());

			if (res != null) {
				event.setCancellationResult(res);
				event.setCanceled(true);
				return;
			}
		}

		if (event.getItemStack().is(SimItems.HONEY_GLUE)) {
			event.setUseBlock(TriState.FALSE);
			if (event.getLevel().isClientSide) {
				SimClickInteractions.HONEY_GLUE_MANAGER.selectPos(event.getPos(), event.getEntity(), event.getItemStack());
			}
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void itemTooltip(final ItemTooltipEvent event) {
		SimulatedCommonClientEvents.appendTooltip(event.getItemStack(), event.getFlags(), event.getEntity(), event.getToolTip());
	}

	@EventBusSubscriber(modid = Simulated.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static class ModBusEvents {

		@SubscribeEvent
		public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
			SimKeys.registerTo(event::register);
		}

		@SubscribeEvent
		public static void registerGuiLayers(final RegisterGuiLayersEvent event) {
			event.registerAbove(VanillaGuiLayers.HOTBAR, Simulated.path("linked_typewriter_binding"), LinkedTypewriterItemBindHandler.OVERLAY);
		}

		@SubscribeEvent
		public static void addReloadListener(final RegisterClientReloadListenersEvent event) {
			for (final PreparableReloadListener listener : SimpleResourceManagerRegistryService.LISTENERS) {
				event.registerReloadListener(listener);
			}
		}
	}
}
