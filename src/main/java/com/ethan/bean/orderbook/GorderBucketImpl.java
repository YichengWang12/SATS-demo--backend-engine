package com.ethan.bean.orderbook;


import com.ethan.bean.command.RbCmd;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import thirdpart.order.OrderStatus;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Getter
@ToString
@Log4j2
public class GorderBucketImpl implements IOrderBucket {

    @Setter
    private long price;

    @Getter
    private long totalVolume = 0;

    //3. 委托列表 需要快速加入委托和快速删除委托
    private final LinkedHashMap<Long,Order> entries = new LinkedHashMap<>();

    @Override
    public void put(Order order) {
        entries.put(order.getOid(),order);
        totalVolume += order.getVolume() - order.getTradedVolume();
    }

    @Override
    public Order remove(long oid) {
        Order order = entries.remove(oid);
        if(order != null){
            totalVolume -= order.getVolume() - order.getTradedVolume();
        }
        return order;
    }

    @Override
    public long match(long volumeLeft, RbCmd triggerCmd, Consumer<Order> removeOrderCallback){
        //S 46 -> 5 10 24
        //S 45 -> 11 20 10 20
        //B 45 100
        Iterator<Map.Entry<Long,Order>> iterator = entries.entrySet().iterator();

        long volumeMatched = 0;

        while(iterator.hasNext() && volumeLeft > 0){
            //1. get the order
            Map.Entry<Long, Order> next = iterator.next();
            Order order = next.getValue();

            //2. calculate the traded volume, how much the volume can be traded
            long tradedVolume = Math.min(volumeLeft,order.getVolume() - order.getTradedVolume());
            volumeMatched += tradedVolume;

            //3. update the order: order自身的量， 剩余的量， bucket总委托量
            order.setTradedVolume(order.getTradedVolume() + tradedVolume);
            volumeLeft -= tradedVolume;
            totalVolume -= tradedVolume;

            //4.生成成交事件
            boolean isFullTraded = order.getVolume() == order.getTradedVolume();
            genMatchEvent(order,triggerCmd,isFullTraded,volumeLeft == 0,tradedVolume);

            //5. 如果是全部成交，那么需要报告上层并且删除委托
            if(isFullTraded){
                removeOrderCallback.accept(order);
                iterator.remove();
            }

        }
        //把最后总的单子成交量返回
        return volumeMatched;
    }

    private void genMatchEvent(final Order order, final RbCmd triggerCmd, boolean isFullTraded, boolean cmdFullMatch, long tradedVolume) {

        long now = System.currentTimeMillis();

        //成交编号
        long tid = IOrderBucket.tidGen.getAndIncrement();

        //两个match event
        MatchEvent bidEvent = new MatchEvent();
        bidEvent.timestamp = now;
        bidEvent.mid = triggerCmd.mid;
        bidEvent.oid = triggerCmd.oid;
        //根据成交量判断是部分成交还是全部成交
        //如果一笔单子进来，与多个委托发生成交，那么它会发出多个成交事件
        bidEvent.status = cmdFullMatch ? OrderStatus.TRADE_ED : OrderStatus.PART_TRADE;
        //tid是成交编号，如果一个单子和另外一个单子发生成交，那么这两个单子的tid是一样的
        bidEvent.tid = tid;
        bidEvent.volume = tradedVolume;
        bidEvent.price = order.getPrice();
        triggerCmd.matchEventList.add(bidEvent);

        MatchEvent ofrEvent = new MatchEvent();
        ofrEvent.timestamp = now;
        ofrEvent.mid = order.getMid();
        ofrEvent.oid = order.getOid();
        ofrEvent.status = isFullTraded ? OrderStatus.TRADE_ED : OrderStatus.PART_TRADE;
        ofrEvent.tid = tid;
        ofrEvent.volume = tradedVolume;
        ofrEvent.price = order.getPrice();
        triggerCmd.matchEventList.add(ofrEvent);

    }

    @Override
    public boolean equals(Object o){
        if(o == this) return true;
        if(o == null || o.getClass() != this.getClass()) return false;
        GorderBucketImpl that = (GorderBucketImpl) o;
        return new EqualsBuilder()
                .append(this.price,that.price)
                .append(this.entries,that.entries)
                .isEquals();
    }

    @Override
    public int hashCode(){
        return new HashCodeBuilder(17,37)
                .append(price)
                .append(entries)
                .toHashCode();
    }
}
