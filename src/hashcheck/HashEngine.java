package hashcheck;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Streaming multi-algorithm hashing plus helpers for parsing / validating checksums. */
final class HashEngine {
    private HashEngine() { }

    enum Algo {
        SHA256("SHA-256", 64, "sha256", "SHA-256 (Default 256-bit Secure Hash)"),
        SHA512("SHA-512", 128, "sha512", "SHA-512 (High Entropy 512-bit Digest)"),
        MD5("MD5", 32, "md5", "MD5 (Legacy 128-bit RFC 1321)"),
        SHA1("SHA-1", 40, "sha1", "SHA-1 (Legacy / Git Digest)");

        final String label;
        final int hexLen;
        final String ext;
        final String longLabel;

        Algo(String label, int hexLen, String ext, String longLabel) {
            this.label = label;
            this.hexLen = hexLen;
            this.ext = ext;
            this.longLabel = longLabel;
        }

        static Algo byLen(int n) {
            for (Algo a : values()) if (a.hexLen == n) return a;
            return null;
        }

        static Algo byLabel(String s) {
            for (Algo a : values()) if (a.label.equalsIgnoreCase(s) || a.name().equalsIgnoreCase(s)) return a;
            return null;
        }
    }

    // ------------------------------------------------------------------ result
    enum End { DONE, CANCELLED, ERROR }

    static final class Result {
        End end = End.DONE;
        String error;
        Path file;
        long size;
        long blocks;
        int chunk;
        long elapsedMs;
        double avgBps;
        String crc32;
        final EnumMap<Algo, String> hex = new EnumMap<>(Algo.class);
        final EnumMap<Algo, Double> bps = new EnumMap<>(Algo.class);
    }

    interface Listener {
        void progress(long done, long total, double bps, long elapsedMs, long block, long blocks);

        void finished(Result r);
    }

    // ------------------------------------------------------------------ job
    static final class Job implements Runnable {
        private final Path path;
        private final EnumSet<Algo> algos;
        private final int chunk;
        private final Listener listener;
        private volatile boolean cancelled;
        private volatile boolean paused;
        volatile int threads = 1;

        Job(Path path, Set<Algo> algos, int chunk, Listener l) {
            this.path = path;
            this.algos = EnumSet.copyOf(algos);
            this.chunk = chunk;
            this.listener = l;
        }

        void cancel() { cancelled = true; }

        void setPaused(boolean p) { paused = p; }

        boolean isPaused() { return paused; }

        private record Chunk(byte[] buf, int len) { }

