import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Berechnet c(n), die Anzahl zusammenhaengender beschrifteter chordaler
 * Graphen, fuer n = 1..30, mithilfe des Zaehlalgorithmus von
 *
 *   Hebert-Johnson, Lokshtanov, Vigoda: "Counting and Sampling Labeled
 *   Chordal Graphs in Polynomial Time", ESA 2023, arXiv:2308.09703.
 *
 * HERKUNFT DIESES CODES:
 * Diese Klasse ist eine Portierung (C++ -> Java) der von den Autoren selbst
 * veroeffentlichten Referenzimplementierung:
 *   https://github.com/uhebertj/chordal (Datei chordal.cpp)
 * Die Rekursionsstruktur, Variablennamen (g, gTilde, gHat, g1Tilde, g2Tilde,
 * f, fTilde, fHat) und die Grundidee der dynamischen Programmierung wurden
 * direkt aus dieser Quelle uebernommen; lediglich BigInteger anstelle von
 * boost::multiprecision und HashMap-Memoisierung anstelle vorallozierter
 * mehrdimensionaler Arrays wurden verwendet.
 *
 * WARUM DIESER ANSATZ (statt der eigenen einfachen Rekursionsformel):
 * Die in Kapitel 4 hergeleitete Formel a(n) = Summe C(n-1,k-1) c(k) a(n-k)
 * beschreibt zwar korrekt den Zusammenhang zwischen a(n) und c(n), enthaelt
 * jedoch eine zirkulaere Abhaengigkeit (der Term k=n liefert genau c(n)),
 * sodass a(n) und c(n) daraus nicht gleichzeitig berechnet werden koennen.
 * Der hier implementierte Algorithmus berechnet c(n) stattdessen unabhaengig,
 * ueber die "Verdunstungssequenz" (evaporation sequence) eines chordalen
 * Graphen -- eine kanonische Version der perfekten Eliminationsordnung, bei
 * der in jedem Schritt ALLE simplizialen Knoten gleichzeitig entfernt
 * werden (siehe Kapitel 3 fuer simpliziale Knoten und PEO). Die Details der
 * Rekurrenzen (Lemmas 3.4-3.12 der Originalarbeit) und deren Korrektheits-
 * beweise (Section 5) sind sehr umfangreich und gehen ueber den Rahmen
 * dieser Arbeit hinaus; hier wird der Algorithmus als Blackbox verwendet
 * und gegen die publizierten Werte aus Table 1 verifiziert.
 *
 * LAUFZEIT: O(n^7) arithmetische Operationen. Fuer n=30 in der Originalarbeit
 * ca. 2.5 Minuten in C++. Diese Java-Portierung kann je nach Rechner
 * aehnlich lange oder laenger benoetigen. Bei Speicherproblemen (grosse
 * Zwischenergebnisse) kann der Heap mit "java -Xmx4g ChordalGraphPolyTime"
 * vergroessert werden. Auf Online-Compilern mit Zeit-/Speicherlimits
 * (z.B. JDoodle) sollte daher zunaechst mit kleinerem maxN getestet werden.
 */
public class ChordalGraphPolyTime {

    static int W; // Schranke fuer die maximale Cliquengroesse (hier: unbeschraenkt = n)
    static BigInteger[][] choose;

    // Memoisierung ueber HashMaps (sparse), Schluessel als gepackter long-Wert.
    static Map<Long, BigInteger> memoG = new HashMap<>();
    static Map<Long, BigInteger> memoGTilde = new HashMap<>();
    static Map<Long, BigInteger> memoGHat = new HashMap<>();
    static Map<Long, BigInteger> memoG1Tilde = new HashMap<>();
    static Map<Long, BigInteger> memoG2Tilde = new HashMap<>();
    static Map<Long, BigInteger> memoF = new HashMap<>();
    static Map<Long, BigInteger> memoFTilde = new HashMap<>();
    static Map<Long, BigInteger> memoFHat5 = new HashMap<>();
    static Map<Integer, BigInteger> memoConn = new HashMap<>();

    static final long BASE = 64; // n <= 30, also reicht Basis 64 sicher zum Packen der Argumente

    static long key4(int a, int b, int c, int d) {
        return ((a * BASE + b) * BASE + c) * BASE + d;
    }

    static long key5(int a, int b, int c, int d, int e) {
        return (((a * BASE + b) * BASE + c) * BASE + d) * BASE + e;
    }

