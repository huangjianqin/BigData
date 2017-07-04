package org.bigdata.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.bigdata.kafka.api.MultiThreadConsumerManager;
import org.bigdata.kafka.multithread.Counters;
import org.bigdata.kafka.multithread.PropertiesWrapper;
import org.bigdata.kafka.multithread.TopicPartitionWithTime;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by hjq on 2017/6/22.
 */
public class TestMultiThreadConsumerManager {
    public static void main(String[] args) throws InterruptedException {
        long startTime = 0;
        long endTime = 0;

        Properties config = PropertiesWrapper.create()
                .set(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "ubuntu:9092")
                .set(ConsumerConfig.GROUP_ID_CONFIG, "multi-handle")
                .set(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
                .set(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
                .set(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                .properties();
        Set<String> topic = new HashSet<>();
        topic.add("multi-msg2");

        //1.一个consumer
        MultiThreadConsumerManager.instance().<String, String>registerConsumer("test", config, topic, null, null);
        startTime = System.currentTimeMillis();

        long runTime = 2 * 60 * 1000;
        Thread.sleep(runTime);
        endTime = System.currentTimeMillis();

        MultiThreadConsumerManager.instance().stopConsumerSync("test");

        long sum = Counters.getCounters().get("consumer-counter");
        long sum1 = Counters.getCounters().get("consumer-byte-counter");
        System.out.println("总消费:" + sum + "条");
        System.out.println("总消费:" + sum1 + "字节");
        System.out.println("总消费率: " + 1.0 * sum / (endTime - startTime) + "条/ms");
        System.out.println("总消费率: " + 1.0 * sum1 / (endTime - startTime) + "B/ms");
    }
}