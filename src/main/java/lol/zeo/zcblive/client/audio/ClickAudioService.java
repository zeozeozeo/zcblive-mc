package lol.zeo.zcblive.client.audio;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lol.zeo.zcblive.ZCBLiveMod;
import lol.zeo.zcblive.client.clickpack.ClickSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.util.SoundCategory;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

public final class ClickAudioService {
    private final AtomicLong sourceCounter = new AtomicLong();
    private final List<TransientSource> transientSources = new ArrayList<TransientSource>();

    private volatile String noiseSourceName;
    private volatile ClickSample noiseSample;
    private volatile float noiseVolume = Float.NaN;

    private volatile boolean reflectionInitialized;
    private Field soundHandlerManagerField;
    private Field soundManagerSystemField;

    public boolean play(ClickSample sample, double volume, double pitch, double preampGain) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || sample == null) {
            return false;
        }

        SoundSystem soundSystem = resolveSoundSystem(minecraft);
        if (soundSystem == null) {
            return false;
        }

        cleanupTransientSources(soundSystem);
        float playersVolume = resolveFinalPlayersVolume(minecraft);
        float finalVolume = (float) Math.max(0.0D, volume * preampGain * playersVolume);
        if (finalVolume <= 0.0F) {
            return false;
        }

        float finalPitch = clamp((float) pitch, 0.5F, 2.0F);
        String sourceName = "zcblive-click-" + sourceCounter.incrementAndGet();

        try {
            URL url = sample.fileUrl();
            soundSystem.newSource(
                false,
                sourceName,
                url,
                sample.fileName(),
                false,
                0.0F,
                0.0F,
                0.0F,
                SoundSystemConfig.ATTENUATION_NONE,
                0.0F
            );
            soundSystem.setPitch(sourceName, finalPitch);
            soundSystem.setVolume(sourceName, finalVolume);
            soundSystem.play(sourceName);
            transientSources.add(new TransientSource(sourceName, System.currentTimeMillis() + 60_000L));
            return true;
        } catch (Throwable throwable) {
            ZCBLiveMod.LOGGER.debug("Failed to play click sample {}", sample.path(), throwable);
            safeRemove(soundSystem, sourceName);
            return false;
        }
    }

    public synchronized void syncNoiseLoop(ClickSample sample, double preampGain, boolean active) {
        Minecraft minecraft = Minecraft.getMinecraft();
        SoundSystem soundSystem = resolveSoundSystem(minecraft);
        if (minecraft == null || soundSystem == null || !active || sample == null) {
            stopNoiseLoop();
            return;
        }

        float playersVolume = resolveFinalPlayersVolume(minecraft);
        float finalVolume = (float) Math.max(0.0D, preampGain * playersVolume);
        if (finalVolume <= 0.0F) {
            stopNoiseLoop();
            return;
        }

        boolean needsRestart = noiseSourceName == null || noiseSample != sample || !isSourceAlive(soundSystem, noiseSourceName);
        if (needsRestart) {
            stopNoiseLoop();
            String sourceName = "zcblive-noise";
            try {
                URL url = sample.fileUrl();
                soundSystem.newSource(
                    true,
                    sourceName,
                    url,
                    sample.fileName(),
                    true,
                    0.0F,
                    0.0F,
                    0.0F,
                    SoundSystemConfig.ATTENUATION_NONE,
                    0.0F
                );
                soundSystem.setPitch(sourceName, 1.0F);
                soundSystem.setVolume(sourceName, finalVolume);
                soundSystem.play(sourceName);
                noiseSourceName = sourceName;
                noiseSample = sample;
                noiseVolume = finalVolume;
            } catch (Throwable throwable) {
                ZCBLiveMod.LOGGER.debug("Failed to start noise loop {}", sample.path(), throwable);
                safeRemove(soundSystem, sourceName);
                noiseSourceName = null;
                noiseSample = null;
                noiseVolume = Float.NaN;
            }
            return;
        }

        if (Float.compare(noiseVolume, finalVolume) != 0) {
            try {
                soundSystem.setVolume(noiseSourceName, finalVolume);
                noiseVolume = finalVolume;
            } catch (Throwable throwable) {
                ZCBLiveMod.LOGGER.debug("Failed to update noise loop volume", throwable);
                stopNoiseLoop();
            }
        }
    }

    public synchronized void stopNoiseLoop() {
        Minecraft minecraft = Minecraft.getMinecraft();
        SoundSystem soundSystem = resolveSoundSystem(minecraft);
        if (noiseSourceName != null && soundSystem != null) {
            try {
                soundSystem.stop(noiseSourceName);
                soundSystem.removeSource(noiseSourceName);
            } catch (Throwable throwable) {
                ZCBLiveMod.LOGGER.debug("Failed to stop noise loop source {}", noiseSourceName, throwable);
            }
        }
        noiseSourceName = null;
        noiseSample = null;
        noiseVolume = Float.NaN;
    }

    public void tick() {
        SoundSystem soundSystem = resolveSoundSystem(Minecraft.getMinecraft());
        if (soundSystem != null) {
            cleanupTransientSources(soundSystem);
            try {
                soundSystem.removeTemporarySources();
            } catch (Throwable ignored) {
                // Best-effort cleanup on backends that support temporary sources.
            }
        }
    }

    private void cleanupTransientSources(SoundSystem soundSystem) {
        long now = System.currentTimeMillis();
        Iterator<TransientSource> iterator = transientSources.iterator();
        while (iterator.hasNext()) {
            TransientSource source = iterator.next();
            boolean expired = now >= source.expiresAtMillis;
            boolean notPlaying = !isSourceAlive(soundSystem, source.name);

            if (notPlaying) {
                safeRemove(soundSystem, source.name);
                iterator.remove();
                continue;
            }
            if (expired) {
                // Safety valve: if a source somehow survives a long time, clean it up.
                safeRemove(soundSystem, source.name);
                iterator.remove();
            }
        }
    }

    private void safeRemove(SoundSystem soundSystem, String sourceName) {
        try {
            soundSystem.stop(sourceName);
        } catch (Throwable ignored) {
            // ignored on cleanup
        }
        try {
            soundSystem.removeSource(sourceName);
        } catch (Throwable ignored) {
            // ignored on cleanup
        }
    }

    private float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private float resolveFinalPlayersVolume(Minecraft minecraft) {
        if (minecraft == null || minecraft.gameSettings == null) {
            return 1.0F;
        }
        float players = minecraft.gameSettings.getSoundLevel(SoundCategory.PLAYERS);
        float master = minecraft.gameSettings.getSoundLevel(SoundCategory.MASTER);
        float volume = players * master;
        return volume < 0.0F ? 0.0F : volume;
    }

    private boolean isSourceAlive(SoundSystem soundSystem, String sourceName) {
        if (soundSystem == null || sourceName == null) {
            return false;
        }
        try {
            return soundSystem.playing(sourceName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private SoundSystem resolveSoundSystem(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }

        SoundHandler soundHandler = minecraft.getSoundHandler();
        if (soundHandler == null) {
            return null;
        }

        initializeReflectionIfNeeded(soundHandler.getClass());
        if (soundHandlerManagerField == null || soundManagerSystemField == null) {
            return null;
        }

        try {
            Object manager = soundHandlerManagerField.get(soundHandler);
            if (!(manager instanceof SoundManager)) {
                return null;
            }
            Object soundSystem = soundManagerSystemField.get(manager);
            return soundSystem instanceof SoundSystem ? (SoundSystem) soundSystem : null;
        } catch (IllegalAccessException exception) {
            ZCBLiveMod.LOGGER.debug("Failed to access sound system fields", exception);
            return null;
        }
    }

    private synchronized void initializeReflectionIfNeeded(Class<?> soundHandlerClass) {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;

        soundHandlerManagerField = findField(soundHandlerClass, "sndManager", SoundManager.class);
        if (soundHandlerManagerField == null) {
            ZCBLiveMod.LOGGER.warn("Could not locate SoundHandler manager field");
            return;
        }

        soundManagerSystemField = findField(SoundManager.class, "sndSystem", SoundSystem.class);
        if (soundManagerSystemField == null) {
            ZCBLiveMod.LOGGER.warn("Could not locate SoundManager sound system field");
        }
    }

    private Field findField(Class<?> owner, String preferredName, Class<?> expectedType) {
        try {
            Field preferred = owner.getDeclaredField(preferredName);
            preferred.setAccessible(true);
            return preferred;
        } catch (NoSuchFieldException ignored) {
            // fallback to type-based lookup below
        }

        for (Field field : owner.getDeclaredFields()) {
            if (expectedType.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static final class TransientSource {
        private final String name;
        private final long expiresAtMillis;

        private TransientSource(String name, long expiresAtMillis) {
            this.name = name;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
