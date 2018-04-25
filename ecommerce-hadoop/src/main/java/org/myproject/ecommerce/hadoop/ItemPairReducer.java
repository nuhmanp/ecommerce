package org.myproject.ecommerce.hadoop;

import com.mongodb.hadoop.io.BSONWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapreduce.Reducer;
import org.bson.BasicBSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

/**
 * ItemPairMapper/ItemPairReducer crunch lastDayOrders collection to produce pair collection.
 */
public class ItemPairReducer extends Reducer<ItemPair, IntWritable, BSONWritable, BSONWritable>
                implements org.apache.hadoop.mapred.Reducer<ItemPair, IntWritable,
        BSONWritable, BSONWritable> {
    private BSONWritable reduceKey;
    private BSONWritable reduceResult;
    private static final Logger logger = LoggerFactory.getLogger(ItemPairReducer.class);

    public ItemPairReducer() {
        super();
        reduceKey = new BSONWritable();
        reduceResult = new BSONWritable();
    }

    @Override
    public void reduce(ItemPair pKey, Iterable<IntWritable> pValues, Context pContext)
            throws IOException, InterruptedException {
        logger.info("Reduce processing with Context class");
        int total = StreamSupport.stream(pValues.spliterator(), false)
                               .mapToInt(i -> i.get()).sum();
        reduceKey.setDoc(new BasicBSONObject().append("a", pKey.getA()).append("b", pKey.getB()));
        reduceResult.setDoc(new BasicBSONObject().append("value", total));
        pContext.write(reduceKey, reduceResult);
    }

    @Override
    public void reduce(ItemPair key, Iterator<IntWritable> values, OutputCollector<BSONWritable,
            BSONWritable> output, Reporter reporter) throws IOException {
        logger.info("Reduce processing with OutputCollector class");
        int total = StreamSupport.stream(Spliterators.spliteratorUnknownSize(values, Spliterator.ORDERED), false)
                .mapToInt(i -> i.get()).sum();
        reduceKey.setDoc(new BasicBSONObject().append("a", key.getA()).append("b", key.getB()));
        reduceResult.setDoc(new BasicBSONObject().append("value", total));
        output.collect(reduceKey, reduceResult);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void configure(JobConf job) {
    }
}
