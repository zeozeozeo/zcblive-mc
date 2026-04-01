package lol.zeo.zcblive.client.clickpack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

public final class PlayerClicks {
	private final EnumMap<ClickType, List<ClickSample>> samples = new EnumMap<>(ClickType.class);

	public PlayerClicks() {
		for (ClickType type : ClickType.values()) {
			if (type != ClickType.NONE) {
				samples.put(type, new ArrayList<>());
			}
		}
	}

	public void add(ClickType type, ClickSample sample) {
		List<ClickSample> bucket = samples.get(type);
		if (bucket != null) {
			bucket.add(sample);
		}
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public int size() {
		return samples.values().stream().mapToInt(List::size).sum();
	}

	public @Nullable ClickSample randomSample(ClickType preferredType) {
		for (ClickType type : preferredType.preferred()) {
			List<ClickSample> bucket = samples.get(type);
			if (bucket == null || bucket.isEmpty()) {
				continue;
			}
			return bucket.get(ThreadLocalRandom.current().nextInt(bucket.size()));
		}
		return null;
	}

	public void close() {
		for (List<ClickSample> bucket : samples.values()) {
			for (ClickSample sample : bucket) {
				sample.close();
			}
		}
	}
}
