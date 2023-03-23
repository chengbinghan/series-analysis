import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ice
 * @date 22:28  2023/3/17
 * @description
 */
public class StoreHelper {

    /**
     * list
     * 请求数
     * 响应时间
     * 错误数和异常数
     */
    private static ConcurrentHashMap<String, BitSet[]> map = new ConcurrentHashMap<>();

    public static BitSet[] getBitSetArrById(String id) {

        BitSet[] bitSets = map.get(id);
        if (bitSets == null) {
            BitSet requestCount = new BitSet();
            BitSet respTime = new BitSet();
            BitSet error = new BitSet();
            bitSets = new BitSet[MetricEnum.METRIC_COUNT];
            bitSets[MetricEnum.REQUEST_COUNT.getValue()] = requestCount;
            bitSets[MetricEnum.RESP_TIME.getValue()] = respTime;
            bitSets[MetricEnum.ERROR.getValue()] = error;
            map.put(id, bitSets);

        }

        return bitSets;

    }

    public static BitSet getTargetMetricBitSet(MetricEnum metricEnum, String id) {


        BitSet[] bitSets = getBitSetArrById(id);


        BitSet bitSet = bitSets[metricEnum.getValue()];
        if (bitSet.length() == 0) {

            Store.initBitSet(bitSet);
        }
        return bitSet;
    }

    public static enum MetricEnum {
        REQUEST_COUNT(0),
        RESP_TIME(1),
        ERROR(2);

        public static int METRIC_COUNT = 3;
        private int value;

        MetricEnum(int value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }

    }

}


