package lol.zeo.zcblive.client.db;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lol.zeo.zcblive.client.clickpack.ClickpackLoader;
import lol.zeo.zcblive.client.clickpack.LoadedClickpack;

public final class ClickpackDbClient {
    private static final String DATABASE_URL = "https://raw.githubusercontent.com/zeozeozeo/clickpack-db/main/db.json";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int nextId = 1;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "zcblive-db-" + nextId++);
            thread.setDaemon(true);
            return thread;
        }
    });

    public CompletableFuture<DatabaseSnapshot> fetchDatabase() {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<DatabaseSnapshot>() {
            @Override
            public DatabaseSnapshot get() {
                try {
                    String body = readStringFromUrl(DATABASE_URL, 20_000, 20_000);
                    return parseSnapshot(body);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        }, EXECUTOR);
    }

    public CompletableFuture<String> downloadAndInstall(
        final ClickpackDbEntry entry,
        final Path clickpacksDir,
        final ClickpackLoader loader
    ) {
        return CompletableFuture.supplyAsync(new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                try {
                    Files.createDirectories(clickpacksDir);
                    Path tempZip = Files.createTempFile(clickpacksDir, "zcb-live-", ".zip");
                    Path tempDir = Files.createTempDirectory(clickpacksDir, "zcb-live-extract-");
                    try {
                        downloadArchive(entry, tempZip);
                        extractArchive(tempZip, tempDir);
                        try (LoadedClickpack ignored = loader.load(tempDir)) {
                            // validated by successful load
                        }
                        String directoryName = directoryNameForPack(entry.name());
                        Path finalDir = clickpacksDir.resolve(directoryName);
                        deleteRecursively(finalDir);
                        try {
                            Files.move(tempDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
                        } catch (IOException moveFailure) {
                            Files.move(tempDir, finalDir, StandardCopyOption.REPLACE_EXISTING);
                        }
                        Files.deleteIfExists(tempZip);
                        return directoryName;
                    } finally {
                        Files.deleteIfExists(tempZip);
                        deleteRecursively(tempDir);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        }, EXECUTOR);
    }

    public String directoryNameForPack(String packName) {
        StringBuilder builder = new StringBuilder(packName.length());
        for (int index = 0; index < packName.length(); index++) {
            char character = packName.charAt(index);
            if (character <= 31
                || character == '/'
                || character == '\\'
                || character == ':'
                || character == '*'
                || character == '?'
                || character == '"'
                || character == '<'
                || character == '>'
                || character == '|') {
                builder.append('_');
            } else {
                builder.append(character);
            }
        }

        String result = builder.toString().trim();
        return result.isEmpty() ? "clickpack" : result;
    }

    private DatabaseSnapshot parseSnapshot(String rawJson) throws IOException {
        JsonObject root = new JsonParser().parse(rawJson).getAsJsonObject();
        JsonObject clickpacks = root.getAsJsonObject("clickpacks");

        List<ClickpackDbEntry> entries = new ArrayList<ClickpackDbEntry>();
        if (clickpacks != null) {
            for (java.util.Map.Entry<String, JsonElement> clickpackEntry : clickpacks.entrySet()) {
                String name = clickpackEntry.getKey();
                JsonObject clickpack = clickpackEntry.getValue().getAsJsonObject();
                entries.add(new ClickpackDbEntry(
                    name,
                    getLong(clickpack, "size"),
                    getLong(clickpack, "uncompressed_size"),
                    getBoolean(clickpack, "has_noise"),
                    getString(clickpack, "url"),
                    getString(clickpack, "checksum"),
                    getNullableString(clickpack, "readme"),
                    getNullableString(clickpack, "added_at")
                ));
            }
        }

        Collections.sort(entries, new Comparator<ClickpackDbEntry>() {
            @Override
            public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                return left.name().toLowerCase(Locale.ROOT).compareTo(right.name().toLowerCase(Locale.ROOT));
            }
        });

        return new DatabaseSnapshot(
            entries,
            getNullableString(root, "updated_at_iso"),
            (int) getLong(root, "version")
        );
    }

    private void downloadArchive(ClickpackDbEntry entry, Path tempZip) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(entry.url()).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(120_000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Archive request failed with status " + code);
            }

            MessageDigest digest = md5();
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(tempZip)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }

            String actualChecksum = toHex(digest.digest());
            if (!actualChecksum.equalsIgnoreCase(entry.checksum())) {
                throw new IOException("Checksum mismatch for " + entry.name());
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void extractArchive(Path archive, Path tempDir) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry zipEntry;
            while ((zipEntry = input.getNextEntry()) != null) {
                Path target = tempDir.resolve(zipEntry.getName()).normalize();
                if (!target.startsWith(tempDir)) {
                    throw new IOException("Blocked invalid archive entry " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        List<Path> children = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
            stream.forEach(children::add);
        }

        Collections.sort(children, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return right.compareTo(left);
            }
        });

        for (Path child : children) {
            Files.deleteIfExists(child);
        }
    }

    private String readStringFromUrl(String url, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Database request failed with status " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                return readUtf8(input);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exception) {
            throw new CompletionException(exception);
        }
    }

    private String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte value : data) {
            int unsigned = value & 0xFF;
            if (unsigned < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(unsigned));
        }
        return builder.toString();
    }

    private long getLong(JsonObject object, String key) {
        if (object == null) {
            return 0L;
        }
        JsonElement value = object.get(key);
        return value == null ? 0L : value.getAsLong();
    }

    private boolean getBoolean(JsonObject object, String key) {
        if (object == null) {
            return false;
        }
        JsonElement value = object.get(key);
        return value != null && value.getAsBoolean();
    }

    private String getString(JsonObject object, String key) {
        if (object == null) {
            return "";
        }
        JsonElement value = object.get(key);
        return value == null ? "" : value.getAsString();
    }

    private String getNullableString(JsonObject object, String key) {
        if (object == null) {
            return null;
        }
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    public static final class DatabaseSnapshot {
        private List<ClickpackDbEntry> entries;
        private String updatedAtIso;
        private int version;

        @SuppressWarnings("unused")
        public DatabaseSnapshot() {
            this(new ArrayList<ClickpackDbEntry>(), null, 0);
        }

        public DatabaseSnapshot(List<ClickpackDbEntry> entries, String updatedAtIso, int version) {
            this.entries = entries == null ? new ArrayList<ClickpackDbEntry>() : new ArrayList<ClickpackDbEntry>(entries);
            this.updatedAtIso = updatedAtIso;
            this.version = version;
        }

        public List<ClickpackDbEntry> entries() {
            return entries == null ? Collections.<ClickpackDbEntry>emptyList() : Collections.unmodifiableList(entries);
        }

        public String updatedAtIso() {
            return updatedAtIso;
        }

        public int version() {
            return version;
        }
    }
}
