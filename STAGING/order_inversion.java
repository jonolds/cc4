import java.io.IOException;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;

class PairsRelativeOccurrenceMapper extends Mapper<LongWritable, Text, WordPair, IntWritable> {
	private WordPair wordPair = new WordPair();
	private IntWritable ONE = new IntWritable(1), totalCount = new IntWritable();

	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
		int neighbors = context.getConfiguration().getInt("neighbors", 2);
		String[] tokens = value.toString().split("\\s+");		  // split the words using spaces
		if (tokens.length > 1) {
			for (int i = 0; i < tokens.length; i++) {
				tokens[i] = tokens[i].replaceAll("\\W+","");   // remove all non-word characters

				if(tokens[i].equals("")) continue;

				wordPair.setWord(tokens[i]);

				int start = (i - neighbors < 0) ? 0 : i - neighbors;
				int end = (i + neighbors >= tokens.length) ? tokens.length - 1 : i + neighbors;
				for (int j = start; j <= end; j++) {
					if (j == i) continue;
					wordPair.setNeighbor(tokens[j].replaceAll("\\W",""));
					context.write(wordPair, ONE);
				}
				wordPair.setNeighbor("*");
				totalCount.set(end - start);
				context.write(wordPair, totalCount);
			}
		}
	}
}

class WordPairPartitioner extends Partitioner<WordPair,IntWritable> {
	public int getPartition(WordPair wordPair, IntWritable intWritable, int numPartitions) {
		return wordPair.getWord().hashCode() % numPartitions;
	}
}

class PairsRelativeOccurrenceReducer extends Reducer<WordPair, IntWritable, WordPair, DoubleWritable> {
	private DoubleWritable totalCount = new DoubleWritable(), relativeCount = new DoubleWritable();
	private Text currentWord = new Text("NOT_SET"), flag = new Text("*");

	protected void reduce(WordPair key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {
		if (key.getNeighbor().equals(flag)) {		  //start a new section of word pairs with a new left word
			if (key.getWord().equals(currentWord)) {   //keep adding the counts of (word, *).
													   //In fact, this will not happen because ...
				totalCount.set(totalCount.get() + getTotalCount(values));
			} 
			else {								   //reset the count when encounting (word, *) for the first time
				currentWord.set(key.getWord());
				totalCount.set(0);
				totalCount.set(getTotalCount(values));
			}
		}
		else {									   //calculate the
			int count = getTotalCount(values);
			relativeCount.set((double) count / totalCount.get());
			context.write(key, relativeCount);
		}
	}
	
	private int getTotalCount(Iterable<IntWritable> values) {
		int count = 0;
		for (IntWritable value : values)
			count += value.get();
		return count;
	}
}