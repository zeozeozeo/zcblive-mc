package lol.zeo.zcblive.client.clickpack;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lol.zeo.zcblive.ZCBLiveMod;

public final class ClickpackLoader {
    public LoadedClickpack load(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Clickpack path is not a directory: " + root);
        }

        PlayerClicks keyboardClicks = null;
        PlayerClicks mouseClicks = null;
        ClickSample noise = null;

        try {
            Path player1 = findChild(root, "player1");
            Path player2 = findChild(root, "player2");
            if (player1 != null || player2 != null) {
                if (player1 != null) {
                    keyboardClicks = loadPlayerClicks(player1);
                    noise = findNoise(player1);
                }
                if (player2 != null) {
                    mouseClicks = loadPlayerClicks(player2);
                    if (noise == null) {
                        noise = findNoise(player2);
                    }
                }
                if (noise == null) {
                    noise = findNoise(root);
                }
            } else {
                mouseClicks = loadPlayerClicks(root);
                noise = findNoise(root);
            }

            if ((keyboardClicks == null || keyboardClicks.isEmpty()) && (mouseClicks == null || mouseClicks.isEmpty())) {
                throw new IOException("No click sounds found in " + root);
            }
            if (keyboardClicks != null && keyboardClicks.isEmpty()) {
                keyboardClicks.close();
                keyboardClicks = null;
            }
            if (mouseClicks != null && mouseClicks.isEmpty()) {
                mouseClicks.close();
                mouseClicks = null;
            }

            return new LoadedClickpack(root.getFileName().toString(), root, keyboardClicks, mouseClicks, noise);
        } catch (IOException exception) {
            closeQuietly(keyboardClicks);
            closeQuietly(mouseClicks);
            if (noise != null) {
                noise.close();
            }
            throw exception;
        }
    }

    private PlayerClicks loadPlayerClicks(Path directory) throws IOException {
        PlayerClicks clicks = new PlayerClicks();
        for (Path child : sortedDirectories(directory)) {
            ClickType type = clickTypeForDirectory(child.getFileName().toString());
            if (type == ClickType.NONE) {
                continue;
            }
            loadSamples(clicks, type, child);
        }
        return clicks;
    }

    private void loadSamples(PlayerClicks clicks, ClickType type, Path directory) throws IOException {
        for (Path path : sortedFiles(directory)) {
            try {
                clicks.add(type, ClickSample.load(path));
            } catch (Exception exception) {
                ZCBLiveMod.LOGGER.warn("Skipping unreadable audio file {}", path, exception);
            }
        }
    }

    private ClickSample findNoise(Path directory) throws IOException {
        for (Path path : sortedFiles(directory)) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.startsWith("noise")
                || name.startsWith("whitenoise")
                || name.startsWith("pcnoise")
                || name.startsWith("background")) {
                try {
                    return ClickSample.load(path);
                } catch (Exception exception) {
                    ZCBLiveMod.LOGGER.warn("Skipping unreadable noise file {}", path, exception);
                    return null;
                }
            }
        }
        return null;
    }

    private Path findChild(Path root, String wanted) throws IOException {
        for (Path child : sortedDirectories(root)) {
            if (child.getFileName().toString().equalsIgnoreCase(wanted)) {
                return child;
            }
        }
        return null;
    }

    private ClickType clickTypeForDirectory(String directoryName) {
        StringBuilder builder = new StringBuilder(directoryName.length());
        for (int i = 0; i < directoryName.length(); i++) {
            char c = directoryName.charAt(i);
            if (Character.isAlphabetic(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }

        String normalized = builder.toString();
        if ("hardclick".equals(normalized) || "hardclicks".equals(normalized)) {
            return ClickType.HARD_CLICK;
        }
        if ("hardrelease".equals(normalized) || "hardreleases".equals(normalized)) {
            return ClickType.HARD_RELEASE;
        }
        if ("click".equals(normalized) || "clicks".equals(normalized)) {
            return ClickType.CLICK;
        }
        if ("release".equals(normalized) || "releases".equals(normalized)) {
            return ClickType.RELEASE;
        }
        if ("softclick".equals(normalized) || "softclicks".equals(normalized)) {
            return ClickType.SOFT_CLICK;
        }
        if ("softrelease".equals(normalized) || "softreleases".equals(normalized)) {
            return ClickType.SOFT_RELEASE;
        }
        if ("microclick".equals(normalized) || "microclicks".equals(normalized)) {
            return ClickType.MICRO_CLICK;
        }
        if ("microrelease".equals(normalized) || "microreleases".equals(normalized)) {
            return ClickType.MICRO_RELEASE;
        }
        return ClickType.NONE;
    }

    private List<Path> sortedDirectories(Path root) throws IOException {
        List<Path> directories = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    directories.add(path);
                }
            }
        }
        Collections.sort(directories, PATH_NAME_COMPARATOR);
        return directories;
    }

    private List<Path> sortedFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<Path>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        Collections.sort(files, PATH_NAME_COMPARATOR);
        return files;
    }

    private void closeQuietly(PlayerClicks clicks) {
        if (clicks != null) {
            clicks.close();
        }
    }

    private static final Comparator<Path> PATH_NAME_COMPARATOR = new Comparator<Path>() {
        @Override
        public int compare(Path left, Path right) {
            return left.getFileName().toString().toLowerCase(Locale.ROOT)
                .compareTo(right.getFileName().toString().toLowerCase(Locale.ROOT));
        }
    };
}