        @Override
        public void run() {
            Result res = new Result();
            res.file = path;
            res.chunk = chunk;
            ExecutorService pool = null;
            Thread reader = null;
            try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
                final long total = ch.size();
                res.size = total;
                res.blocks = Math.max(1, (total + chunk - 1) / chunk);
                List<Algo> list = new ArrayList<>(algos);
                MessageDigest[] md = new MessageDigest[list.size()];
                long[] nanos = new long[list.size()];
                for (int i = 0; i < md.length; i++) md[i] = MessageDigest.getInstance(list.get(i).label);
                CRC32 crc = new CRC32();
                if (md.length > 1) pool = Executors.newFixedThreadPool(md.length, r -> {
                    Thread t = new Thread(r, "hash-worker");
                    t.setDaemon(true);
                    return t;
                });
                threads = md.length + 1;

                final int depth = 6;
                BlockingQueue<byte[]> free = new ArrayBlockingQueue<>(depth + 1);
                for (int i = 0; i < depth; i++) free.add(new byte[chunk]);
                BlockingQueue<Chunk> ready = new ArrayBlockingQueue<>(depth + 1);
                final Throwable[] rerr = new Throwable[1];
                reader = new Thread(() -> {
                    try {
                        ByteBuffer bb;
                        while (!cancelled) {
                            byte[] b = free.poll(100, TimeUnit.MILLISECONDS);
                            if (b == null) continue;
                            bb = ByteBuffer.wrap(b);
                            int n = 0;
                            while (bb.hasRemaining()) {
                                int r = ch.read(bb);
                                if (r < 0) break;
                                n += r;
                            }
                            while (!cancelled && !ready.offer(new Chunk(b, n), 100, TimeUnit.MILLISECONDS)) { }
                            if (n < b.length) return;
                        }
                    } catch (Throwable t) {
                        rerr[0] = t;
                        ready.offer(new Chunk(null, -1));
                    }
                }, "hash-reader");
                reader.setDaemon(true);
                reader.start();

                long done = 0, block = 0;
                long active = 0;               // nanoseconds spent not paused
                long lastTick = System.nanoTime();
                long lastReport = 0;
                while (true) {
                    // pause handling
                    while (paused && !cancelled) {
                        Thread.sleep(40);
                        lastTick = System.nanoTime();
                    }
                    if (cancelled) { res.end = End.CANCELLED; break; }
                    Chunk c = ready.poll(100, TimeUnit.MILLISECONDS);
                    long now = System.nanoTime();
                    active += now - lastTick;
                    lastTick = now;
                    if (c == null) continue;
                    if (c.len() < 0) throw new IOException(rerr[0] != null ? rerr[0].getMessage() : "Read error");
                    if (c.len() > 0) {
                        final byte[] buf = c.buf();
                        final int len = c.len();
                        if (pool != null) {
                            List<Future<?>> fs = new ArrayList<>();
                            for (int i = 0; i < md.length; i++) {
                                final int k = i;
                                fs.add(pool.submit(() -> {
                                    long t0 = System.nanoTime();
                                    md[k].update(buf, 0, len);
                                    nanos[k] += System.nanoTime() - t0;
                                }));
                            }
                            crc.update(buf, 0, len);
                            for (Future<?> f : fs) f.get();
                        } else {
                            long t0 = System.nanoTime();
                            md[0].update(buf, 0, len);
                            nanos[0] += System.nanoTime() - t0;
                            crc.update(buf, 0, len);
                        }
                        done += len;
                        block++;
                    }
                    free.offer(c.buf());
                    long after = System.nanoTime();
                    active += after - lastTick;
                    lastTick = after;
                    if (after - lastReport > 70_000_000L || c.len() < chunk) {
                        lastReport = after;
                        double bps = active > 0 ? done / (active / 1e9) : 0;
                        listener.progress(done, total, bps, active / 1_000_000, block, res.blocks);
                    }
                    if (c.len() < chunk) {
                        res.elapsedMs = active / 1_000_000;
                        res.avgBps = active > 0 ? done / (active / 1e9) : 0;
                        for (int i = 0; i < md.length; i++) {
                            res.hex.put(list.get(i), hex(md[i].digest()));
                            res.bps.put(list.get(i), nanos[i] > 0 ? done / (nanos[i] / 1e9) : 0);
                        }
                        res.crc32 = String.format("%08X", crc.getValue());
                        res.end = End.DONE;
                        break;
                    }
                }
            } catch (Throwable t) {
                res.end = End.ERROR;
                res.error = t.getMessage() != null ? t.getMessage() : t.toString();
            } finally {
                cancelled = true;
                if (pool != null) pool.shutdownNow();
            }
            listener.finished(res);
        }
    }

    static String hex(byte[] b) {
        char[] h = "0123456789abcdef".toCharArray();
        char[] o = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            o[i * 2] = h[(b[i] >> 4) & 15];
            o[i * 2 + 1] = h[b[i] & 15];
        }
        return new String(o);
    }

    // ------------------------------------------------------------------ expected-hash parsing
    private static final Pattern HEXTOK = Pattern.compile("(?<![0-9A-Fa-f])[0-9A-Fa-f]{32,128}(?![0-9A-Fa-f])");
    private static final Pattern BSD = Pattern.compile("^\\s*(?:MD5|SHA-?1|SHA-?256|SHA-?512)\\s*\\((.+)\\)\\s*=\\s*([0-9A-Fa-f]+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GNU = Pattern.compile("^\\s*([0-9A-Fa-f]{32,128})\\s+[* ]?(.+?)\\s*$");

    /**
     * Pull a bare hex digest out of whatever the user pasted: bare hash, spaced/colon separated bytes,
     * GNU "hash *name" lines, BSD "SHA256 (name) = hash" lines, or a whole manifest (best match for file name).
     */
    static String extractHash(String raw, String fileName) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        String[] lines = t.split("\\R");
        if (lines.length > 1 || t.contains(" ")) {
            String firstHit = null;
            for (String ln : lines) {
                String l = ln.trim();
                if (l.isEmpty() || l.startsWith(";") || l.startsWith("#")) continue;
                String h = null, nm = null;
                Matcher b = BSD.matcher(l);
                Matcher g = GNU.matcher(l);
                if (b.matches()) { nm = b.group(1); h = b.group(2); }
                else if (g.matches()) { h = g.group(1); nm = g.group(2); }
                if (h != null) {
                    if (fileName != null && nm != null && nm.replace('\\', '/').endsWith(fileName)) return h;
                    if (firstHit == null) firstHit = h;
                }
            }
            if (firstHit != null) return firstHit;
        }
        String squeezed = t.replaceAll("(?i)^0x", "").replaceAll("[\\s:\\-]", "");
        if (squeezed.matches("[0-9A-Fa-f]+") && Algo.byLen(squeezed.length()) != null) return squeezed;
        Matcher m = HEXTOK.matcher(t);
        if (m.find() && Algo.byLen(m.group().length()) != null) return m.group();
        return t.replaceAll("\\s+", "");
    }

    enum VState { EMPTY, VALID, INVALID }

    record Validation(VState state, Algo algo, String message) { }

    static Validation validate(String normalized) {
        if (normalized == null || normalized.isEmpty())
            return new Validation(VState.EMPTY, null, "No expected checksum \u2014 digest will be calculated only");
        if (!normalized.matches("[0-9A-Fa-f]+"))
            return new Validation(VState.INVALID, null, "Invalid: contains non-hexadecimal characters");
        Algo a = Algo.byLen(normalized.length());
        if (a == null)
            return new Validation(VState.INVALID, null, "Invalid length: " + normalized.length() + " characters (expected 32, 40, 64 or 128)");
        return new Validation(VState.VALID, a, "Valid " + a.hexLen + "-character " + a.label + " hexadecimal hash");
    }

    /** Number of differing bits between two hex strings (the shorter is zero-padded). */
    static int hamming(String a, String b) {
        int n = Math.max(a.length(), b.length()), bits = 0;
        for (int i = 0; i < n; i++) {
            int x = i < a.length() ? Character.digit(a.charAt(i), 16) : 0;
            int y = i < b.length() ? Character.digit(b.charAt(i), 16) : 0;
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            bits += Integer.bitCount(x ^ y);
        }
        return bits;
    }

    static int charDiffs(String a, String b) {
        int n = Math.max(a.length(), b.length()), d = 0;
        for (int i = 0; i < n; i++) {
            if (i >= a.length() || i >= b.length() || Character.toLowerCase(a.charAt(i)) != Character.toLowerCase(b.charAt(i))) d++;
        }
        return d;
    }

    // ------------------------------------------------------------------ formatting
    static String size(long b) {
        if (b < 1024) return b + " B";
        double v = b;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = -1;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.2f %s", v, u[i]);
    }

    static String speed(double bps) {
        return String.format(Locale.US, "%.1f MB/s", bps / 1048576.0);
    }

    static String clock(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
