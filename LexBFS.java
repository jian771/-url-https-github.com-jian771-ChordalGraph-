import java.util.*;

public class LexBFS {

    static int n;
    static boolean[][] adj;

    public static void main(String[] args) {
        System.out.println("n\t Laufzeit (ms)\t chordal?");

        for (n = 1; n <= 30; n++) {
            adj = new boolean[n][n];
            Random rand = new Random(42);
            for (int i = 0; i < n; i++)
                for (int j = i + 1; j < n; j++)
                    if (rand.nextBoolean()) {
                        adj[i][j] = true;
                        adj[j][i] = true;
                    }

            long start = System.currentTimeMillis();
            int[] order = lexBFS();

            // KORREKTUR: Die Umkehrung der LexBFS-Reihenfolge ist eine
            // perfekte Eliminationsordnung (nicht die Reihenfolge selbst!).
            // Siehe Kapitel 5.1: "Die Umkehrung der LexBFS-Reihenfolge ist
            // eine perfekte Eliminationsordnung [7, 9]."
            int[] reversedOrder = reverse(order);
            boolean isChordal = isChordal(reversedOrder);

            long end = System.currentTimeMillis();

            System.out.println(n + "\t" + (end - start) + "\t\t" +
                (isChordal ? "Ja" : "Nein"));
        }
    }

    static int[] reverse(int[] order) {
        int len = order.length;
        int[] reversed = new int[len];
        for (int i = 0; i < len; i++) {
            reversed[i] = order[len - 1 - i];
        }
        return reversed;
    }

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

    static boolean isChordal(int[] order) {
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) pos[order[i]] = i;

        for (int i = 0; i < n; i++) {
            int v = order[i];
            List<Integer> rightNeighbors = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                if (adj[v][u] && pos[u] > i) rightNeighbors.add(u);
            }
            if (!rightNeighbors.isEmpty()) {
                int w = rightNeighbors.stream()
                    .min(Comparator.comparingInt(x -> pos[x])).get();
                for (int u : rightNeighbors) {
                    if (u != w && !adj[w][u]) return false;
                }
            }
        }
        return true;
    }
}
