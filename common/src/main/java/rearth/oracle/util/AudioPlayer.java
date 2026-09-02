package rearth.oracle.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
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
        if (playing == null || !playing.getIdentifier().equals(location)) return false;

        ChannelHandle handle = Minecraft.getInstance().getSoundManager().soundEngine.instanceToChannel.get(playing);
        return handle != null && !handle.isStopped();
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

        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            Oracle.LOGGER.warn("Wiki audio not found: {}", location);
            return false;
        }

        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        soundManager.soundCache.putIfAbsent(location, resource.get());

        stop();

        WikiSoundInstance instance = new WikiSoundInstance(location);
        if (soundManager.play(instance) == PlayResult.NOT_STARTED) return false;

        playing = instance;
        return true;
    }

    public static void stop() {
        if (playing == null) return;

        Minecraft.getInstance().getSoundManager().stop(playing);
        playing = null;
    }

    public static void release() {
        stop();
    }

    private static class WikiSound extends Sound {
        private WikiSound(Identifier location) {
            super(location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Type.FILE, false, false, ATTENUATION_DISTANCE);
        }

        @Override
        public Identifier getPath() {
            return getLocation();
        }
    }

    private static class WikiSoundInstance implements SoundInstance {
        private final Sound sound;
        private final WeighedSoundEvents events;

        WikiSoundInstance(Identifier location) {
            this.sound = new WikiSound(location);
            this.events = new WeighedSoundEvents(location, null);
            this.events.addSound(this.sound);
        }

        @Override
        public Identifier getIdentifier() {
            return sound.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            return events;
        }

        @Override
        public Sound getSound() {
            return sound;
        }

        @Override
        public SoundSource getSource() {
            return SoundSource.MASTER;
        }

        @Override
        public boolean isLooping() {
            return false;
        }

        @Override
        public boolean isRelative() {
            return true;
        }

        @Override
        public int getDelay() {
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
        public Attenuation getAttenuation() {
            return Attenuation.NONE;
        }
    }
}
