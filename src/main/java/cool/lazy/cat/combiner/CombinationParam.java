package cool.lazy.cat.combiner;

import java.util.Arrays;

/**
 * @author: mahao
 * @date: 2022-02-28 13:40
 */
public class CombinationParam {

    /**
     * 参与穷举的元素
     */
    private final char[] words;
    /**
     * 生成数据的最小位数
     */
    private final int minBit;
    /**
     * 生成数据的最大位数
     */
    private final int maxBit;
    /**
     * 分批穷举 设置批大小
     */
    private final int batchSize;
    /**
     * 穷举起始位置
     */
    private long startOffset = -1;
    /**
     * 穷举终止位置
     */
    private long endOffset = -1;
    /**
     * 穷举起始批
     */
    private long startBatch = -1;
    /**
     * 穷举终止批
     */
    private long endBatch = -1;

    public CombinationParam(char[] words, int minBit, int maxBit, int batchSize) {
        this.words = words;
        this.minBit = minBit;
        this.maxBit = maxBit;
        this.batchSize = batchSize;
    }

    public char[] getWords() {
        return words;
    }

    public int getMinBit() {
        return minBit;
    }

    public int getMaxBit() {
        return maxBit;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public CombinationParam setStartOffset(long startOffset) {
        this.startOffset = startOffset;
        return this;
    }

    public long getEndOffset() {
        return endOffset;
    }

    public CombinationParam setEndOffset(long endOffset) {
        this.endOffset = endOffset;
        return this;
    }

    public long getStartBatch() {
        return startBatch;
    }

    public CombinationParam setStartBatch(long startBatch) {
        this.startBatch = startBatch;
        return this;
    }

    public long getEndBatch() {
        return endBatch;
    }

    public CombinationParam setEndBatch(long endBatch) {
        this.endBatch = endBatch;
        return this;
    }

    @Override
    public String toString() {
        return "{" + "\"words\":" +
                Arrays.toString(words) +
                ",\"minBit\":" +
                minBit +
                ",\"maxBit\":" +
                maxBit +
                ",\"batchSize\":" +
                batchSize +
                ",\"startOffset\":" +
                startOffset +
                ",\"endOffset\":" +
                endOffset +
                ",\"startBatch\":" +
                startBatch +
                ",\"endBatch\":" +
                endBatch +
                '}';
    }
}
