package cool.lazy.cat.combiner;

/**
 * @author: mahao
 * @date: 2022-03-09 14:16
 */
@FunctionalInterface
public interface AnalysisDictionaryCollector {

    /**
     * 收集字典信息
     * @param prefix 符号前缀
     * @param curSymbol 当前符号
     * @param depth 当前遍历深度
     * @param pos 当前符号对应位置
     * @param relativePos 上层位置
     * @param increment 元素增量值
     */
    void collect(String prefix, char curSymbol, int depth, long pos, long relativePos, long increment);
}
