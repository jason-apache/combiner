package cool.lazy.cat.combiner;

/**
 * @author: mahao
 * @date: 2022-02-28 13:38
 */
@FunctionalInterface
public interface CombinationCallback {

    /**
     * 穷举回调, 穷举数据量达到阈值调用此函数, 结果集中也许存在脏数据, 需要根据 realLength 参数取值
     * @param payload 穷举结果
     * @param realLength 结果集数据真实长度
     * @param curBatch 当前批次
     * @return 回调函数状态
     */
    State call(char[][] payload, int realLength, long curBatch, long curCount, String combinerId) throws Exception;

    enum State {
        /**
         * NORMAL表示正常, 其余返回值将中断穷举的运行
         */
        INTERRUPTED, NORMAL, ERROR
    }
}
