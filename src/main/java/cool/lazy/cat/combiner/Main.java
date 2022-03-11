package cool.lazy.cat.combiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author: mahao
 * @date: 2022-03-09 16:34
 */
public class Main {

    static final Logger LOGGER = LoggerFactory.getLogger("Combiner.Main");
    static final Set<String> ACTIONS = new HashSet<>(Arrays.asList("dict", "start", "total"));
    static final Set<String> PARAMETERS = new HashSet<>(Arrays.asList("-minBit", "-maxBit", "-words", "-batchSize", "-startOffset", "-endOffset",
            "-startBatch", "-endBatch", "-targetWords", "-level", "-bit", "-workerCount", "-debug"));
    static final Set<String> COMBINATION_REQUIRED = new HashSet<>(Arrays.asList("-minBit", "-maxBit", "-words", "-batchSize"));
    static final Set<String> DICT_REQUIRED = new HashSet<>(Arrays.asList("-level", "-bit", "-words"));
    static final Set<String> TOTAL_REQUIRED = new HashSet<>(Arrays.asList("-minBit", "-maxBit", "-words"));

    public static void main(String[] args) {
        try {
            Map<String, String> argMap = toMap(args);
            boolean debug = argMap.containsKey("-debug");
            if (debug) {
                LOGGER.debug(Arrays.toString(args));
                LOGGER.debug(argMap.toString());
            }
            String action = getAction(argMap);
            switch (action) {
                case "dict":
                    callDict(argMap, debug);
                    break;
                case "start":
                    callCombination(argMap, debug);
                    break;
                case "total":
                    callTotal(argMap, debug);
                    break;
                default: throw new CommandLineModeException("未知行为: " + action);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * 调用分析字典
     */
    public static void callDict(Map<String, String> argMap, boolean debug) {
        for (String k : DICT_REQUIRED) {
            if (null == argMap.get(k)) {
                throw new CommandLineModeException("缺失必要参数: " + k);
            }
        }
        char[] words = argMap.get("-words").toCharArray();
        int bit = getInt(argMap, "-bit", true);
        int level = getInt(argMap, "-level", true);
        if (debug) {
            LOGGER.debug("调用分析字典 {} {} {}", words, bit, level);
        }
        LOGGER.info(Combiner.analysisDictionary(words, bit, level));
    }

    /**
     * 调用计算总数
     */
    public static void callTotal(Map<String, String> argMap, boolean debug) {
        for (String k : TOTAL_REQUIRED) {
            if (null == argMap.get(k)) {
                throw new CommandLineModeException("缺失必要参数: " + k);
            }
        }
        char[] words = argMap.get("-words").toCharArray();
        int minBit = getInt(argMap, "-minBit", true);
        int maxBit = getInt(argMap, "-maxBit", true);
        if (debug) {
            LOGGER.debug("调用计算总数 {} {} {}", words, minBit, maxBit);
        }
        LOGGER.info("穷举总数: " + Combiner.calculationTotalCount(words, minBit, maxBit));
    }

    /**
     * 调用穷举
     */
    public static void callCombination(Map<String, String> argMap, boolean debug) {
        for (String k : COMBINATION_REQUIRED) {
            if (null == argMap.get(k)) {
                throw new CommandLineModeException("缺失必要参数: " + k);
            }
        }
        char[] symbols = argMap.get("-words").toCharArray();
        int minBit = getInt(argMap, "-minBit", true);
        int maxBit = getInt(argMap, "-maxBit", true);
        int batchSize = getInt(argMap, "-batchSize", true);
        long startOffset = getLong(argMap, "-startOffset", false);
        long endOffset = getLong(argMap, "-endOffset", false);
        long startBatch = getLong(argMap, "-startBatch", false);
        long endBatch = getLong(argMap, "-endBatch", false);
        int workerCount = getInt(argMap, "-workerCount", false);
        char[] targetWords = argMap.getOrDefault("-targetWords", "").toCharArray();
        boolean hasTarget = targetWords.length > 0;
        CombinationParam param = new CombinationParam(symbols, minBit, maxBit, batchSize).setStartOffset(startOffset).setEndOffset(endOffset).setStartBatch(startBatch).setEndBatch(endBatch);
        if (debug) {
            LOGGER.debug("调用穷举 symbols:{} minBit:{} maxBit:{} batchSize:{} startOffset:{} endOffset:{} startBatch:{} endBatch:{} workerCount:{} targetWords:{}", symbols, minBit, maxBit, batchSize, startOffset, endOffset, startBatch, endBatch, workerCount, targetWords);
        }
        long totalCount = Combiner.calculationTotalCount(symbols, minBit, maxBit);
        if (workerCount > 1 && totalCount > batchSize) {
            callCombinationWithPool(param, workerCount, targetWords, hasTarget, totalCount, batchSize);
        } else {
            CombinationCallback callback = (payload, realLength, curBatch, curCount, id) -> {
                if (hasTarget) {
                    for (int j = 0; j < realLength; j++) {
                        if (Arrays.equals(payload[j], targetWords)) {
                            LOGGER.info("找到了" + new String(targetWords));
                            return CombinationCallback.State.INTERRUPTED;
                        }
                    }
                }
                return CombinationCallback.State.NORMAL;
            };
            new Combiner().call(param, callback);
        }
    }

    /**
     * 多线程穷举
     */
    public static void callCombinationWithPool(CombinationParam param, int workerCount, char[] targetWords, boolean hasTarget, long totalCount, int batchSize) {
        List<Combiner> combiners = new ArrayList<>(workerCount);
        long averageBatchCount = totalCount / batchSize / workerCount;
        long preBatchEnd = -1L;
        ThreadPoolExecutor pool = new ThreadPoolExecutor(workerCount, workerCount, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        for (int i = 0; i < workerCount; i++) {
            long startBatch = preBatchEnd == -1L ? 0L : preBatchEnd + 1;
            long endBatch = i == workerCount - 1 ? -1 : (i + 1) * averageBatchCount;
            CombinationCallback callback = (payload, realLength, curBatch, curCount, id) -> {
                if (hasTarget) {
                    for (int j = 0; j < realLength; j++) {
                        if (Arrays.equals(payload[j], targetWords)) {
                            LOGGER.info(Combiner.class.getSimpleName() + id + " 找到了" + new String(targetWords));
                            pool.shutdownNow();
                            return CombinationCallback.State.INTERRUPTED;
                        }
                    }
                }
                return CombinationCallback.State.NORMAL;
            };
            Combiner combiner = new Combiner(String.valueOf(i), new CombinationParam(param.getWords(), param.getMinBit(), param.getMaxBit(), batchSize).setStartBatch(startBatch).setEndBatch(endBatch), callback);
            combiners.add(combiner);
            preBatchEnd = endBatch;
        }
        combiners.forEach(pool::submit);
    }

    public static String getAction(Map<String, String> argMap) {
        List<String> actions = new ArrayList<>();
        for (String k : argMap.keySet()) {
            if (ACTIONS.contains(k)) {
                actions.add(k);
            }
        }
        if (actions.size() > 2) {
            throw new CommandLineModeException("未知行为: " + actions);
        }
        if (actions.isEmpty() || actions.get(0) == null) {
            throw new CommandLineModeException("未知命令");
        }
        return actions.get(0);
    }

    public static Map<String, String> toMap(String[] args) {
        Map<String, String> result = new HashMap<>(args.length);
        for (String arg : args) {
            String[] values = arg.split("=");
            if (values.length > 1) {
                result.put(values[0], values[1]);
            } else {
                result.put(values[0], null);
            }
        }
        return result;
    }

    public static int getInt(Map<String, String> map, String key, boolean required) {
        String val = map.get(key);
        if (null == val) {
            if (required) {
                throw new CommandLineModeException(key + "参数不存在");
            } else {
                return -1;
            }
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + "参数不是一个整形数字", e);
        }
    }

    public static List<String> getList(Map<String, String> map, String... keys) {
        List<String> result = new ArrayList<>(keys.length);
        for (String key : keys) {
            String v = map.get(key);
            if (null != v) {
                result.add(v);
            }
        }
        return result;
    }

    public static long getLong(Map<String, String> map, String key, boolean required) {
        String val = map.get(key);
        if (null == val) {
            if (required) {
                throw new CommandLineModeException(key + "参数不存在");
            } else {
                return -1;
            }
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new CommandLineModeException(key + "参数不是一个长整形数字", e);
        }
    }

    static class CommandLineModeException extends RuntimeException {
        private static final long serialVersionUID = -1432743839140914879L;

        public CommandLineModeException() {
        }

        public CommandLineModeException(String message) {
            super(message);
        }

        public CommandLineModeException(String message, Throwable cause) {
            super(message, cause);
        }

        public CommandLineModeException(Throwable cause) {
            super(cause);
        }

        public CommandLineModeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
