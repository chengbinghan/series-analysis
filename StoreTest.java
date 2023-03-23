import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;

/**
 * @author ice
 * @date 19:51  2023/3/12
 * @description
 */
public class StoreTest {

    public static void main(String[] args) throws InterruptedException {


   /*
       int[] arr = new int[100000000];
        for (int i : arr) {
            arr[i]=i;
        }

        Thread.sleep(100000);*/




        ArrayList<BitSet> bitSets = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            BitSet bitSet = new BitSet();
            bitSets.add(bitSet);

            testDoWriteRead(bitSet);
            System.out.println();
            System.out.println("------------------------------------");

        }


        Thread.sleep(100000);
    }


    private static void testDoWriteRead(BitSet bitSet) throws InterruptedException {



        Store.initBitSet(bitSet);
        int mis = (int) (System.currentTimeMillis() / 1000 / 60);
        for (int i = 0; i < 50000; i++) {


            int randomValue;
            Random random = new Random();

           if (i < 40000) {
                randomValue = 0;
            } else if(i<45000)  {

                randomValue = random.nextInt(10000);
            }else {
               randomValue = random.nextInt(1000000);
           }

            Store.writeWithHandleDefaultValue(bitSet,  mis + i+1,randomValue);
            if(i!=0&&i%49999==0) {
                System.out.print(randomValue);
                System.out.print(",");
            }
        }
        int writeIndexValue = Store.getWriteIndexValue(bitSet);


        for (int i = 0; i < 50000; i++) {

            long value = Store.doRead(bitSet);
            if(i!=0&&i%49999==0) {
                System.out.print(value);
                System.out.print(",");
            }
        }


    }

}
