package net.pieroxy.mom.standalone;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.pieroxy.mom.detection.reputation.IpTrie;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * CPU + memory benchmark: loads an IPv4 CIDR range file (lz4-compressed, like
 * {@code dataFolder/reputation/<id>.txt.lz4}) into a plain [start,end] list — the current
 * approach used by {@code IpReputationList}/{@code CidrRange}, which compares the tested IP
 * against every range (O(n)) — and into an {@link IpTrie}, then compares the lookup time of both
 * over a large number of random IPs (the realistic scenario: overwhelmingly "not present", since
 * the vast majority of mail doesn't come from a blacklisted IP). Usage:
 * <pre>java -cp mom-core-*.jar net.pieroxy.mom.standalone.IpTrieBenchmark &lt;file.txt.lz4&gt; [lookup count]</pre>
 */
public final class IpTrieBenchmark {
  private static final int DEFAULT_LOOKUPS = 2_000_000;

  private IpTrieBenchmark() {}

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("Usage: IpTrieBenchmark <file.txt.lz4> [lookupCount]");
      System.exit(1);
    }
    int lookupCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_LOOKUPS;

    // {start, end, prefixLength} per range.
    List<long[]> ranges = readRanges(new File(args[0]));
    System.out.println(ranges.size() + " CIDR range(s) read from " + args[0]);

    long baseline = usedMemory();

    List<long[]> flatRanges = new ArrayList<>(ranges);
    long afterFlat = usedMemory();
    System.out.println("Linear list (" + flatRanges.size() + " ranges): "
        + format(afterFlat - baseline) + " above baseline");

    IpTrie trie = new IpTrie();
    for (long[] r : ranges) {
      trie.add(r[0], (int) r[2]);
    }
    long afterTrie = usedMemory();
    System.out.println("IpTrie (" + ranges.size() + " ranges): "
        + format(afterTrie - afterFlat) + " on top of the linear list above");

    long[] queries = randomIps(lookupCount);

    long linearMatches = 0;
    long linearStart = System.nanoTime();
    for (long ip : queries) {
      if (containsLinear(flatRanges, ip)) linearMatches++;
    }
    long linearNanos = System.nanoTime() - linearStart;

    long trieMatches = 0;
    long trieStart = System.nanoTime();
    for (long ip : queries) {
      if (trie.contains(ip)) trieMatches++;
    }
    long trieNanos = System.nanoTime() - trieStart;

    System.out.println();
    System.out.println(lookupCount + " lookup(s), random IPs (fixed seed, same queries for both):");
    System.out.printf(Locale.ROOT, "  Linear scan: %d match(es), %.0f ms total, %.1f ns/lookup%n",
        linearMatches, linearNanos / 1_000_000.0, (double) linearNanos / lookupCount);
    System.out.printf(Locale.ROOT, "  IpTrie:      %d match(es), %.0f ms total, %.1f ns/lookup%n",
        trieMatches, trieNanos / 1_000_000.0, (double) trieNanos / lookupCount);
    if (linearMatches != trieMatches) {
      System.out.println("  WARNING: match counts differ between the two implementations — something is wrong.");
    }
  }

  private static boolean containsLinear(List<long[]> ranges, long ip) {
    for (long[] r : ranges) {
      if (ip >= r[0] && ip <= r[1]) return true;
    }
    return false;
  }

  private static long[] randomIps(int count) {
    Random random = new Random(42); // fixed seed: same IPs queried run to run, and for both structures
    long[] ips = new long[count];
    for (int i = 0; i < count; i++) {
      ips[i] = random.nextInt() & 0xFFFFFFFFL;
    }
    return ips;
  }

  private static List<long[]> readRanges(File file) throws IOException {
    List<long[]> ranges = new ArrayList<>();
    try (Reader r = new InputStreamReader(new LZ4FrameInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      StringBuilder sb = new StringBuilder();
      char[] buf = new char[8192];
      int n;
      while ((n = r.read(buf)) != -1) {
        sb.append(buf, 0, n);
      }
      for (String raw : sb.toString().split("\\r?\\n")) {
        String line = stripComment(raw).trim();
        if (line.isEmpty()) continue;
        try {
          ranges.add(parseCidr(line));
        } catch (RuntimeException e) {
          // invalid line: skipped, same as in production (see ReputationListParser)
        }
      }
    }
    return ranges;
  }

  private static String stripComment(String line) {
    int hash = line.indexOf('#');
    int semi = line.indexOf(';');
    int cut = hash < 0 ? semi : (semi < 0 ? hash : Math.min(hash, semi));
    return cut < 0 ? line : line.substring(0, cut);
  }

  private static long[] parseCidr(String text) {
    int slash = text.indexOf('/');
    String ipPart = slash < 0 ? text : text.substring(0, slash);
    int prefixLength = slash < 0 ? 32 : Integer.parseInt(text.substring(slash + 1));
    long ip = ipToLong(ipPart);
    long mask = prefixLength == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
    long start = ip & mask;
    long end = start | (~mask & 0xFFFFFFFFL);
    return new long[]{start, end, prefixLength};
  }

  private static long ipToLong(String ip) {
    String[] parts = ip.split("\\.", -1);
    if (parts.length != 4) throw new IllegalArgumentException("Not an IPv4 address: " + ip);
    long result = 0;
    for (String part : parts) {
      int octet = Integer.parseInt(part);
      if (octet < 0 || octet > 255) throw new IllegalArgumentException("Not an IPv4 address: " + ip);
      result = (result << 8) | octet;
    }
    return result;
  }

  private static long usedMemory() {
    Runtime rt = Runtime.getRuntime();
    for (int i = 0; i < 2; i++) {
      System.gc();
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    return rt.totalMemory() - rt.freeMemory();
  }

  private static String format(long bytes) {
    return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
  }
}
