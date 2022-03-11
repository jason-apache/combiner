import cool.lazy.cat.combiner.CombinationCallback;
import cool.lazy.cat.combiner.CombinationParam;
import cool.lazy.cat.combiner.Combiner;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author: mahao
 * @date: 2022-03-09 14:03
 */
public class MainTest {

    @Test
    public void test1() {
        List<String> result = new ArrayList<>();
        char[] symbols = "01234".toCharArray();
        int minBit = 3;
        int maxBit = 4;
        int batchSize = 200000;
        boolean allow = Combiner.calculationTotalCount(symbols, minBit, maxBit) < 1000;
        CombinationCallback callback = (payload, realLength, curBatch, curCount, id) -> {
            if (allow) {
                for (int i = 0; i < realLength; i++) {
                    result.add(new String(payload[i]));
                }
            }
            return CombinationCallback.State.NORMAL;
        };
        new Combiner().call(new CombinationParam(symbols, minBit , maxBit, batchSize), callback);
        System.out.println(result + ":" + result.size());
        result.clear();
        new Combiner().call(new CombinationParam(symbols, minBit, maxBit, batchSize).setStartOffset(140), callback);
        System.out.println(result + ":" + result.size());
        result.clear();
        new Combiner().call(new CombinationParam(symbols, minBit, maxBit, batchSize), callback);
        System.out.println(result + ":" + result.size());
    }

    @Test
    public void test2() {
        char[] symbols = "01234".toCharArray();
        System.out.println(Combiner.analysisDictionary(symbols, 4, 2));
        int minBit = 4;
        int maxBit = 4;
        int batchSize = 200000;
        boolean allow = Combiner.calculationTotalCount(symbols, minBit, maxBit) < 1000;
        List<String> result = new ArrayList<>();
        CombinationCallback callback = (payload, realLength, curBatch, curCount, id) -> {
            if (allow) {
                for (int i = 0; i < realLength; i++) {
                    result.add(new String(payload[i]));
                }
            }
            return CombinationCallback.State.NORMAL;
        };
        new Combiner().call(new CombinationParam(symbols, minBit , maxBit, batchSize), callback);
        System.out.println(result + ":" + result.size());
    }

    @Test
    public void shards() throws Exception {
        char[] symbols = "0123456789".toCharArray();
        char[] target = "00000700000".toCharArray();
        char[] max = "999999999".toCharArray();
        int workerCount = 3;
        int minBit = 11;
        int maxBit = 11;
        int batchSize = 200000;
        long totalCount = Combiner.calculationTotalCount(symbols, minBit, maxBit);
        List<Combiner> combiners = new ArrayList<>(workerCount);
        long averageBatchCount = totalCount / batchSize / workerCount;
        long preBatchEnd = -1L;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(workerCount, workerCount, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());
        Object monitor = new Object();
        for (int i = 0; i < workerCount; i++) {
            long startBatch = preBatchEnd == -1L ? 0L : preBatchEnd + 1;
            long endBatch = i == workerCount - 1 ? -1 : (i + 1) * averageBatchCount;
            CombinationCallback callback = (payload, realLength, curBatch, curCount, id) -> {
                for (int j = 0; j < realLength; j++) {
                    if (Arrays.equals(payload[j], target)) {
                        synchronized (monitor) {
                            monitor.notify();
                        }
                        System.out.println(id + " 找到了" + new String(target));
                        pool.shutdownNow();
                    }
                }
                if (Arrays.equals(payload[realLength -1], max)) {
                    synchronized (monitor) {
                        monitor.notify();
                    }
                }
                return CombinationCallback.State.NORMAL;
            };
            Combiner combiner = new Combiner(String.valueOf(i), new CombinationParam(symbols, minBit, maxBit, batchSize).setStartBatch(startBatch).setEndBatch(endBatch), callback);
            combiners.add(combiner);
            preBatchEnd = endBatch;
        }
        combiners.forEach(pool::submit);
        synchronized (monitor) {
            monitor.wait();
        }
    }

    @Test
    public void test3() {
        long total = Combiner.calculationTotalCount("0123456789abcdefghijklmnopqrstuvwxyz".toCharArray(), 11, 11);
        int batchSize = 200000;
        int workCounter = 36;
        long averageBatchCount = total / batchSize / workCounter;
        int batchesPerSecond = 200;
        System.out.printf("需要%d秒%n", averageBatchCount / batchesPerSecond);
        System.out.printf("需要%d分钟%n", averageBatchCount / batchesPerSecond / 60);
        System.out.printf("需要%d小时%n", averageBatchCount / batchesPerSecond / 60 / 60);
        System.out.printf("需要%d天%n", averageBatchCount / batchesPerSecond / 60 / 60 / 24);
        System.out.printf("需要%d年%n", averageBatchCount / batchesPerSecond / 60 / 60 /24 / 365);
    }
}
