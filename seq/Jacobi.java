/*
  Modified from http://rendon.x10.mx/?p=189
  Assumes strictly diagonaly dominant matrices as input
*/

import java.io.*;
import java.util.Arrays;
import java.util.StringTokenizer;

public class Jacobi {

    public static final boolean verbose = true;
    public static final int print_n  = 5;
    public static final int max_iter = 100;
    public static final double eps = 1e-10;

    private double[][] Ab;
    int n;

    public Jacobi(double [][] matrix,  int n) {
        Ab = new double[n][n+1];
        this.n = n;
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                Ab[i][j] = matrix[i][j];
                sum += Math.abs(Ab[i][j]);
            }
            Ab[i][n] = matrix[i][n];
            if (2 * Math.abs(Ab[i][i]) <= sum) {
                System.out.println("Input matrix is not SDD");
                System.out.format("i = %d, Ab[%d][%d]=%f, sum=%f\n",
                                  i, i, i, 2 * Math.abs(Ab[i][i]), sum);
            }
        }
    }

    public void print()
    {
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if(Ab[i][j] != 0)
                    System.out.format("%-8d %-8d %.5f\n", i, j, Ab[i][j]);
            }
        }
        System.out.format("%d %d %d\n", 0, 0, 0);
    }

    public void solve()
    {
        int iter = 0;
        double[] X = new double[n]; // Approximations
        double[] P = new double[n]; // Prev
        Arrays.fill(X, 0);
        Arrays.fill(P, 0);

        while (iter++ < max_iter) {
            if(verbose)
                System.out.println("************************");
            for (int i = 0; i < n; i++) {
                double sum = 0;

                for (int j = 0; j < n; j++)
                    if (j != i)
                        sum -= Ab[i][j] * P[j];
                sum  += Ab[i][n]; // B[n]
                // System.out.format("sum %f \n", sum);
                X[i]  = sum/Ab[i][i];
            }

            if(verbose) {
                System.out.format("iteration %5d X = [", iter);
                if(n < 2*print_n) {
                    for (int i = 0; i < n; i++)
                        System.out.format(" %20.3f ", X[i]);
                    System.out.println("]");
                } else {
                    for (int i = 0; i < print_n; i++)
                        System.out.format(" %20.3f ", X[i]);
                    System.out.format(" ... ");
                    for (int i = n-print_n; i < n; i++)
                        System.out.format(" %20.3f ", X[i]);
                    System.out.println("]");
                }
            }

            if (iter == 1)
                continue;

            boolean stop = true;
            for (int i = 0; i < n && stop; i++)
                if (Math.abs(X[i] - P[i]) > eps)
                    stop = false;
            if (stop)
                break;

            P = (double[])X.clone();
        }

        System.out.print("Converged after " + iter + " iterations ");
        System.out.format("with a convergence threshold set at %e\n", eps);
        if(verbose) {
            for (int i = 0; i < n; i++)
                System.out.format("%-10d \t %-5.5f\n", i, X[i]);
        }
    }

    public static void main(String[] args) throws IOException
    {
        int n;
        double[][] matrix;

        if(args.length == 0) {
            System.err.println("Usage:  java Jacobi <file.dat>\n" +
                               "where <file.dat> the input matrix file");
            return;
        }

        // assuming input file: path/n.dat
        // where n is the matrix A [nxn] , vector b[n] size
        // matrix A and vector b are provided as a single [n X n+1]
        // matrix, where b takes up the last column
        BufferedReader br = new BufferedReader(new FileReader(args[0]));
        int prefix = args[0].lastIndexOf('/') + 1;
        int suffix = args[0].lastIndexOf('.');
        n = Integer.parseInt(args[0].substring(prefix, suffix));
        matrix = new double[n][n+1];
        try {
            String line = br.readLine();
            while(line != null) {
                String[] tokens = line.trim().split("\\s+");
                if(tokens.length != 3)
                    throw new IOException("Input file has improper format");
                int x = Integer.parseInt(tokens[0]);
                int y = Integer.parseInt(tokens[1]);
                double val = Double.parseDouble(tokens[2]);
                if(verbose)
                    System.out.format("[%d,%d]=%.3f\n", x, y, val);
                matrix[x][y] = val;
                line = br.readLine();
            }
        } finally {
            br.close();
        }

        if(verbose)
            System.out.println("***********************************************");

        Jacobi jacobi = new Jacobi(matrix, n);

        jacobi.print();
        jacobi.solve();
    }
}