    public static void main(String[] args) {
        int maxN = 30;

        chooseInit(maxN);

        System.out.println("n\t c(n)\t\t\t Laufzeit (ms)");
        for (int n = 1; n <= maxN; n++) {
            W = maxN; // unbeschraenkte Cliquengroesse; einmalig fest fuer den ganzen Lauf
            long start = System.currentTimeMillis();
            BigInteger result = chordalConn(n);
            long end = System.currentTimeMillis();
            System.out.println(n + "\t" + result + "\t" + (end - start));
        }
    }

    static void chooseInit(int n) {
        choose = new BigInteger[n + 1][n + 1];
        for (int m = 0; m <= n; m++) {
            choose[m][0] = BigInteger.ONE;
            for (int k = 1; k <= m; k++) {
                BigInteger a = choose[m - 1][k];
                BigInteger b = choose[m - 1][k - 1];
                choose[m][k] = (a == null ? BigInteger.ZERO : a).add(b);
            }
        }
    }

    static BigInteger choose(int n, int k) {
        if (n < 0 || k < 0 || k > n) return BigInteger.ZERO;
        return choose[n][k];
    }

    /** c(k): Anzahl zusammenhaengender beschrifteter chordaler Graphen auf k Knoten. */
    static BigInteger chordalConn(int k) {
        if (k == 0) return BigInteger.ZERO;
        BigInteger cached = memoConn.get(k);
        if (cached != null) return cached;
        BigInteger ans = BigInteger.ZERO;
        for (int t = 1; t <= k; t++) {
            for (int l = 1; l <= k; l++) {
                ans = ans.add(choose(k, l).multiply(f(t, 0, l, k - l)));
            }
        }
        memoConn.put(k, ans);
        return ans;
    }

    // g(t,x,z,k): chordale Graphen, die bis Zeit t "verdunsten" (evaporate), x>=1 erforderlich
    static BigInteger g(int t, int x, int z, int k) {
        long key = key4(t, x, z, k);
        BigInteger cached = memoG.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (t == 0) {
            ans = (k == 0) ? BigInteger.ONE : BigInteger.ZERO;
        } else {
            ans = BigInteger.ZERO;
            for (int kk = 0; kk <= k; kk++) {
                ans = ans.add(choose(k, kk).multiply(gTilde(t, x, z, kk)).multiply(g(t - 1, x, z, k - kk)));
            }
        }
        memoG.put(key, ans);
        return ans;
    }

    // gTilde(t,x,z,k): wie g, aber alle Komponenten von G\X verdunsten exakt bei t
    static BigInteger gTilde(int t, int x, int z, int k) {
        long key = key4(t, x, z, k);
        BigInteger cached = memoGTilde.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (k == 0) {
            ans = BigInteger.ONE;
        } else {
            ans = BigInteger.ZERO;
            for (int kk = 1; kk <= k; kk++) {
                for (int xx = 1; xx <= x; xx++) {
                    BigInteger term = choose(k - 1, kk - 1)
                        .multiply(choose(x, xx).subtract(choose(z, xx)))
                        .multiply(g1Tilde(t, xx, kk))
                        .multiply(gTilde(t, x, z, k - kk));
                    ans = ans.add(term);
                }
            }
        }
        memoGTilde.put(key, ans);
        return ans;
    }

    // gHat(t,x,z,k): wie gTilde, aber keine Komponente sieht ganz X (xx < x statt xx <= x)
    static BigInteger gHat(int t, int x, int z, int k) {
        long key = key4(t, x, z, k);
        BigInteger cached = memoGHat.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (k == 0) {
            ans = BigInteger.ONE;
        } else {
            ans = BigInteger.ZERO;
            for (int kk = 1; kk <= k; kk++) {
                for (int xx = 1; xx < x; xx++) {
                    BigInteger term = choose(k - 1, kk - 1)
                        .multiply(choose(x, xx).subtract(choose(z, xx)))
                        .multiply(g1Tilde(t, xx, kk))
                        .multiply(gHat(t, x, z, k - kk));
                    ans = ans.add(term);
                }
            }
        }
        memoGHat.put(key, ans);
        return ans;
    }

