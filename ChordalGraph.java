import java.math.BigInteger;
import java.util.*;

/**
 * Berechnet a(n) (Anzahl aller beschrifteten chordalen Graphen) durch
 * vollstaendige Enumeration (Brute-Force) aller moeglichen Graphen auf n
 * Knoten, unter Verwendung der eigenen (korrigierten) LexBFS-Implementierung
 * zur Chordalitaetspruefung. Aus a(n) wird anschliessend c(n) mithilfe der
 * in Kapitel 4 hergeleiteten Rekursionsformel extrahiert:
 *
 *   c(n) = a(n) - Summe_{k=1}^{n-1} C(n-1,k-1) * c(k) * a(n-k)
 *
 * WICHTIG - Laufzeit waechst extrem schnell:
 * Es gibt 2^(n ueber 2) moegliche Graphen auf n Knoten. Das bedeutet:
 *   n=5  ->      1.024 Graphen   (sofort)
 *   n=6  ->     32.768 Graphen   (sofort)
 *   n=7  ->  2.097.152 Graphen   (wenige Sekunden)
 *   n=8  -> 268.435.456 Graphen  (mehrere Minuten)
 *   n=9  -> bereits ueber 68 Milliarden Graphen (nicht mehr praktikabel)
 *
 * Diese Brute-Force-Methode ist daher NUR fuer kleine n (bis etwa n=8)
 * geeignet. Fuer groessere n (bis n=30) sind die Werte aus Table 1 von
 * Hebert-Johnson, Lokshtanov, Vigoda (ESA 2023, arXiv:2308.09703) zu
 * verwenden, die auf einem wesentlich effizienteren Algorithmus beruhen.
 *
 * Der Zweck dieser Klasse ist es, die Rekursionsformel aus Kapitel 4 mit
 * tatsaechlich selbst berechneten (nicht nur zitierten) Werten zu
 * verifizieren.
 */
public class ChordalGraphBruteForce {

    static int n;
    static boolean[][] adj;

    public static void main(String[] args) {
        int maxN = 7; // sicherer Standardwert; n=8 dauert bereits mehrere Minuten

        BigInteger[] a = new BigInteger[maxN + 1];
        BigInteger[] c = new BigInteger[maxN + 1];
        a[0] = BigInteger.ONE;

        System.out.println("Berechnung von a(n) durch Brute-Force-Enumeration:");
        System.out.println("n\t a(n)\t\t Laufzeit (ms)");
        for (n = 1; n <= maxN; n++) {
            long start = System.currentTimeMillis();
            a[n] = BigInteger.valueOf(countChordalGraphsBruteForce(n));
            long end = System.currentTimeMillis();
            System.out.println(n + "\t" + a[n] + "\t\t" + (end - start));
        }

        System.out.println();
        System.out.println("Extraktion von c(n) aus a(n) (Formel aus Kapitel 4):");
        System.out.println("n\t c(n)");
        for (int m = 1; m <= maxN; m++) {
            BigInteger sum = BigInteger.ZERO;
            for (int k = 1; k < m; k++) {
                BigInteger binom = binomial(m - 1, k - 1);
                sum = sum.add(binom.multiply(c[k]).multiply(a[m - k]));
            }
            c[m] = a[m].subtract(sum);
            System.out.println(m + "\t" + c[m]);
        }

        System.out.println();
        System.out.println("Kontrolle gegen Table 1 (Hebert-Johnson et al. 2023):");
        System.out.println("erwartet: c(1)=1, c(2)=1, c(3)=4, c(4)=35, c(5)=541, c(6)=13302, c(7)=489287");
    }

    /**
     * Zaehlt per Brute-Force, wie viele der 2^(n ueber 2) moeglichen
     * beschrifteten Graphen auf n Knoten chordal sind.
     */
    static long countChordalGraphsBruteForce(int nn) {
        n = nn;
        int numPairs = n * (n - 1) / 2;
        int[][] pairs = new int[numPairs][2];
        int idx = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                pairs[idx++] = new int[]{i, j};

        long total = 1L << numPairs;
        long count = 0;

        for (long mask = 0; mask < total; mask++) {
            adj = new boolean[n][n];
            for (int p = 0; p < numPairs; p++) {
                if ((mask & (1L << p)) != 0) {
                    int u = pairs[p][0], v = pairs[p][1];
                    adj[u][v] = true;
                    adj[v][u] = true;
                }
            }
            if (isChordal()) count++;
        }
        return count;
    }

    // ---- LexBFS-Implementierung (Partition-Refinement), siehe Kapitel 5 ----

    static int[] lexBFS() {
        List<List<Integer>> partition = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < n; i++) all.add(i);
        partition.add(all);

        int[] order = new int[n];
        boolean[] visited = new boolean[n];

        for (int i = 0; i < n; i++) {
            int v = partition.get(0).remove(0);
            if (partition.get(0).isEmpty()) partition.remove(0);
            order[i] = v;
            visited[v] = true;

            List<List<Integer>> newPartition = new ArrayList<>();
            for (List<Integer> group : partition) {
                List<Integer> neighbors = new ArrayList<>();
                List<Integer> nonNeighbors = new ArrayList<>();
                for (int u : group) {
                    if (!visited[u]) {
                        if (adj[v][u]) neighbors.add(u);
                        else nonNeighbors.add(u);
                    }
                }
                if (!neighbors.isEmpty()) newPartition.add(neighbors);
                if (!nonNeighbors.isEmpty()) newPartition.add(nonNeighbors);
            }
            partition = newPartition;
        }
        return order;
    }

    /**
     * Prueft, ob der aktuelle Graph (adj) chordal ist.
     * WICHTIG: Es wird die UMKEHRUNG der LexBFS-Reihenfolge verwendet
     * (siehe Kapitel 5.1: "Die Umkehrung der LexBFS-Reihenfolge ist eine
     * perfekte Eliminationsordnung").
     */
    static boolean isChordal() {
        int[] order = lexBFS();
        int[] reversed = new int[n];
        for (int i = 0; i < n; i++) reversed[i] = order[n - 1 - i];

        int[] pos = new int[n];
        for (int i = 0; i < n; i++) pos[reversed[i]] = i;

        for (int i = 0; i < n; i++) {
            int v = reversed[i];
            List<Integer> rightNeighbors = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                if (adj[v][u] && pos[u] > i) rightNeighbors.add(u);
            }
            if (!rightNeighbors.isEmpty()) {
                int w = rightNeighbors.get(0);
                for (int u : rightNeighbors) {
                    if (pos[u] < pos[w]) w = u;
                }
                for (int u : rightNeighbors) {
                    if (u != w && !adj[w][u]) return false;
                }
            }
        }
        return true;
    }

    static BigInteger binomial(int n, int k) {
        if (k > n || k < 0) return BigInteger.ZERO;
        if (k == 0 || k == n) return BigInteger.ONE;
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < k; i++) {
            result = result.multiply(BigInteger.valueOf(n - i));
            result = result.divide(BigInteger.valueOf(i + 1));
        }
        return result;
    }
}
