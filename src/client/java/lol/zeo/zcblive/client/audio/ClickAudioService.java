package lol.zeo.zcblive.client.audio;

import com.mojang.blaze3d.audio.Library;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import lol.zeo.zcblive.client.clickpack.ClickSample;
import lol.zeo.zcblive.mixin.client.SoundEngineAccessor;
import lol.zeo.zcblive.mixin.client.SoundManagerAccessor;

public final class ClickAudioService {
	private ChannelAccess.@org.jspecify.annotations.Nullable ChannelHandle noiseHandle;
	private @org.jspecify.annotations.Nullable ClickSample noiseSample;
	private float noiseVolume = Float.NaN;

	public boolean play(ClickSample sample, double volume, double pitch, double preampGain) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return false;
		}

		float finalVolume = (float) Math.max(0.0D, volume * minecraft.options.getFinalSoundSourceVolume(SoundSource.PLAYERS));
		if (finalVolume <= 0.0F) {
			return false;
		}

		SoundManager soundManager = minecraft.getSoundManager();
		SoundEngine soundEngine = ((SoundManagerAccessor) soundManager).zcblive$getSoundEngine();
		ChannelAccess channelAccess = ((SoundEngineAccessor) soundEngine).zcblive$getChannelAccess();
		CompletableFuture<ChannelAccess.ChannelHandle> future = channelAccess.createHandle(Library.Pool.STATIC);
		ChannelAccess.ChannelHandle handle;
		try {
			handle = future.join();
		} catch (RuntimeException exception) {
			return false;
		}
		if (handle == null) {
			return false;
		}

		float finalPitch = Mth.clamp((float) pitch, 0.5F, 2.0F);
		var buffer = sample.buffer(preampGain);
		handle.execute(channel -> {
			channel.setPitch(finalPitch);
			channel.setVolume(finalVolume);
			channel.disableAttenuation();
			channel.setRelative(true);
			channel.setSelfPosition(Vec3.ZERO);
			channel.attachStaticBuffer(buffer);
			channel.play();
		});
		return true;
	}

	public synchronized void syncNoiseLoop(@org.jspecify.annotations.Nullable ClickSample sample, double preampGain, boolean active) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || !active || sample == null) {
			stopNoiseLoop();
			return;
		}

		float finalVolume = (float) Math.max(0.0D, minecraft.options.getFinalSoundSourceVolume(SoundSource.PLAYERS) * preampGain);
		if (finalVolume <= 0.0F) {
			stopNoiseLoop();
			return;
		}

		if (noiseHandle == null || noiseHandle.isStopped() || noiseSample != sample) {
			stopNoiseLoop();
			ChannelAccess.ChannelHandle handle = createStaticHandle();
			if (handle == null) {
				return;
			}
			handle.execute(channel -> {
				channel.setPitch(1.0F);
				channel.setVolume(finalVolume);
				channel.disableAttenuation();
				channel.setRelative(true);
				channel.setSelfPosition(Vec3.ZERO);
				channel.setLooping(true);
				channel.attachStaticBuffer(sample.buffer(1.0D));
				channel.play();
			});
			noiseHandle = handle;
			noiseSample = sample;
			noiseVolume = finalVolume;
			return;
		}

		if (Float.compare(noiseVolume, finalVolume) != 0) {
			noiseHandle.execute(channel -> channel.setVolume(finalVolume));
			noiseVolume = finalVolume;
		}
	}

	public synchronized void stopNoiseLoop() {
		if (noiseHandle != null) {
			noiseHandle.execute(channel -> {
				channel.setLooping(false);
				channel.stop();
			});
			noiseHandle = null;
		}
		noiseSample = null;
		noiseVolume = Float.NaN;
	}

	private ChannelAccess.@org.jspecify.annotations.Nullable ChannelHandle createStaticHandle() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null) {
			return null;
		}

		SoundManager soundManager = minecraft.getSoundManager();
		SoundEngine soundEngine = ((SoundManagerAccessor) soundManager).zcblive$getSoundEngine();
		ChannelAccess channelAccess = ((SoundEngineAccessor) soundEngine).zcblive$getChannelAccess();
		CompletableFuture<ChannelAccess.ChannelHandle> future = channelAccess.createHandle(Library.Pool.STATIC);
		try {
			return future.join();
		} catch (RuntimeException exception) {
			return null;
		}
	}
}
