import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;

/**
 * @author ice
 * @date 11:28  2023/3/11
 * @description
 */

public class Store {
    private static final byte HEAD_ITEM_BIT_LENGTH = 32;

    static final int END_MINUTES_INDEX = HEAD_ITEM_BIT_LENGTH * 0;
    static final int START_MINUTES_INDEX = HEAD_ITEM_BIT_LENGTH * 1;
    static final int READ_INDEX = HEAD_ITEM_BIT_LENGTH * 2;
    static final int WRITE_INDEX = HEAD_ITEM_BIT_LENGTH * 3;
    static final int ITEM_COUNT_INDEX = HEAD_ITEM_BIT_LENGTH * 4;
    static final int HEAD_BIT_SIZE = 5 * HEAD_ITEM_BIT_LENGTH;


    private static byte LENGTH_BIT_COUNT = 6;

    private static int LENGTH_BIT_MAX_SAVE_VALUE = 1 << (LENGTH_BIT_COUNT - 1);

    private static byte EXISTS_BIT_NO_DATA_VALUE = 0;


    /**
     * 一个数据段大小
     */
    static final int DEFAULT_BYTE = 512;
    /**
     * 一个数据段bit 位
     */
    static final int DEFAULT_BITS = DEFAULT_BYTE * 8;





    public static long[] getSeriesValues(BitSet bitSet, int seriesLength) {
        int itemCount = getItemCount(bitSet);
        int readIndex = getReadIndex(bitSet);
        long[] values = new long[itemCount];

        for (int i = 0; i < itemCount; i++) {
            values[i] = doRead(bitSet);
            if (itemCount - i > seriesLength) {
                readIndex = getReadIndex(bitSet);
            }
        }
        reSetReadIndex(bitSet, readIndex);
        return values;
    }

    public static double[] getSeriesDoubleValues(BitSet bitSet, int seriesLength) {
        int itemCount = getItemCount(bitSet);
        int readIndex = getReadIndex(bitSet);
        double[] values = new double[itemCount];

        for (int i = 0; i < itemCount; i++) {
            values[i] = doRead(bitSet);
            if (itemCount - i > seriesLength) {
                readIndex = getReadIndex(bitSet);
            }
        }
        reSetReadIndex(bitSet, readIndex);
        return values;
    }


    public static long doRead(BitSet bitSet) {
        int existsBit = readFixedNumberAndAddReadIndex(bitSet, (byte) 1);
        if (existsBit == 0) {
            return existsBit;
        }
        int lengthSpace4Length = readFixedNumberAndAddReadIndex(bitSet, (byte) 1);
        int lengthSpaceValue = readFixedNumberAndAddReadIndex(bitSet, LENGTH_BIT_COUNT);
        if (lengthSpace4Length == 0) {
            return lengthSpaceValue;
        } else {
            return readFixedNumberAndAddReadIndex(bitSet, (byte) lengthSpaceValue);
        }
    }

    public static void writeWithHandleDefaultValue(BitSet bitSet, int currentMinutes, long value) {
        //内存释放
        releaseDirtyMemory(bitSet);

        int endTime = getEndTime(bitSet);
        int noDataMinuteCount =  (currentMinutes - endTime);
        while (noDataMinuteCount-- > 0) {

            long t = (currentMinutes-noDataMinuteCount)*60L*1000;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = new Date(t);
            String d = sdf.format(date);


            //处理无数据
            if (noDataMinuteCount != 0) {

                doWrite(bitSet, EXISTS_BIT_NO_DATA_VALUE, currentMinutes - noDataMinuteCount);
               // System.out.println("item_count:"+Store.getItemCount(bitSet));
                continue;
            }

           // System.out.println(d+","+value);

            //处理上一分钟
            doWrite(bitSet, value, currentMinutes - noDataMinuteCount);
            //System.out.println("item_count:"+Store.getItemCount(bitSet));
        }
    }


