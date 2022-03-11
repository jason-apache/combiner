package cool.lazy.cat.combiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: mahao
 * @date: 2022-02-28 13:36
 */
public class Combiner implements Runnable {

    // 0-1-2
    // 000 001 002 010 011 012 020 021 022
    // 100 101 102 110 111 112 120 121 122
    // 200 201 202 210 211 212 220 221 222

    // 0-1
    // 00 01
    // 10 11

    public static final char[] NUMBERS = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    public static final char[] WORDS = new char[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
    public static final char[] UPPER_WORDS = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
    public static final char[] SYMBOL = new char[]{'.', '@', '#', '$', '%', '_', '-', '=', '+'};

    public enum ExecuteState {
        DONE, RUNNING, ERROR, IDLE, INTERRUPTED, TERMINATION
    }

    protected String id;
    protected long curBatch;
    protected long batchCount;
    protected long curCount;
    protected long totalCount;
    protected int[] indexes;
    protected ExecuteState state = ExecuteState.IDLE;
    protected CombinationParam combinationParam;
    protected CombinationCallback callback;
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    protected void init() {
        this.curBatch = 0L;
        this.batchCount = 0L;
        this.curCount = 0L;
        this.totalCount = 0L;
        this.indexes = null;
        this.state = ExecuteState.IDLE;
    }

    public Combiner() {
    }

    public Combiner(String id, CombinationParam combinationParam, CombinationCallback callback) {
        this.id = id;
        this.combinationParam = combinationParam;
        this.callback = callback;
        String prefix = id;
        if (combinationParam.getStartBatch() != -1 || combinationParam.getEndBatch() != -1) {
            prefix = prefix + "{startBatch:"+ combinationParam.getStartBatch() +", endBatch: "+ combinationParam.getEndBatch() +"}";
        } else if (combinationParam.getStartOffset() != -1 || combinationParam.getEndOffset() != -1) {
            prefix = prefix + "{startPos:"+ combinationParam.getStartOffset() +", endPos: "+ combinationParam.getEndOffset() +"}";
        }
        this.logger = LoggerFactory.getLogger(this.getClass().getSimpleName() + " -> " + prefix);
    }

    @Override
    public void run() {
        if (null != combinationParam && null != callback) {
            this.call(combinationParam, callback);
        }
    }

    public void call(CombinationParam param, CombinationCallback callback) {
        if (param.getMaxBit() <= 1 || param.getMaxBit() < param.getMinBit() || param.getBatchSize() <= 0 || param.getWords() == null || param.getWords().length == 0) {
            throw new IllegalArgumentException("无效参数: " + param);
        }
        this.init();
        this.state = ExecuteState.RUNNING;
        try {
            if (param.getStartBatch() != -1 || param.getEndBatch() != -1) {
                if (param.getStartBatch() != -1 && param.getEndBatch() != -1 && (param.getStartBatch() < 0 || param.getEndBatch() <= param.getStartBatch())) {
                    throw new IllegalArgumentException("无效参数: " + param);
                }
                // 按批穷举
                this.callWithBatch(param.getWords(), param.getMinBit(), param.getMaxBit(), param.getBatchSize(), param.getStartBatch(), param.getEndBatch(), callback);
            } else if (param.getStartOffset() != -1 || param.getEndOffset() != -1) {
                if (param.getStartOffset() != -1 && param.getEndOffset() != -1 && (param.getStartOffset() < 0 || param.getEndOffset() <= param.getStartOffset())) {
                    throw new IllegalArgumentException("无效参数: " + param);
                }
                // 按起始位置穷举
                this.callWithOffset(param.getWords(), param.getMinBit(), param.getMaxBit(), param.getBatchSize(), param.getStartOffset(), param.getEndOffset(), callback);
            } else {
                this.call(param.getWords(), param.getMinBit(), param.getMaxBit(), param.getBatchSize(), callback);
            }
            this.state = ExecuteState.DONE;
        } catch (Exception e) {
            logger.error("穷举异常", e);
            this.state = ExecuteState.ERROR;
        }
    }

    /**
     * 组合穷举
     * @param factors 元素字符
     * @param minBit 数据最小位数
     * @param maxBit 数据最大位数
     * @param batchSize 每批数据量大小
     * @param callback 穷举回调函数
     */
    protected void call(char[] factors, int minBit, int maxBit, int batchSize, CombinationCallback callback) throws Exception {
        final int itemCount = factors.length;
        int offset = 0;
        final char[][] payload = new char[batchSize][];
        int curBit = minBit;
        logger.debug("穷举前分析...");
        for (int i = minBit; i <= maxBit; i++) {
            long count = (long) Math.pow(itemCount, i);
            totalCount += count;
            logger.debug("位: " + i + ", 当前位计算次数: " + count + ", 增量计算次数: " + totalCount);
        }
        logger.debug("分析完毕");
        Thread hook = this.buildHook();
        // 监听jvm关闭
        this.addListen(hook);
        this.batchCount = totalCount / batchSize +1;
        logger.info("开始穷举 预计需要计算 ["+ batchCount +"] 批");
        long start = System.currentTimeMillis();
        while (curBit <= maxBit) {
            int[] indexes = new int[curBit];
            long internalTotalCount = (long) Math.pow(itemCount, curBit);
            long internalCurCount = 0L;
            while (internalCurCount ++ < internalTotalCount) {
                curCount ++;
                char[] values = new char[curBit];
                for (int i = 0; i < values.length; i++) {
                    values[i] = factors[indexes[i]];
                }
                payload[offset++] = values;
                if (offset >= batchSize) {
                    if (Thread.currentThread().isInterrupted()) {
                        logger.warn("线程任务已被终止");
                        this.state = ExecuteState.TERMINATION;
                        return;
                    }
                    this.indexes = indexes;
                    curBatch ++;
                    logger.info("--已完成 [" + curBatch + "] 批, 预计需要 [" + batchCount + "] 批, 当前计算量: " + curCount + "/" + totalCount);
                    if (callback.call(payload, offset, curBatch, curCount, id) != CombinationCallback.State.NORMAL) {
                        this.state = ExecuteState.INTERRUPTED;
                        return;
                    }
                    offset = 0;
                }
                this.increment(itemCount, curBit, indexes);
            }
            logger.info("--已完成符号组合位数: " + curBit + ", 当前计算量: " + curCount + "/" + totalCount);
            curBit++;
        }
        logger.info("穷举结束, 耗时: " + (System.currentTimeMillis() - start));
        if (offset > 0) {
            callback.call(payload, offset, curBatch, curCount, id);
        }
        this.removeListen(hook);
    }

    /**
     * 按批次穷举
     */
    protected void callWithBatch(final char[] factors, final int minBit, final int maxBit, final int batchSize,
                                 long startBatch, long endBatch, final CombinationCallback callback) throws Exception {
        this.callWithOffset(factors, minBit, maxBit, batchSize, startBatch * batchSize, endBatch * batchSize + batchSize, callback);
    }

    /**
     * 根据穷举起始位置开始穷举
     * @param factors 元素字符
     * @param minBit 数据最小位数
     * @param maxBit 数据最大位数
     * @param batchSize 每批数据量大小
     * @param startOffset 起始位置偏移
     * @param endOffset 结束位置偏移
     * @param callback 穷举回调函数
     */
    protected void callWithOffset(final char[] factors, final int minBit, final int maxBit, final int batchSize,
                                  long startOffset, long endOffset, final CombinationCallback callback) throws Exception {
        startOffset = startOffset < 0 ? 0 : startOffset;
        final int itemCount = factors.length;
        final char[][] payload = new char[batchSize][];
        int offset = 0;
        int curBit = -1;
        long pastCount = 0L;
        logger.debug("穷举前分析...");
        for (int i = minBit; i <= maxBit; i++) {
            long count = (long) Math.pow(itemCount, i);
            totalCount += count;
            logger.debug("位: " + i + ", 当前位计算次数: " + count + ", 增量计算次数: " + totalCount);
            if (curBit == -1 && startOffset < totalCount) {
                curBit = i;
                if (i > minBit) {
                    pastCount = totalCount - count;
                }
            }
        }
        if (curBit == -1) {
            throw new IllegalArgumentException("无法查找当前起始位数! startOffset: " + startOffset);
        }
        logger.debug("分析完毕");
        this.batchCount = totalCount / batchSize +1;
        this.curBatch = startOffset / batchSize +1;
        this.curCount = startOffset;
        endOffset = endOffset <= startOffset ? totalCount : endOffset;
        startOffset -= pastCount;
        Thread hook = this.buildHook();
        // 监听jvm关闭
        this.addListen(hook);
        int[] indexes = this.generateIndexes(curBit, startOffset, factors);
        long internalCurCount = startOffset;
        long start = System.currentTimeMillis();
        logger.info("开始穷举 预计需要计算 ["+ batchCount +"] 批");
        main_: while (curBit <= maxBit) {
            long internalTotalCount = (long) Math.pow(itemCount, curBit);
            while (internalCurCount ++ < internalTotalCount) {
                if (curCount ++ >= endOffset) {
                    break main_;
                }
                char[] values = new char[curBit];
                for (int i = 0; i < values.length; i++) {
                    values[i] = factors[indexes[i]];
                }
                payload[offset++] = values;
                if (offset >= batchSize) {
                    if (Thread.currentThread().isInterrupted()) {
                        logger.warn("任务已被终止");
                        this.state = ExecuteState.TERMINATION;
                        return;
                    }
                    this.indexes = indexes;
                    curBatch ++;
                    logger.info("--已完成第 [" + curBatch + "] 批, 预计需要 [" + batchCount + "] 批, 当前已计算量: " + curCount + "/" + totalCount);
                    if (callback.call(payload, offset, curBatch, curCount, id) != CombinationCallback.State.NORMAL) {
                        this.state = ExecuteState.INTERRUPTED;
                        return;
                    }
                    offset = 0;
                }
                this.increment(itemCount, curBit, indexes);
            }
            logger.info("--已完成符号组合位数: " + curBit + ", 当前计算量: " + curCount + "/" + totalCount);
            indexes = new int[++ curBit];
            internalCurCount = 0L;
        }
        logger.info("穷举结束, 耗时: " + (System.currentTimeMillis() - start));
        if (offset > 0) {
            callback.call(payload, offset, curBatch, curCount, id);
        }
        this.removeListen(hook);
    }

    /**
     * 步进
     */
    protected void increment(int itemCount, int curBit, int[] indexes) {
        indexes[curBit - 1] = indexes[curBit - 1] + 1;
        if (indexes[curBit - 1] >= itemCount) {
            for (int i = indexes.length - 1; i >= 0; i--) {
                int v = indexes[i];
                if (v >= itemCount) {
                    indexes[i] = 0;
                    if (i - 1 >= 0) {
                        indexes[i - 1] = indexes[i - 1] + 1;
                    }
                } else {
                    break;
                }
            }
        }
    }

    /**
     * 生成起始位置对应的字符索引
     * @param bit 字符长度位数
     * @param startOffset 起始位置
     * @param factors 元素字符
     * @return 索引
     */
    protected int[] generateIndexes(int bit, long startOffset, char[] factors) {
        Map<Character, Integer> dictionary = new HashMap<>(factors.length);
        for (int i = 0; i < factors.length; i++) {
            dictionary.put(factors[i], i);
        }
        String val = DigitUtil.tenToAny(startOffset, factors);
        char[] values;
        if (val.length() > bit) {
            throw new IllegalArgumentException("未知异常");
        } else if (val.length() < bit) {
            values = new char[bit];
            System.arraycopy(val.toCharArray(), 0, values, bit - val.length(), val.length());
        } else {
            values = val.toCharArray();
        }
        int[] indexes = new int[bit];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i] = dictionary.getOrDefault(values[i], 0);
        }
        return indexes;
    }

    protected Thread buildHook() {
        return new Thread(() -> {
            logger.info("退出程序, 已计算至第 ["+ this.curBatch +"] 批, 索引: "+ Arrays.toString(this.indexes));
            logger.info(this.toString());
        });
    }

    protected void addListen(Thread hook) {
        Runtime.getRuntime().addShutdownHook(hook);
    }

    protected void removeListen(Thread hook) {
        Runtime.getRuntime().removeShutdownHook(hook);
    }

    @Override
    public String toString() {
        return "{" + "\"id\":\"" +
                id + '\"' +
                ",\"curBatch\":" +
                curBatch +
                ",\"batchCount\":" +
                batchCount +
                ",\"curCount\":" +
                curCount +
                ",\"totalCount\":" +
                totalCount +
                ",\"indexes\":" +
                Arrays.toString(indexes) +
                '}';
    }

    /**
     * 计算可穷尽的总数
     * @param factors 元素字符
     * @param minBit 数据最小位数
     * @param maxBit 数据最大位数
     * @return 穷举总数
     */
    public static long calculationTotalCount(char[] factors, int minBit, int maxBit) {
        final int itemCount = factors.length;
        long totalCount = 0L;
        for (int i = minBit; i <= maxBit; i++) {
            totalCount += (long) Math.pow(itemCount, i);
        }
        return totalCount;
    }

    /**
     * 分析穷举数据字典分布
     * @param factors 元素字符
     * @param bit 数据位数
     * @param level 计算粒度(深度)
     * @return 分布描述
     */
    public static String analysisDictionary(char[] factors, int bit, int level) {
        if (level > bit) {
            throw new IllegalArgumentException("非法参数: bit["+ bit +"] level["+ level +"]");
        }
        StringBuilder msg = new StringBuilder();
        AnalysisDictionaryCollector collector = (prefix, curSymbol, depth, pos, relativePos, increment) -> {
            for (int j = 0; j < depth; j++) {
                msg.append("|");
                msg.append("\t");
            }
            msg.append(prefix).append(curSymbol);
            for (int j = 0; j < bit - depth -1; j++) {
                msg.append("_");
            }
            msg.append(">>>").append(pos).append("\r\n");
        };
        analysisDictionary(collector, factors, bit, level);
        return msg.toString();
    }

    public static void analysisDictionary(AnalysisDictionaryCollector collector, char[] factors, int bit, int level) {
        if (level > bit) {
            throw new IllegalArgumentException("非法参数: bit["+ bit +"] level["+ level +"]");
        }
        long increment = (long) Math.pow(factors.length, bit) / factors.length;
        analysisDictionary(collector, factors, new char[bit], 0, 0, bit, increment, 0, level);
    }

    /**
     * 分析穷举数据字典分布
     * @param collector 收集字典信息函数
     * @param factors 元素字符
     * @param payload 字符载体
     * @param payloadIndex 字符载体当前位置索引
     * @param relativePos 上一层起始位置
     * @param bit 数据位数
     * @param increment 元素增量值
     * @param depth 当前深度
     * @param level 粒度
     */
    public static void analysisDictionary(AnalysisDictionaryCollector collector, char[] factors, char[] payload, int payloadIndex, long relativePos, int bit, long increment, int depth, int level) {
        if (!(depth < level)) {
            return;
        }
        long internalIncrement = increment / factors.length;
        for (int i = 0; i < factors.length; i++) {
            char c = factors[i];
            long pos = i * increment + relativePos;
            String symbol = new String(payload, 0, payloadIndex);
            collector.collect(symbol, c, depth, pos, relativePos, increment);
            payload[payloadIndex] = c;
            analysisDictionary(collector, factors, payload, payloadIndex + 1, pos, bit, internalIncrement, depth +1, level);
        }
    }

    public String getId() {
        return id;
    }

    public long getCurBatch() {
        return curBatch;
    }

    public long getBatchCount() {
        return batchCount;
    }

    public long getCurCount() {
        return curCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public ExecuteState getState() {
        return state;
    }
}
