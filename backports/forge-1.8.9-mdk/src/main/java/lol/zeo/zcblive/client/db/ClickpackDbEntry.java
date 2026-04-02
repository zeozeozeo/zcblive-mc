package lol.zeo.zcblive.client.db;

public final class ClickpackDbEntry {
    private String name;
    private long size;
    private long uncompressedSize;
    private boolean hasNoise;
    private String url;
    private String checksum;
    private String readme;
    private String addedAt;

    @SuppressWarnings("unused")
    public ClickpackDbEntry() {
        this("", 0L, 0L, false, "", "", null, null);
    }

    public ClickpackDbEntry(
        String name,
        long size,
        long uncompressedSize,
        boolean hasNoise,
        String url,
        String checksum,
        String readme,
        String addedAt
    ) {
        this.name = name;
        this.size = size;
        this.uncompressedSize = uncompressedSize;
        this.hasNoise = hasNoise;
        this.url = url;
        this.checksum = checksum;
        this.readme = readme;
        this.addedAt = addedAt;
    }

    public String name() {
        return name;
    }

    public long size() {
        return size;
    }

    public long uncompressedSize() {
        return uncompressedSize;
    }

    public boolean hasNoise() {
        return hasNoise;
    }

    public String url() {
        return url;
    }

    public String checksum() {
        return checksum;
    }

    public String readme() {
        return readme;
    }

    public String addedAt() {
        return addedAt;
    }
}
