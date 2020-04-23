Hadoop wordcount and Jacobi implementation
Courtesy: Hadoop tutorial for the wordcount

How to use:
	WordCount:
	Compile:
		$HADOOP_HOME/bin/hadoop com.sun.tools.javac.Main WordCount.java
	 	jar cf WordCount.jar WordCount*.class
	Run:
		$HADOOP_HOME/bin/hadoop jar WordCount.jar <class> <arguments>

	Jacobi:
	Compile:
		$HADOOP_HOME/bin/hadoop com.sun.tools.javac.Main Jacobi.java
	 	jar cf Jacobi.jar Jacobi*.class
	Run:
		$HADOOP_HOME/bin/hadoop jar Jacobi.jar Jacobi [matrix size] [max iteration] [number of reducers] [path to input file] [path to output file]

The wordcount is performing well with no big issue even with large corpora.
The idea is simple: tokenize the corpora with mapper and distribute the segments to reducers for consolidated results. 
The data is past in such format: <word, occurences>

I implemented the Jacobi algorithm described on the wikipedia page.
In each iteration, the intermediate X[i] results are stored in hdfs system for the next iteration.
There are matrix A, vector b, and intermediate solution vector x from last iteration.
The mapper will create k-v pair <i, (j, A[i][j])> where i: [0, n-1] and j: [0, n-1], and keep record of the tail of last x.
The reducer then receives the k-v pairs and calculate the sum and measure the convergence as described by the algorithm.
The performance of the Jacobi tends to be very slow when the matrix is relatively small. No matter how small the matrix is, it usually will require tens of seconds to finish each iteration. It is possibly because of the api overhead and expensive I/O operations. It shows better result with matrix size 65536 comparing to the sequential version. Anything larger than that will immediately kill the sequential version because of heap limitation. However, thought the hadoop version is not dead on large matrix, it also slows down possibly because the hdfs system need local storage's assistance to do the work. I think the Hadoop is and should be dedicated for very large scale data processing. 

There are results for 4.dat by both the hadoop and seq in the folder for santination check.