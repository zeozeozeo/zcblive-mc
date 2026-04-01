package lol.zeo.zcblive.client.clickpack;

import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.lwjgl.BufferUtils;

public final class ClickSample {
	private final Path path;
	private final byte[] pcmData;
	private final AudioFormat format;
	private SoundBuffer buffer;
	private int bufferGainPercent = Integer.MIN_VALUE;

	private ClickSample(Path path, byte[] pcmData, AudioFormat format) {
		this.path = path;
		this.pcmData = pcmData;
		this.format = format;
	}

	public static ClickSample load(Path path) throws IOException, UnsupportedAudioFileException {
		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (filename.endsWith(".ogg")) {
			try (InputStream input = Files.newInputStream(path); JOrbisAudioStream stream = new JOrbisAudioStream(input)) {
				ByteBuffer decoded = stream.readAll();
				byte[] data = new byte[decoded.remaining()];
				decoded.get(data);
				return new ClickSample(path, data, stream.getFormat());
			}
		}

		try (AudioInputStream source = AudioSystem.getAudioInputStream(path.toFile())) {
			AudioFormat baseFormat = source.getFormat();
			AudioFormat targetFormat = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				baseFormat.getSampleRate(),
				16,
				baseFormat.getChannels(),
				baseFormat.getChannels() * 2,
				baseFormat.getSampleRate(),
				false
			);
			try (AudioInputStream pcm = AudioSystem.getAudioInputStream(targetFormat, source)) {
				byte[] data = pcm.readAllBytes();
				return new ClickSample(path, data, targetFormat);
			}
		}
	}

	public Path path() {
		return path;
	}

	public synchronized SoundBuffer buffer(double preampGain) {
		int gainPercent = clamp((int) Math.round(preampGain * 100.0D), 0, 500);
		if (buffer != null && bufferGainPercent == gainPercent) {
			return buffer;
		}
		if (buffer != null) {
			buffer.discardAlBuffer();
		}
		buffer = new SoundBuffer(toByteBuffer(applyGain(gainPercent / 100.0D)), format);
		bufferGainPercent = gainPercent;
		return buffer;
	}

	public synchronized void close() {
		if (buffer != null) {
			buffer.discardAlBuffer();
			buffer = null;
			bufferGainPercent = Integer.MIN_VALUE;
		}
	}

	private byte[] applyGain(double gain) {
		if (pcmData.length == 0) {
			return pcmData;
		}
		if (!supportsPcmGain()) {
			if (gain == 1.0D) {
				return pcmData;
			}
			return Arrays.copyOf(pcmData, pcmData.length);
		}
		byte[] output = new byte[pcmData.length];
		boolean bigEndian = format.isBigEndian();
		for (int index = 0; index + 1 < pcmData.length; index += 2) {
			int low = pcmData[index] & 0xFF;
			int high = pcmData[index + 1] & 0xFF;
			short sample = (short) (bigEndian ? ((low << 8) | high) : (low | (high << 8)));
			int scaled = (int) Math.round(sample * gain);
			if (scaled > Short.MAX_VALUE) {
				scaled = Short.MAX_VALUE;
			} else if (scaled < Short.MIN_VALUE) {
				scaled = Short.MIN_VALUE;
			}
			if (bigEndian) {
				output[index] = (byte) ((scaled >>> 8) & 0xFF);
				output[index + 1] = (byte) (scaled & 0xFF);
			} else {
				output[index] = (byte) (scaled & 0xFF);
				output[index + 1] = (byte) ((scaled >>> 8) & 0xFF);
			}
		}
		return output;
	}

	private boolean supportsPcmGain() {
		return format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && format.getSampleSizeInBits() == 16;
	}

	private ByteBuffer toByteBuffer(byte[] data) {
		ByteBuffer direct = BufferUtils.createByteBuffer(data.length);
		direct.put(data);
		direct.flip();
		return direct;
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
