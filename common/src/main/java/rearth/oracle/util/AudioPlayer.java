package rearth.oracle.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Channel.SourceManager;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.resource.Resource;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import org.jetbrains.annotations.Nullable;
import rearth.oracle.Oracle;

import java.util.Locale;
import java.util.Optional;

public final class AudioPlayer {
    private static final int ATTENUATION_DISTANCE = 16;

    @Nullable
    private static WikiSoundInstance playing;

    private AudioPlayer() {
    }

    public static boolean isPlaying(Identifier location) {
        if (playing == null || !playing.getId().equals(location)) return false;

        SourceManager source = MinecraftClient.getInstance().getSoundManager().soundSystem.sources.get(playing);
        return source != null && !source.isStopped();
    }

    public static boolean toggle(Identifier location) {
        if (isPlaying(location)) {
            stop();
            return false;
        }
        return play(location);
    }

    public static boolean play(Identifier location) {
        if (!location.getPath().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            Oracle.LOGGER.warn("Unsupported wiki audio format: {}", location);
            return false;
        }

        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            Oracle.LOGGER.warn("Wiki audio not found: {}", location);
            return false;
        }

        SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();
        soundManager.soundResources.putIfAbsent(location, resource.get());

        stop();

        WikiSoundInstance instance = new WikiSoundInstance(location);
        soundManager.play(instance);

        playing = instance;
        return true;
    }

    public static void stop() {
        if (playing == null) return;

        MinecraftClient.getInstance().getSoundManager().stop(playing);
        playing = null;
    }

    public static void release() {
        stop();
    }

    private static class WikiSound extends Sound {
        private WikiSound(Identifier location) {
            super(location, ConstantFloatProvider.create(1.0F), ConstantFloatProvider.create(1.0F), 1,
                RegistrationType.FILE, false, false, ATTENUATION_DISTANCE);
        }

        @Override
        public Identifier getLocation() {
            return getIdentifier();
        }
    }

    private static class WikiSoundInstance implements SoundInstance {
        private final Sound sound;
        private final WeightedSoundSet soundSet;

        WikiSoundInstance(Identifier location) {
            this.sound = new WikiSound(location);
            this.soundSet = new WeightedSoundSet(location, null);
            this.soundSet.add(this.sound);
        }

        @Override
        public Identifier getId() {
            return sound.getIdentifier();
        }

        @Override
        public WeightedSoundSet getSoundSet(SoundManager soundManager) {
            return soundSet;
        }

        @Override
        public Sound getSound() {
            return sound;
        }

        @Override
        public SoundCategory getCategory() {
            return SoundCategory.MASTER;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isRelative() {
            return true;
        }

        @Override
        public int getRepeatDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            return 1.0F;
        }

        @Override
        public float getPitch() {
            return 1.0F;
        }

        @Override
        public double getX() {
            return 0;
        }

        @Override
        public double getY() {
            return 0;
        }

        @Override
        public double getZ() {
            return 0;
        }

        @Override
        public AttenuationType getAttenuationType() {
            return AttenuationType.NONE;
        }
    }
}
