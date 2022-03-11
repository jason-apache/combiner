package cool.lazy.cat.combiner;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: mahao
 * @date: 2022-03-07 17:38
 */
public class DigitUtil {

    /**
     * 将任意进制转换为10进制
     * @param input 值
     * @param symbols 该进制所有符号
     * @return 对应10进制的值
     */
    public static long anyToTen(String input, char[] symbols) {
        Map<Character, Integer> map = new HashMap<>();
        // 生成符号索引
        for (int i = 0; i < symbols.length; i++) {
            map.put(symbols[i], i);
        }
        return anyToTen(input.toCharArray(), symbols, map);
    }

    public static long anyToTen(char[] input, char[] symbols, Map<Character, Integer> codeDictionary) {
        int size = input.length;
        long num = 0;
        for (int i = 0; i < size; i++) {
            double weight = Math.pow(symbols.length, size - i - 1);
            Integer indexOf = codeDictionary.get(input[i]);
            num += indexOf * weight;
        }
        return num;
    }

    /**
     * 将十进制转换为任意进制
     * @param input 值
     * @param symbols 该进制所有符号
     * @return 对应进制的值
     */
    public static String tenToAny(long input, char[] symbols) {
        if (input < symbols.length) {
            return String.valueOf(symbols[(int) input]);
        }
        return tenToAny(input / symbols.length, symbols) + symbols[(int) (input % symbols.length)];
    }
}
