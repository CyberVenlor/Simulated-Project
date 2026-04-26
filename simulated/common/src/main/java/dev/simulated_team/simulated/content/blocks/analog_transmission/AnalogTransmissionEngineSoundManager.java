package dev.simulated_team.simulated.content.blocks.analog_transmission;

import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.api.sound.SimSoundEntry;
import dev.simulated_team.simulated.index.SimSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AnalogTransmissionEngineSoundManager {
    private static final int SCAN_RADIUS = 80;

    private static final Map<AnalogTransmissionBlockEntity, EnumMap<FerrariSample, FerrariEngineSoundInstance>> ACTIVE_SOUNDS = new HashMap<>();
    private static ClientLevel activeLevel;

    public static void tick(final ClientLevel level, final LocalPlayer player) {
        if (activeLevel != level) {
            ACTIVE_SOUNDS.clear();
            AnalogTransmissionAudioRegistry.clear();
            activeLevel = level;
        }

        cleanupStoppedSounds();
        for (final AnalogTransmissionBlockEntity analogTransmission : AnalogTransmissionAudioRegistry.snapshot()) {
            if (analogTransmission.getFerrariEngineRpm() > 0.0f) {
                playEngine(level, analogTransmission);
            }
        }
    }

    private static void playEngine(final ClientLevel level, final AnalogTransmissionBlockEntity analogTransmission) {
        final Minecraft minecraft = Minecraft.getInstance();
        final EnumMap<FerrariSample, FerrariEngineSoundInstance> samples =
                ACTIVE_SOUNDS.computeIfAbsent(analogTransmission, key -> new EnumMap<>(FerrariSample.class));

        for (final FerrariSample sample : FerrariSample.values()) {
            final FerrariEngineSoundInstance sound = samples.computeIfAbsent(sample,
                    key -> new FerrariEngineSoundInstance(analogTransmission, key.event.event(), level.getRandom(), key));

            if (!minecraft.getSoundManager().isActive(sound)) {
                minecraft.getSoundManager().play(sound);
            }
        }
    }

    private static void cleanupStoppedSounds() {
        final Minecraft minecraft = Minecraft.getInstance();
        final Iterator<Map.Entry<AnalogTransmissionBlockEntity, EnumMap<FerrariSample, FerrariEngineSoundInstance>>> iterator = ACTIVE_SOUNDS.entrySet().iterator();

        while (iterator.hasNext()) {
            final Map.Entry<AnalogTransmissionBlockEntity, EnumMap<FerrariSample, FerrariEngineSoundInstance>> entry = iterator.next();
            final AnalogTransmissionBlockEntity analogTransmission = entry.getKey();
            if (analogTransmission == null || analogTransmission.isRemoved() || analogTransmission.getLevel() == null) {
                iterator.remove();
                continue;
            }

            entry.getValue().values().removeIf(sound -> sound.isStopped() || !minecraft.getSoundManager().isActive(sound));
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static Vec3 getSoundPosition(final AnalogTransmissionBlockEntity analogTransmission) {
        final Vec3 localCenter = Vec3.atCenterOf(analogTransmission.getBlockPos());
        if (analogTransmission.getLevel() == null) {
            return localCenter;
        }

        return Sable.HELPER.projectOutOfSubLevel(analogTransmission.getLevel(), localCenter);
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
        private static final float ENGINE_VOLUME_SCALE = 0.4f;

        private final AnalogTransmissionBlockEntity analogTransmission;
        private final FerrariSample sample;
        private int inactiveTicks;

        protected FerrariEngineSoundInstance(final AnalogTransmissionBlockEntity analogTransmission, final SoundEvent event,
                                             final RandomSource random, final FerrariSample sample) {
            super(event, SoundSource.BLOCKS, random);
            this.analogTransmission = analogTransmission;
            this.sample = sample;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            final Vec3 soundPosition = getSoundPosition(analogTransmission);
            this.x = soundPosition.x;
            this.y = soundPosition.y;
            this.z = soundPosition.z;
            this.volume = 0.001f;
        }

        @Override
        public void tick() {
            final AnalogTransmissionBlockEntity analogTransmission = this.analogTransmission;
            if (analogTransmission == null || analogTransmission.isRemoved() || analogTransmission.getLevel() == null) {
                this.stop();
                return;
            }

            final Vec3 soundPosition = getSoundPosition(analogTransmission);
            this.x = soundPosition.x;
            this.y = soundPosition.y;
            this.z = soundPosition.z;

            final float rpm = analogTransmission.getFerrariEngineRpm();
            final float throttle = analogTransmission.getFerrariAudioThrottle();

            final LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.distanceToSqr(soundPosition) > Mth.square(SCAN_RADIUS + 8.0f)) {
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
            this.volume = getSampleGain(this.sample, rpm, throttle) * ENGINE_VOLUME_SCALE;
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
