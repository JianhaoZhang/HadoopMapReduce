import java.io.IOException;
import java.util.*;
import java.lang.InterruptedException;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.*;

import org.apache.commons.logging.Log;


public class Jacobi{

    public enum ConvergeCounter {NOTCONVERGED}

    public static class JacobiMapper extends Mapper<Object, Text, IntWritable, Text> {
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            int N = Integer.parseInt(conf.get("matrixSize"));
            String[] strArr = value.toString().split("\\s+");
            int i = Integer.parseInt(strArr[0]);

            if (strArr.length == 3) {
                context.write(new IntWritable(i), new Text(strArr[1]+" "+strArr[2]));
            } else {
                for (int j = 0; j < N; j++) {
                    if (j != i) {
                        context.write(new IntWritable(j), new Text(String.valueOf(i)+"\tx"+strArr[1]));
                    }
                }
            }
        }
    }

    public static class JacobiReducer extends Reducer<IntWritable, Text, IntWritable, Text> {

        public void reduce(IntWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            int N = Integer.parseInt(conf.get("matrixSize"));

            double tail = 0.0;
            double[] Abi = new double[N];
            double[] x = new double[N];
            int i = key.get();
            for (Text value: values) {
                String[] strArr = value.toString().split("\\s+");
                int j = Integer.parseInt(strArr[0]);
                if (j == N) {
                    tail = Double.parseDouble(strArr[1]);
                } else if (strArr[1].indexOf("x") != -1) {
                    x[j] = Double.parseDouble(strArr[1].replace("x", ""));
                } else {
                    Abi[j] = Double.parseDouble(strArr[1]);
                }
            }

            double sum = 0.0;
            for (int j = 0; j < N; j++) {
                if (i != j) {
                    sum += Abi[j] * x[j];
                }
            }
            sum = tail - sum;
            sum /= Abi[i];

            if (Math.abs(sum-x[i]) > 1e-10) {
                context.getCounter(ConvergeCounter.NOTCONVERGED).increment(1);
            }

            if (sum != 0) {
                context.write(new IntWritable(i), new Text(String.valueOf(sum)));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String matrixSize = args[0];
        int maxIt = Integer.parseInt(args[1]);
        int reducers = Integer.parseInt(args[2]);
        String inputPath = args[3];
        String outputPath = args[4];

        for (int i = 0; i < maxIt; i++) {
            Configuration conf = new Configuration();
            conf.set("matrixSize", matrixSize);

            Job job = Job.getInstance(conf, "Jacotail " + String.valueOf(i));
            job.setNumReduceTasks(reducers);
            job.setJarByClass(Jacobi.class);
            job.setMapperClass(JacobiMapper.class);
            job.setReducerClass(JacobiReducer.class);

            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(Text.class);

            FileInputFormat.addInputPath(job, new Path(inputPath));
            if (i > 0) {
                FileInputFormat.addInputPath(job, new Path(outputPath+"/Iteration"+Integer.toString(i)));
            }
            FileOutputFormat.setOutputPath(job, new Path(outputPath+"/Iteration"+Integer.toString(i+1)));
            job.waitForCompletion(true);

            if ((int)job.getCounters().findCounter(ConvergeCounter.NOTCONVERGED).getValue() == 0) {
                break;
            }
        }
    }
}