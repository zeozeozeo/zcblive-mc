package lol.zeo.zcblive.client.clickpack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerClicks {
    private final EnumMap<ClickType, List<ClickSample>> samples = new EnumMap<ClickType, List<ClickSample>>(ClickType.class);

    public PlayerClicks() {
        for (ClickType type : ClickType.values()) {
            if (type != ClickType.NONE) {
                samples.put(type, new ArrayList<ClickSample>());
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
        int count = 0;
        for (List<ClickSample> bucket : samples.values()) {
            count += bucket.size();
        }
        return count;
    }

    public ClickSample randomSample(ClickType preferredType) {
        return randomSample(preferredType, true);
    }

    public ClickSample randomSample(ClickType preferredType, boolean allowHardClicks) {
        for (ClickType type : preferredType.preferred()) {
            if (!allowHardClicks && (type == ClickType.HARD_CLICK || type == ClickType.HARD_RELEASE)) {
                continue;
            }
            List<ClickSample> bucket = samples.get(type);
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            return bucket.get(ThreadLocalRandom.current().nextInt(bucket.size()));
        }
        return null;
    }

    public void close() {
        for (Map.Entry<ClickType, List<ClickSample>> entry : samples.entrySet()) {
            for (ClickSample sample : entry.getValue()) {
                sample.close();
            }
        }
    }
}