    private static void doWrite(BitSet bitSet, long value, int minutes) {
        //|end_time|start_time|read_index|write_index|item_count|
        addAndGetItemCount(bitSet);

        writeEndTime(bitSet, minutes);
        boolean justWriteExistsBit = value == 0;
        writeExistsBit(bitSet, !justWriteExistsBit);
        writeIndexAddValue(bitSet, 1);
        if (justWriteExistsBit) {
            return;
        }


        short lengthSpaceValue;

        //当0<value<LENGTH_BIT_MAX_SAVE_VALUE时,用length保存数据
        boolean lengthSpace4Length = false;
        if (0 < value && value <= LENGTH_BIT_MAX_SAVE_VALUE) {
            lengthSpaceValue = (short) value;
        } else {
            lengthSpace4Length = true;
            lengthSpaceValue = LENGTH_BIT_COUNT;
            while ((value >>> lengthSpaceValue) > 0) {
                lengthSpaceValue++;
            }
        }
        writeIsLengthSpace4LengthBit(bitSet, lengthSpace4Length);
        writeIndexAddValue(bitSet, 1);
        writeLengthSpace(bitSet, lengthSpaceValue);
        writeIndexAddValue(bitSet, LENGTH_BIT_COUNT);

        if (!lengthSpace4Length) {
            return;
        }
        writeFixedBitNumber(value, bitSet, (byte) lengthSpaceValue);
        writeIndexAddValue(bitSet, lengthSpaceValue);
    }

    private static void writeLengthSpace(BitSet bitSet, int value) {
        writeInt(value, bitSet);
    }

    private static void writeExistsBit(BitSet bitSet, boolean value) {
        int writeIndex = getWriteIndexValue(bitSet);
        bitSet.set(writeIndex, value);
    }

    private static void writeIsLengthSpace4LengthBit(BitSet bitSet, boolean value) {
        int writeIndex = getWriteIndexValue(bitSet);
        bitSet.set(writeIndex, value);
    }

    private static void writeIndexAddValue(BitSet bitSet, int addValue) {
        int writeIndex = getWriteIndexValue(bitSet);
        writeIndex += addValue;
        writeInt(writeIndex, bitSet, WRITE_INDEX);
    }


    private static void releaseDirtyMemory(BitSet bitSet) {
        int length = bitSet.length();
        int writeIndex = getWriteIndexValue(bitSet);
        int readIndex = getReadIndex(bitSet);
        int dirtyBitLength = 0;

        dirtyBitLength = readIndex - HEAD_BIT_SIZE;

        if (dirtyBitLength <= DEFAULT_BITS) {
            return;
            //do not release;
        }

        int dirtySegmentCount = dirtyBitLength / DEFAULT_BITS;
        BitSet headBitSet = bitSet.get(0, HEAD_BIT_SIZE);
        bitSet.set(0, readIndex, false);
        //|end_time|start_time|read_index|write_index|length_byte|item_count|
        reSetWriteIndex(headBitSet, writeIndex - dirtySegmentCount * DEFAULT_BITS * Byte.SIZE);
        reSetReadIndex(headBitSet, readIndex - dirtySegmentCount * DEFAULT_BITS * Byte.SIZE);

        BitSet smallerBitSet = bitSet.get(readIndex - HEAD_BIT_SIZE, bitSet.length());
        smallerBitSet.or(headBitSet);
        bitSet = smallerBitSet;
        //log.debug("success release memory.before:{} after:{}", length, bitSet.length());

    }


    private static void reSetWriteIndex(BitSet bitSet, int value) {
        writeInt(value, bitSet, WRITE_INDEX);
    }

    private static void reSetReadIndex2Start(BitSet bitSet) {
        writeInt(HEAD_BIT_SIZE, bitSet, READ_INDEX);
    }

    private static void reSetReadIndex(BitSet bitSet, int index) {
        writeInt(HEAD_BIT_SIZE, bitSet, READ_INDEX);
    }

