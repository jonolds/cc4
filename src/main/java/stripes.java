import java.io.IOException;
import java.util.Set;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

class StripesOccurrenceMapper extends Mapper<LongWritable, Text, Text, MapWritable> {
	private MapWritable occurrenceMap = new MapWritable();
	private Text word = new Text();

	@Override
	protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
		// get the parameter from the driver. "2" is the default value
		// 		example: Configuration conf = new Configuration();
		//		conf.setInt("neighbors", 2);
		//		Job job = new Job(conf, "word pairs count");
		int neighbors = context.getConfiguration().getInt("neighbors", 2);			

		String[] tokens = value.toString().split("\\s+");
		if(tokens.length > 1) {
			for (int i = 0; i < tokens.length; i++) {
				word.set(tokens[i]);
				occurrenceMap.clear();

				int start = (i - neighbors < 0) ? 0 : i - neighbors;
				int end = (i + neighbors > tokens.length - 1) ? tokens.length - 1 : i + neighbors;
				for (int j = start; j <= end; j++) {
					if (j == i) continue;
					Text neighbor = new Text(tokens[j]);
					if(occurrenceMap.containsKey(neighbor)) {
						IntWritable count = (IntWritable)occurrenceMap.get(neighbor); //get neighbor count
						count.set(count.get()+1);									  //increment
						occurrenceMap.put(neighbor, count);							  //add count to map
					}
					else
						occurrenceMap.put(neighbor,new IntWritable(1));
				}
				context.write(word,occurrenceMap);
			}
		}
	}
}

class StripesReducer extends Reducer<Text, MapWritable, Text, MapWritable> {
	private MapWritable incrementingMap = new MapWritable();

	@Override
	protected void reduce(Text key, Iterable<MapWritable> values, Context context) throws IOException, InterruptedException {
		incrementingMap.clear();
		for (MapWritable value : values)
			addAll(value);
		context.write(key, incrementingMap);
	}

	private void addAll(MapWritable mapWritable) {
		Set<Writable> keys = mapWritable.keySet();
		for (Writable key : keys) {
			IntWritable fromCount = (IntWritable) mapWritable.get(key);
			if (incrementingMap.containsKey(key)) {
				IntWritable count = (IntWritable) incrementingMap.get(key);
				count.set(count.get() + fromCount.get());
				incrementingMap.put(key, count);
			} 
			else
				incrementingMap.put(key, fromCount);
		}
	}
}