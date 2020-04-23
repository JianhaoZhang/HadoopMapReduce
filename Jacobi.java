import java.io.IOException;
import java.util.*;
import java.lang.InterruptedException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.*;

import org.apache.commons.logging.Log;


public class Jacobi {
    public enum ConvergeCounter {INCONVERGED}



    /* Input
     *   < offset, "i, j, M[i][j]" >
     *   < offset, "i, N, b[i]" >
     *   shared < offset, "i, x[i]" > i = 0..N-1
     * Output
     *   < i, "N, b[i]" >
     *   < i, "i, M[i][i]" >
     *   < i, "j, x[j]*M[i][j]" > i != j
     */
    public static class Map extends Mapper<Object, Text, IntWritable, Text> {

        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            int N = Integer.parseInt(conf.get("matrixSize"));
            int it = Integer.parseInt(conf.get("iteration"));
            Path sharedFile = new Path(conf.get("sharedFile"));
            String[] strArr;
            double[] x = new double[N];
            int i, j;

            if (it > 0) {
                FileSystem fs = FileSystem.get(context.getConfiguration());
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(sharedFile)));
                String line;
                line = br.readLine();
                while (line != null) {
                    strArr = line.split("\\s+");
                    x[Integer.parseInt(strArr[0])] = Double.parseDouble(strArr[1]);
                    line = br.readLine();
                }
                br.close();
            }

            strArr = value.toString().split("\\s+");
            i = Integer.parseInt(strArr[0]);
            j = Integer.parseInt(strArr[1]);

            if (j == N) {
                context.write(new IntWritable(i), new Text(strArr[1]+"\t"+strArr[2]));
            } else {
                if (i != j) {
                    double Mij = Double.parseDouble(strArr[2]);
                    if (Mij != 0 && x[j] != 0) {
                        context.write(new IntWritable(i), new Text(strArr[1]+"\t"+String.valueOf(Mij*x[j])));
                    }
                } else {
                    context.write(new IntWritable(i), new Text(strArr[1]+"\t"+strArr[2]));
                }
            }
        }
    }




    /* Input
     *   < i, "N, b[i]" >
     *   < i, "i, M[i][j]" >
     *   < i, "j, M[i][j]*x[j]" > i != j
     * Output
     *   < i, "N, b[i]" >
     *   < i, "i, M[i][j]" >
     *   < i, "N+1, sum(M[i][j]*x[j])" >
     */
    public static class Combine extends Reducer<IntWritable, Text, IntWritable, Text> {
        public void reduce(IntWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            int N = Integer.parseInt(conf.get("matrixSize"));
            int i = key.get();
            double sum = 0.0;

            for (Text value: values) {
                String[] strArr = value.toString().split("\\s+");
                int j = Integer.parseInt(strArr[0]);

                if (i == j || j == N) {
                    context.write(new IntWritable(i), new Text(value.toString()));
                } else {
                    sum += Double.parseDouble(strArr[1]);
                }
            }

            if (sum != 0) {
                context.write(new IntWritable(i), new Text(String.valueOf(N+1)+"\t"+String.valueOf(sum)));
            }
        }
    }






    /* Input
     *   < i, "N, b[i]" >
     *   shared < i, "x[i]" > i = 0..N-1
     *   < i, "i, M[i][i]" >
     *   < i, "N+1, sum(M[i][j]*x[j])" >
     * Output
     *   < i, "(b[i]-sum(M[i][j]*x[j])/M[i][i])" >
     */
    public static class Reduce extends Reducer<IntWritable, Text, IntWritable, Text> {
        public void reduce(IntWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            int N = Integer.parseInt(conf.get("matrixSize"));
            int it = Integer.parseInt(conf.get("iteration"));
            Path sharedFile = new Path(conf.get("sharedFile"));
            String[] strArr;
            int i = key.get();
            double bi = 0.0;
            double Mii = 0.0;
            double sum = 0.0;
            double xi = 0.0;

            if (it > 0) {
                FileSystem fs = FileSystem.get(context.getConfiguration());
                BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(sharedFile)));
                String line;
                line = br.readLine();
                while (line != null) {
                    strArr = line.split("\\s+");
                    if (Integer.parseInt(strArr[0]) == i) {
                        xi = Double.parseDouble(strArr[1]);
                        break;
                    }
                    line = br.readLine();
                }
                br.close();
            }

            for (Text value: values) {
                strArr = value.toString().split("\\s+");
                int j = Integer.parseInt(strArr[0]);

                if (j == N) {
                    bi = Double.parseDouble(strArr[1]);
                } else if (j == N+1) {
                    sum += Double.parseDouble(strArr[1]);
                } else {
                    Mii = Double.parseDouble(strArr[1]);
                }
            }

            sum = (bi - sum) / Mii;

            /* Check convergence here. */

            if (sum != 0) {
                context.write(new IntWritable(i), new Text(String.valueOf(sum)));
            }
        }
    }









    public static void main(String[] args) throws Exception
    {
        String matrixSize = args[0];
        int maxIt = Integer.parseInt(args[1]);
        int reducers = Integer.parseInt(args[2]);
        String inputPath = args[3];
        String outputPath = args[4];
        Configuration conf;
        Job job;

        for (int i = 0; i < maxIt; i++)
        {
            conf = new Configuration();
            conf.set("matrixSize", matrixSize);
            conf.set("iteration", String.valueOf(i));
            conf.set("sharedFile", outputPath+"/"+Integer.toString(i)+"/part-r-00000");

            job = Job.getInstance(conf, "Jacobi");
            job.setNumReduceTasks(reducers);
            job.setJarByClass(Jacobi.class);
            job.setMapperClass(Map.class);
            //job.setCombinerClass(Combine.class);
            job.setReducerClass(Reduce.class);

            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(Text.class);

            FileInputFormat.setInputPaths(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, new Path(outputPath+"/"+Integer.toString(i+1)));

            job.waitForCompletion(true);




            /*           if ((int)job.getCounters().findCounter(ConvergeCounter.INCONVERGED).getValue() == 0)
            {
                break;
            }
            */

        }
    }
}