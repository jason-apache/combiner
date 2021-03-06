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
            throw new IllegalArgumentException("????????????: " + param);
        }
        this.init();
        this.state = ExecuteState.RUNNING;
        try {
            if (param.getStartBatch() != -1 || param.getEndBatch() != -1) {
                if (param.getStartBatch() != -1 && param.getEndBatch() != -1 && (param.getStartBatch() < 0 || param.getEndBatch() <= param.getStartBatch())) {
                    throw new IllegalArgumentException("????????????: " + param);
                }
                // ????????????
                this.callWithBatch(param.getWords(), param.getMinBit(), param.getMaxBit(), param.getBatchSize(), param.getStartBatch(), param.getEndBatch(), callback);
            } else if (param.getStartOffset() != -1 || param.getEndOffset() != -1) {
                if (param.getStartOffset() != -1 && param.getEndOffset() != -1 && (param.getStartOffset() < 0 || param.getEndOffset() <= param.getStartOffset())) {
                    throw new IllegalArgumentException("????????????: " + param);
                }
                // ?????????????????????
                this.callWithOffset(param.getWords(), param.getMinBit(), param.getMaxBit(), param.getBatchSize(), param.getStartOffset(), param.getEndOffset(), callback);
            } else {
                this.call(param.getWords(), param.getMinBit(), param.getMaxBit(), param.getBatchSize(), callback);
            }
            this.state = ExecuteState.DONE;
        } catch (Exception e) {
            logger.error("????????????", e);
            this.state = ExecuteState.ERROR;
        }
    }

    /**
     * ????????????
     * @param factors ????????????
     * @param minBit ??????????????????
     * @param maxBit ??????????????????
     * @param batchSize ?????????????????????
     * @param callback ??????????????????
     */
    protected void call(char[] factors, int minBit, int maxBit, int batchSize, CombinationCallback callback) throws Exception {
        final int itemCount = factors.length;
        int offset = 0;
        final char[][] payload = new char[batchSize][];
        int curBit = minBit;
        logger.debug("???????????????...");
        for (int i = minBit; i <= maxBit; i++) {
            long count = (long) Math.pow(itemCount, i);
            totalCount += count;
            logger.debug("???: " + i + ", ?????????????????????: " + count + ", ??????????????????: " + totalCount);
        }
        logger.debug("????????????");
        Thread hook = this.buildHook();
        // ??????jvm??????
        this.addListen(hook);
        this.batchCount = totalCount / batchSize +1;
        logger.info("???????????? ?????????????????? ["+ batchCount +"] ???");
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
                        logger.warn("????????????????????????");
                        this.state = ExecuteState.TERMINATION;
                        return;
                    }
                    this.indexes = indexes;
                    curBatch ++;
                    logger.info("--????????? [" + curBatch + "] ???, ???????????? [" + batchCount + "] ???, ???????????????: " + curCount + "/" + totalCount);
                    if (callback.call(payload, offset, curBatch, curCount, id) != CombinationCallback.State.NORMAL) {
                        this.state = ExecuteState.INTERRUPTED;
                        return;
                    }
                    offset = 0;
                }
                this.increment(itemCount, curBit, indexes);
            }
            logger.info("--???????????????????????????: " + curBit + ", ???????????????: " + curCount + "/" + totalCount);
            curBit++;
        }
        logger.info("????????????, ??????: " + (System.currentTimeMillis() - start));
        if (offset > 0) {
            callback.call(payload, offset, curBatch, curCount, id);
        }
        this.removeListen(hook);
    }

    /**
     * ???????????????
     */
    protected void callWithBatch(final char[] factors, final int minBit, final int maxBit, final int batchSize,
                                 long startBatch, long endBatch, final CombinationCallback callback) throws Exception {
        long endOffset = endBatch > 0 ? endBatch * batchSize + batchSize : -1;
        this.callWithOffset(factors, minBit, maxBit, batchSize, startBatch * batchSize, endOffset, callback);
    }

    /**
     * ????????????????????????????????????
     * @param factors ????????????
     * @param minBit ??????????????????
     * @param maxBit ??????????????????
     * @param batchSize ?????????????????????
     * @param startOffset ??????????????????
     * @param endOffset ??????????????????
     * @param callback ??????????????????
     */
    protected void callWithOffset(final char[] factors, final int minBit, final int maxBit, final int batchSize,
                                  long startOffset, long endOffset, final CombinationCallback callback) throws Exception {
        startOffset = startOffset < 0 ? 0 : startOffset;
        final int itemCount = factors.length;
        final char[][] payload = new char[batchSize][];
        int offset = 0;
        int curBit = -1;
        long pastCount = 0L;
        logger.debug("???????????????...");
        for (int i = minBit; i <= maxBit; i++) {
            long count = (long) Math.pow(itemCount, i);
            totalCount += count;
            logger.debug("???: " + i + ", ?????????????????????: " + count + ", ??????????????????: " + totalCount);
            if (curBit == -1 && startOffset < totalCount) {
                curBit = i;
                if (i > minBit) {
                    pastCount = totalCount - count;
                }
            }
        }
        if (curBit == -1) {
            throw new IllegalArgumentException("??????????????????????????????! startOffset: " + startOffset);
        }
        logger.debug("????????????");
        this.batchCount = totalCount / batchSize +1;
        this.curBatch = startOffset / batchSize +1;
        this.curCount = startOffset;
        endOffset = endOffset <= startOffset ? totalCount : endOffset;
        startOffset -= pastCount;
        Thread hook = this.buildHook();
        // ??????jvm??????
        this.addListen(hook);
        int[] indexes = this.generateIndexes(curBit, startOffset, factors);
        long internalCurCount = startOffset;
        long start = System.currentTimeMillis();
        logger.info("???????????? ?????????????????? ["+ batchCount +"] ???");
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
                        logger.warn("??????????????????");
                        this.state = ExecuteState.TERMINATION;
                        return;
                    }
                    this.indexes = indexes;
                    curBatch ++;
                    logger.info("--???????????? [" + curBatch + "] ???, ???????????? [" + batchCount + "] ???, ??????????????????: " + curCount + "/" + totalCount);
                    if (callback.call(payload, offset, curBatch, curCount, id) != CombinationCallback.State.NORMAL) {
                        this.state = ExecuteState.INTERRUPTED;
                        return;
                    }
                    offset = 0;
                }
                this.increment(itemCount, curBit, indexes);
            }
            logger.info("--???????????????????????????: " + curBit + ", ???????????????: " + curCount + "/" + totalCount);
            indexes = new int[++ curBit];
            internalCurCount = 0L;
        }
        logger.info("????????????, ??????: " + (System.currentTimeMillis() - start));
        if (offset > 0) {
            callback.call(payload, offset, curBatch, curCount, id);
        }
        this.removeListen(hook);
    }

    /**
     * ??????
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
     * ???????????????????????????????????????
     * @param bit ??????????????????
     * @param startOffset ????????????
     * @param factors ????????????
     * @return ??????
     */
    protected int[] generateIndexes(int bit, long startOffset, char[] factors) {
        Map<Character, Integer> dictionary = new HashMap<>(factors.length);
        for (int i = 0; i < factors.length; i++) {
            dictionary.put(factors[i], i);
        }
        String val = DigitUtil.tenToAny(startOffset, factors);
        char[] values;
        if (val.length() > bit) {
            throw new IllegalArgumentException("????????????");
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
            logger.info("????????????, ??????????????? ["+ this.curBatch +"] ???, ??????: "+ Arrays.toString(this.indexes));
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
     * ????????????????????????
     * @param factors ????????????
     * @param minBit ??????????????????
     * @param maxBit ??????????????????
     * @return ????????????
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
     * ??????????????????????????????
     * @param factors ????????????
     * @param bit ????????????
     * @param level ????????????(??????)
     * @return ????????????
     */
    public static String analysisDictionary(char[] factors, int bit, int level) {
        if (level > bit) {
            throw new IllegalArgumentException("????????????: bit["+ bit +"] level["+ level +"]");
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
            throw new IllegalArgumentException("????????????: bit["+ bit +"] level["+ level +"]");
        }
        long increment = (long) Math.pow(factors.length, bit) / factors.length;
        analysisDictionary(collector, factors, new char[bit], 0, 0, increment, 0, level);
    }

    /**
     * ??????????????????????????????
     * @param collector ????????????????????????
     * @param factors ????????????
     * @param payload ????????????
     * @param payloadIndex ??????????????????????????????
     * @param relativePos ?????????????????????
     * @param increment ???????????????
     * @param depth ????????????
     * @param level ??????
     */
    public static void analysisDictionary(AnalysisDictionaryCollector collector, char[] factors, char[] payload, int payloadIndex, long relativePos, long increment, int depth, int level) {
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
            analysisDictionary(collector, factors, payload, payloadIndex + 1, pos, internalIncrement, depth +1, level);
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