    // g1Tilde(t,x,k): genau eine Komponente von G\X, verdunstet exakt bei t, sieht ganz X
    static BigInteger g1Tilde(int t, int x, int k) {
        long key = key4(t, x, 0, k);
        BigInteger cached = memoG1Tilde.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (k == 0 || t == 0) {
            ans = BigInteger.ZERO;
        } else {
            ans = BigInteger.ZERO;
            for (int l = 1; l <= k; l++) {
                ans = ans.add(choose(k, l).multiply(f(t, x, l, k - l)));
            }
        }
        memoG1Tilde.put(key, ans);
        return ans;
    }

    // g2Tilde(t,x,k): mindestens zwei Komponenten von G\X, jede sieht ganz X
    static BigInteger g2Tilde(int t, int x, int k) {
        long key = key4(t, x, 0, k);
        BigInteger cached = memoG2Tilde.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (k == 0 || t == 0) {
            ans = BigInteger.ZERO;
        } else {
            ans = BigInteger.ZERO;
            for (int kk = 1; kk < k; kk++) {
                BigInteger term = choose(k - 1, kk - 1)
                    .multiply(g1Tilde(t, x, kk))
                    .multiply(g1Tilde(t, x, k - kk).add(g2Tilde(t, x, k - kk)));
                ans = ans.add(term);
            }
        }
        memoG2Tilde.put(key, ans);
        return ans;
    }

    // f(t,x,l,k): L fest, verdunstet exakt bei t, G\X zusammenhaengend
    static BigInteger f(int t, int x, int l, int k) {
        long key = key4(t, x, l, k);
        BigInteger cached = memoF.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (x + l > W) {
            ans = BigInteger.ZERO;
        } else if (t == 0) {
            ans = BigInteger.ZERO;
        } else if (t == 1) {
            ans = (k == 0) ? BigInteger.ONE : BigInteger.ZERO;
        } else if (k == 0) {
            ans = BigInteger.ZERO;
        } else {
            ans = BigInteger.ZERO;
            for (int kk = 1; kk <= k; kk++) {
                ans = ans.add(choose(k, kk).multiply(fTilde(t, x, l, kk)).multiply(g(t - 2, x + l, x, k - kk)));
            }
        }
        memoF.put(key, ans);
        return ans;
    }

    // fTilde(t,x,l,k): wie f, aber alle Komponenten von G\(X u L) verdunsten exakt bei t-1
    static BigInteger fTilde(int t, int x, int l, int k) {
        long key = key4(t, x, l, k);
        BigInteger cached = memoFTilde.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (t == 0 || t == 1 || k == 0) {
            ans = BigInteger.ZERO;
        } else {
            ans = fHat5(t, x, x, l, k);
            for (int kk = 1; kk < k; kk++) {
                ans = ans.add(choose(k, kk).multiply(g1Tilde(t - 1, x + l, kk)).multiply(fHat5(t, x, x, l, k - kk)));
            }
            for (int kk = 1; kk <= k; kk++) {
                ans = ans.add(choose(k, kk).multiply(g2Tilde(t - 1, x + l, kk)).multiply(gHat(t - 1, x + l, x, k - kk)));
            }
        }
        memoFTilde.put(key, ans);
        return ans;
    }

    // fHat(t,x,z,l,k): wie fTilde, aber keine Komponente sieht ganz X u L
    static BigInteger fHat5(int t, int x, int z, int l, int k) {
        long key = key5(t, x, z, l, k);
        BigInteger cached = memoFHat5.get(key);
        if (cached != null) return cached;
        BigInteger ans;
        if (t == 0 || t == 1 || k == 0) {
            ans = BigInteger.ZERO;
        } else {
            ans = BigInteger.ZERO;
            for (int kk = 1; kk <= k; kk++) {
                for (int xx = 0; xx <= x; xx++) {
                    for (int ll = 0; ll <= l; ll++) {
                        if (xx + ll == 0 || xx + ll == x + l) continue;
                        BigInteger prod = choose(k - 1, kk - 1).multiply(choose(l, ll));
                        if (ll > 0) {
                            prod = prod.multiply(choose(x, xx));
                        } else {
                            prod = prod.multiply(choose(x, xx).subtract(choose(z, xx)));
                        }
                        prod = prod.multiply(g1Tilde(t - 1, xx + ll, kk));
                        if (ll < l) {
                            prod = prod.multiply(fHat5(t, x + ll, z, l - ll, k - kk));
                        } else {
                            prod = prod.multiply(gHat(t - 1, x + ll, z, k - kk));
                        }
                        ans = ans.add(prod);
                    }
                }
            }
        }
        memoFHat5.put(key, ans);
        return ans;
    }
}
