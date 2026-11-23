package xuro;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Http {
    private Http() {}

    /** Fortschrittsmeldung an die Oberfläche. */
    public interface Prog {
        void status(String s);
        void frac(double f);
        Prog NONE = new Prog() { public void status(String s) {} public void frac(double f) {} };
    }

    /** Eine herunterzuladende Datei. sha1 und size sind optional. */
    public static final class Dl {
        public final String url; public final Path dest; public final String sha1; public final long size;
        public Dl(String url, Path dest, String sha1, long size) { this.url = url; this.dest = dest; this.sha1 = sha1; this.size = size; }
    }

    static final String UA = "XuroClient/" + Main.VERSION + " (xurosmp.daronlo.com)";
    static final HttpClient C = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15)).build();

    public static String get(String url, String... headers) throws IOException {
        return send(builder(url, headers).GET().build());
    }

    public static String post(String url, String body, String ctype, String... headers) throws IOException {
        HttpRequest.Builder b = builder(url, headers).header("Content-Type", ctype).POST(HttpRequest.BodyPublishers.ofString(body));
        return send(b.build());
    }

    /** Wie post, liefert aber auch bei HTTP-Fehlern den Body (für OAuth-Antworten). */
    public static String postLenient(String url, String body, String ctype, String... headers) throws IOException {
        HttpRequest r = builder(url, headers).header("Content-Type", ctype).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            return C.send(r, HttpResponse.BodyHandlers.ofString()).body();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    private static HttpRequest.Builder builder(String url, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", UA).header("Accept", "application/json, */*");
        for (int i = 0; i + 1 < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return b;
    }

    private static String send(HttpRequest r) throws IOException {
        try {
            HttpResponse<String> res = C.send(r, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode() + " bei " + r.uri().getHost() + r.uri().getPath());
            return res.body();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }

    public static String sha1(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }

    /** True, wenn die Datei schon da und (falls bekannt) korrekt ist. */
    public static boolean ok(Dl d) {
        try {
            if (!Files.isRegularFile(d.dest)) return false;
            if (d.sha1 != null && !d.sha1.isEmpty()) return d.sha1.equalsIgnoreCase(sha1(d.dest));
            return d.size <= 0 || Files.size(d.dest) == d.size;
        } catch (IOException e) { return false; }
    }

    public static void file(Dl d) throws IOException {
        if (ok(d)) return;
        IOException last = null;
        for (int t = 0; t < 3; t++) {
            try {
                Files.createDirectories(d.dest.getParent());
                Path tmp = d.dest.resolveSibling(d.dest.getFileName() + ".part");
                HttpResponse<InputStream> res = C.send(builder(d.url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() / 100 != 2) { res.body().close(); throw new IOException("HTTP " + res.statusCode() + " " + d.url); }
                try (InputStream in = res.body()) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                if (d.sha1 != null && !d.sha1.isEmpty() && !d.sha1.equalsIgnoreCase(sha1(tmp))) {
                    Files.deleteIfExists(tmp);
                    throw new IOException("Prüfsumme falsch: " + d.dest.getFileName());
                }
                Files.move(tmp, d.dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e);
            } catch (IOException e) { last = e; }
        }
        throw last;
    }

    /** Lädt viele Dateien parallel herunter und meldet den Fortschritt. */
    public static void all(List<Dl> list, String label, Prog p) throws IOException {
        if (list.isEmpty()) return;
        ExecutorService ex = Executors.newFixedThreadPool(8);
        AtomicInteger done = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        int total = list.size();
        for (Dl d : list) fs.add(ex.submit(() -> {
            file(d);
            int n = done.incrementAndGet();
            if (n % 10 == 0 || n == total) { p.status(label + " (" + n + "/" + total + ")"); p.frac((double) n / total); }
            return null;
        }));
        try {
            for (Future<?> f : fs) f.get();
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof IOException ? (IOException) c : new IOException(c);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
        finally { ex.shutdownNow(); }
    }
}
