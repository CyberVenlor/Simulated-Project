package dev.simulated_team.simulated.content.blocks.analog_transmission;

import dev.simulated_team.simulated.api.sound.SimSoundEntry;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class AnalogTransmissionEngineSoundManager {
    private static final int SCAN_INTERVAL = 20;
    private static final int SCAN_RADIUS = 32;

    private static final Map<BlockPos, EnumMap<FerrariSample, FerrariEngineSoundInstance>> ACTIVE_SOUNDS = new HashMap<>();
    private static ClientLevel activeLevel;
    private static int scanCooldown;
    private static int debugCooldown;

    public static void tick(final ClientLevel level, final LocalPlayer player) {
        if (activeLevel != level) {
            ACTIVE_SOUNDS.clear();
            activeLevel = level;
            scanCooldown = 0;
            debugCooldown = 0;
        }

        cleanupStoppedSounds();
        debugNearestTransmission(level, player);

        if (scanCooldown-- > 0) {
            return;
        }
        scanCooldown = SCAN_INTERVAL;

        final BlockPos center = player.blockPosition();
        final BlockPos min = center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
        final BlockPos max = center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);

        for (final BlockPos pos : BlockPos.betweenClosed(min, max)) {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AnalogTransmissionBlockEntity analogTransmission)) {
                continue;
            }
            if (analogTransmission.getFerrariEngineRpm() <= 0.0f) {
                continue;
            }

            playEngine(level, pos.immutable());
        }
    }

    private static void debugNearestTransmission(final ClientLevel level, final LocalPlayer player) {
        if (debugCooldown-- > 0) {
            return;
        }
        debugCooldown = 20;

        final BlockPos center = player.blockPosition();
        final BlockPos min = center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS);
        final BlockPos max = center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS);
        AnalogTransmissionBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (final BlockPos pos : BlockPos.betweenClosed(min, max)) {
            final BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof AnalogTransmissionBlockEntity analogTransmission)) {
                continue;
            }

            final double distance = player.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < nearestDistance) {
                nearest = analogTransmission;
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            player.displayClientMessage(Component.literal("[Analog Transmission Audio] no block within " + SCAN_RADIUS + " blocks"), false);
            return;
        }

        player.displayClientMessage(Component.literal(String.format(Locale.ROOT,
                "[Analog Transmission Audio] pos=%s signal=%d cmdGear=%d gear=%d audioGear=%d power=%.2f throttle=%.2f brake=%.2f rpm=%.2f theta=%.3f omega=%.3f driveTheta=%.3f driveOmega=%.3f",
                nearest.getBlockPos().toShortString(),
                nearest.getSignal(),
                nearest.getFerrariEngineGear(),
                nearest.getFerrariEngagedGear(),
                nearest.getFerrariAudioGear(),
                nearest.getFerrariEnginePowerInput(),
                nearest.getFerrariAudioThrottle(),
                nearest.getFerrariBrake(),
                nearest.getFerrariEngineRpm(),
                nearest.getFerrariEngineTheta(),
                nearest.getFerrariEngineOmega(),
                nearest.getFerrariDrivetrainTheta(),
                nearest.getFerrariDrivetrainOmega()
        )), false);
    }

    private static void playEngine(final ClientLevel level, final BlockPos pos) {
        final Minecraft minecraft = Minecraft.getInstance();
        final EnumMap<FerrariSample, FerrariEngineSoundInstance> samples =
                ACTIVE_SOUNDS.computeIfAbsent(pos, key -> new EnumMap<>(FerrariSample.class));

        for (final FerrariSample sample : FerrariSample.values()) {
            final FerrariEngineSoundInstance sound = samples.computeIfAbsent(sample,
                    key -> new FerrariEngineSoundInstance(level, pos, key.event.event(), level.getRandom(), key));

            if (!minecraft.getSoundManager().isActive(sound)) {
                minecraft.getSoundManager().play(sound);
            }
        }
    }

    private static void cleanupStoppedSounds() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Iterator<Map.Entry<BlockPos, EnumMap<FerrariSample, FerrariEngineSoundInstance>>> iterator = ACTIVE_SOUNDS.entrySet().iterator();

        while (iterator.hasNext()) {
            final Map.Entry<BlockPos, EnumMap<FerrariSample, FerrariEngineSoundInstance>> entry = iterator.next();
            entry.getValue().values().removeIf(sound -> sound.isStopped() || !minecraft.getSoundManager().isActive(sound));
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private enum FerrariSample {
        ON_LOW(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_ON_LOW, 5300.0f, 1.5f),
        ON_HIGH(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_ON_HIGH, 7700.0f, 2.5f),
        OFF_LOW(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_OFF_LOW, 6900.0f, 1.4f),
        OFF_HIGH(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_OFF_HIGH, 7900.0f, 1.6f),
        LIMITER(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_LIMITER, 8900.0f, 1.8f);

        private final SimSoundEntry event;
        private final float sampleRpm;
        private final float sampleVolume;

        FerrariSample(final SimSoundEntry event, final float sampleRpm, final float sampleVolume) {
            this.event = event;
            this.sampleRpm = sampleRpm;
            this.sampleVolume = sampleVolume;
        }
    }

    private static class FerrariEngineSoundInstance extends AbstractTickableSoundInstance {
        private static final float SOFT_LIMITER_RPM = 8800.0f;
        private static final float LIMITER_RPM = 8900.0f;
        private static final float RPM_PITCH_FACTOR = 0.2f;

        private final ClientLevel level;
        private final BlockPos pos;
        private final FerrariSample sample;
        private int inactiveTicks;

        protected FerrariEngineSoundInstance(final ClientLevel level, final BlockPos pos, final SoundEvent event,
                                             final RandomSource random, final FerrariSample sample) {
            super(event, SoundSource.BLOCKS, random);
            this.level = level;
            this.pos = pos;
            this.sample = sample;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
            this.volume = 0.001f;
        }

        @Override
        public void tick() {
            final BlockEntity blockEntity = this.level.getBlockEntity(this.pos);
            if (!(blockEntity instanceof AnalogTransmissionBlockEntity analogTransmission) || analogTransmission.isRemoved()) {
                this.stop();
                return;
            }

            final float rpm = analogTransmission.getFerrariEngineRpm();
            final float throttle = analogTransmission.getFerrariAudioThrottle();

            final LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.distanceToSqr(Vec3.atCenterOf(this.pos)) > Mth.square(SCAN_RADIUS + 8.0f)) {
                this.stop();
                return;
            }

            if (rpm <= 0.0f) {
                this.volume = 0.0f;
                if (++this.inactiveTicks > 40) {
                    this.stop();
                }
                return;
            }

            this.inactiveTicks = 0;
            this.pitch = getRpmPitch(rpm, this.sample.sampleRpm);
            this.volume = getSampleGain(this.sample, rpm, throttle) * 0.08f;
        }

        private static float getSampleGain(final FerrariSample sample, final float rpm, final float throttle) {
            final CrossFade rpmFade = crossFade(rpm, 3000.0f, 6500.0f);
            final CrossFade throttleFade = crossFade(throttle, 0.0f, 1.0f);
            final float limiterGain = ratio(rpm, SOFT_LIMITER_RPM * 0.93f, LIMITER_RPM);

            return switch (sample) {
                case ON_LOW -> throttleFade.gain1 * rpmFade.gain2 * sample.sampleVolume;
                case ON_HIGH -> throttleFade.gain1 * rpmFade.gain1 * sample.sampleVolume;
                case OFF_LOW -> throttleFade.gain2 * rpmFade.gain2 * sample.sampleVolume;
                case OFF_HIGH -> throttleFade.gain2 * rpmFade.gain1 * sample.sampleVolume;
                case LIMITER -> limiterGain * sample.sampleVolume;
            };
        }

        private static float getRpmPitch(final float rpm, final float sampleRpm) {
            final float cents = (rpm - sampleRpm) * RPM_PITCH_FACTOR;
            return Mth.clamp((float) Math.pow(2.0, cents / 1200.0f), 0.5f, 2.0f);
        }

        private static CrossFade crossFade(final float value, final float start, final float end) {
            final float x = ratio(value, start, end);
            return new CrossFade(
                    (float) Math.cos((1.0f - x) * 0.5f * Math.PI),
                    (float) Math.cos(x * 0.5f * Math.PI)
            );
        }

        private static float ratio(final float value, final float start, final float end) {
            return Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        }

        @Override
        public double getX() {
            return this.x;
        }

        @Override
        public double getY() {
            return this.y;
        }

        @Override
        public double getZ() {
            return this.z;
        }
    }

    private record CrossFade(float gain1, float gain2) {
    }
}
