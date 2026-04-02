package lol.zeo.zcblive.client.clickpack;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

public final class ClickSample {
    private final Path path;
    private URL fileUrl;

    private ClickSample(Path path) {
        this.path = path;
    }

    public static ClickSample load(Path path) {
        return new ClickSample(path);
    }

    public Path path() {
        return path;
    }

    public String fileName() {
        return path.getFileName().toString();
    }

    public URL fileUrl() throws IOException {
        if (fileUrl == null) {
            fileUrl = path.toUri().toURL();
        }
        return fileUrl;
    }

    public void close() {
        // No buffers to release in the Forge 1.8.9 backport implementation.
    }
}
