package com.ethan.bean.orderbook;

import com.ethan.bean.command.RbCmd;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public interface IOrderBucket extends Comparable<IOrderBucket>{
    //1. add order
    void put(Order order);
    //2. remove order
    Order remove(long oid);
    //3. match order
    long match(long volumeLeft, RbCmd triggerCmd, Consumer<Order> removeOrderCallback);

    //4. 行情发布
    long getPrice();
    long setPrice(long price);
    long getTotalVolume();

    AtomicLong tidGen = new AtomicLong(0);

    //5. Initialize
    static IOrderBucket create(OrderBucketImplType type) {
        switch (type){
            case ETHAN:
                return new GorderBucketImpl();
            default:
                throw new IllegalArgumentException("unknown type");
        }
    }

    @Getter
    enum OrderBucketImplType{
        ETHAN(0);

        private byte code;

        OrderBucketImplType(int code) {
            this.code = (byte)code;
        }
    }

    //6. compare
    default int compareTo(IOrderBucket o){
        return Long.compare(this.getPrice(),o.getPrice());
    }


}