    private static int readFixedNumberAndAddReadIndex(BitSet bitSet, byte bitCount) {
        int value = readFixedBitAsInt(bitSet, bitCount);
        int readIndex = getReadIndex(bitSet);
        updateReadIndex(bitSet, readIndex + bitCount);
        return value;
    }

    private static void updateReadIndex(BitSet bitSet, int value) {

        writeInt(value, bitSet, READ_INDEX);
    }


    private static int getEndTime(BitSet bitSet) {
        return readInt(bitSet, END_MINUTES_INDEX);
    }

    private static int getStartTime(BitSet bitSet) {
        return readInt(bitSet, START_MINUTES_INDEX);
    }


    public static int getWriteIndexValue(BitSet bitSet) {
        return readInt(bitSet, WRITE_INDEX);
    }

    private static int getReadIndex(BitSet bitSet) {
        return readInt(bitSet, READ_INDEX);
    }

    public static int getItemCount(BitSet bitSet) {
        return readInt(bitSet, ITEM_COUNT_INDEX);
    }


    public static void writeEndTime(BitSet bitSet, int endTime) {
        writeInt(endTime, bitSet, END_MINUTES_INDEX);
    }
    public static void writeStartTime(BitSet bitSet, int endTime) {
        writeInt(endTime, bitSet, START_MINUTES_INDEX);
    }


    private static int addAndGetItemCount(BitSet bitSet) {
        int itemCount = getItemCount(bitSet);
        writeInt(++itemCount, bitSet,ITEM_COUNT_INDEX);
        return itemCount;
    }


    public static void initBitSet(BitSet bitSet) {// TODO: 2023/3/11 大端&小端 *******
        long currentTimeMillis = System.currentTimeMillis();

        //for test
        String testCurrentTimeMillis = System.getProperty("test_currentTimeMillis");
        if(testCurrentTimeMillis!=null){
            currentTimeMillis = Long.parseLong(testCurrentTimeMillis)-1;
        }
        writeInt((int) (currentTimeMillis / (60 * 1000L)), bitSet, END_MINUTES_INDEX);
        writeInt((int) (currentTimeMillis / (60 * 1000L)), bitSet, START_MINUTES_INDEX);
        writeInt(HEAD_BIT_SIZE, bitSet, READ_INDEX);
        writeInt(HEAD_BIT_SIZE, bitSet, WRITE_INDEX);
        writeInt(0, bitSet, ITEM_COUNT_INDEX);


    }


    public static void writeInt(int value, BitSet bitSet, int index) {
        //清空
        bitSet.set(index, index + Integer.SIZE, false);
        //设置新的值
        while (value != 0) {
            if (value % 2 != 0) {
                bitSet.set(index);
            }
            ++index;
            value = value >>> 1;
        }
    }


    private static void writeFixedBitNumber(long value, BitSet bitSet, byte byteLength) {
        int writeIndexValue = getWriteIndexValue(bitSet);
        bitSet.set(writeIndexValue, writeIndexValue + byteLength, false);
        //设置新的值
        while (value != 0) {
            if (value % 2 != 0) {
                bitSet.set(writeIndexValue);
            }
            ++writeIndexValue;
            value = value >>> 1;
        }
    }

    public static void writeInt(int value, BitSet bitSet) {
        int writeIndex = getWriteIndexValue(bitSet);
        writeInt(value, bitSet, writeIndex);

    }


    private static int readFixedBitAsInt(BitSet bitSet, byte bitCount) {
        int readIndex = getReadIndex(bitSet);
        int value = 0;
        for (int i = 0; i < bitCount; ++i) {
            value += bitSet.get(readIndex + i) ? (1L << i) : 0L;
        }
        return value;

    }

    private static int readInt(BitSet bitSet, int index) {
        int value = 0;
        for (int i = 0; i < 32; ++i) {
            value += bitSet.get(index + i) ? (1L << i) : 0L;
        }
        return value;
    }



}
