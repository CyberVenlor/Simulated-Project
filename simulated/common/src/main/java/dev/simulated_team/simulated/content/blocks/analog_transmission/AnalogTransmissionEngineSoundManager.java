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
    private static AudioProfile activeProfile = AudioProfile.BAC_MONO;

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
            final SampleSettings settings = activeProfile.get(sample);
            final FerrariEngineSoundInstance sound = samples.computeIfAbsent(sample,
                    key -> new FerrariEngineSoundInstance(analogTransmission, settings.event.event(), level.getRandom(), key, settings));

            if (!minecraft.getSoundManager().isActive(sound)) {
                minecraft.getSoundManager().play(sound);
            }
        }
    }

    public static AudioProfile getActiveProfile() {
        return activeProfile;
    }

    public static boolean setActiveProfile(final String id) {
        for (final AudioProfile profile : AudioProfile.values()) {
            if (profile.id.equals(id)) {
                setActiveProfile(profile);
                return true;
            }
        }

        return false;
    }

    public static void setActiveProfile(final AudioProfile profile) {
        if (activeProfile == profile) {
            return;
        }

        activeProfile = profile;
        for (final EnumMap<FerrariSample, FerrariEngineSoundInstance> samples : ACTIVE_SOUNDS.values()) {
            samples.values().forEach(FerrariEngineSoundInstance::stopFromManager);
        }
        ACTIVE_SOUNDS.clear();
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

    public enum AudioProfile {
        BAC_MONO("bac_mono", "BAC Mono",
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_BAC_MONO_ON_LOW, 1000.0f, 0.5f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_BAC_MONO_ON_HIGH, 1000.0f, 0.5f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_BAC_MONO_OFF_LOW, 1000.0f, 0.5f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_BAC_MONO_OFF_HIGH, 1000.0f, 0.5f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_BAC_MONO_LIMITER, 8000.0f, 0.4f)),
        FERRARI_458("ferrari_458", "Ferrari 458",
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_ON_LOW, 5300.0f, 1.5f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_ON_HIGH, 7700.0f, 2.5f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_OFF_LOW, 6900.0f, 1.4f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_OFF_HIGH, 7900.0f, 1.6f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_FERRARI_458_LIMITER, 8900.0f, 1.8f)),
        PROCAR("procar", "Procar",
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_PROCAR_ON_LOW, 3200.0f, 1.0f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_PROCAR_ON_HIGH, 8000.0f, 1.0f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_PROCAR_OFF_LOW, 3400.0f, 1.3f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_PROCAR_OFF_HIGH, 8430.0f, 1.3f),
                new SampleSettings(SimSoundEvents.ANALOG_TRANSMISSION_PROCAR_LIMITER, 8000.0f, 0.5f));

        private final String id;
        private final String displayName;
        private final EnumMap<FerrariSample, SampleSettings> samples;

        AudioProfile(final String id, final String displayName, final SampleSettings onLow, final SampleSettings onHigh,
                     final SampleSettings offLow, final SampleSettings offHigh, final SampleSettings limiter) {
            this.id = id;
            this.displayName = displayName;
            this.samples = new EnumMap<>(FerrariSample.class);
            this.samples.put(FerrariSample.ON_LOW, onLow);
            this.samples.put(FerrariSample.ON_HIGH, onHigh);
            this.samples.put(FerrariSample.OFF_LOW, offLow);
            this.samples.put(FerrariSample.OFF_HIGH, offHigh);
            this.samples.put(FerrariSample.LIMITER, limiter);
        }

        public String id() {
            return this.id;
        }

        public String displayName() {
            return this.displayName;
        }

        private SampleSettings get(final FerrariSample sample) {
            return this.samples.get(sample);
        }
    }

    private enum FerrariSample {
        ON_LOW,
        ON_HIGH,
        OFF_LOW,
        OFF_HIGH,
        LIMITER
    }

    private record SampleSettings(SimSoundEntry event, float sampleRpm, float sampleVolume) {
    }

    private static class FerrariEngineSoundInstance extends AbstractTickableSoundInstance {
        private static final float SOFT_LIMITER_RPM = 8800.0f;
        private static final float LIMITER_RPM = 8900.0f;
        private static final float RPM_PITCH_FACTOR = 0.2f;
        private static final float ENGINE_VOLUME_SCALE = 0.4f;

        private final AnalogTransmissionBlockEntity analogTransmission;
        private final FerrariSample sample;
        private final SampleSettings settings;
        private int inactiveTicks;

        protected FerrariEngineSoundInstance(final AnalogTransmissionBlockEntity analogTransmission, final SoundEvent event,
                                             final RandomSource random, final FerrariSample sample, final SampleSettings settings) {
            super(event, SoundSource.BLOCKS, random);
            this.analogTransmission = analogTransmission;
            this.sample = sample;
            this.settings = settings;
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

        void stopFromManager() {
            this.stop();
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
            this.pitch = getRpmPitch(rpm, this.settings.sampleRpm);
            this.volume = getSampleGain(this.sample, this.settings, rpm, throttle) * ENGINE_VOLUME_SCALE;
        }

        private static float getSampleGain(final FerrariSample sample, final SampleSettings settings, final float rpm, final float throttle) {
            final CrossFade rpmFade = crossFade(rpm, 3000.0f, 6500.0f);
            final CrossFade throttleFade = crossFade(throttle, 0.0f, 1.0f);
            final float limiterGain = ratio(rpm, SOFT_LIMITER_RPM * 0.93f, LIMITER_RPM);

            return switch (sample) {
                case ON_LOW -> throttleFade.gain1 * rpmFade.gain2 * settings.sampleVolume;
                case ON_HIGH -> throttleFade.gain1 * rpmFade.gain1 * settings.sampleVolume;
                case OFF_LOW -> throttleFade.gain2 * rpmFade.gain2 * settings.sampleVolume;
                case OFF_HIGH -> throttleFade.gain2 * rpmFade.gain1 * settings.sampleVolume;
                case LIMITER -> limiterGain * settings.sampleVolume;
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
