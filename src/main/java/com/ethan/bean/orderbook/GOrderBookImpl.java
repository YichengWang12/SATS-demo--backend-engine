package com.ethan.bean.orderbook;

import com.ethan.bean.command.CmdResultCode;
import com.ethan.bean.command.RbCmd;
import com.google.common.collect.Lists;
import io.netty.util.collection.LongObjectHashMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import thirdpart.hq.L1MarketData;
import thirdpart.order.OrderDirection;
import thirdpart.order.OrderStatus;

import java.util.*;


@Log4j2
@RequiredArgsConstructor
public class GOrderBookImpl implements IOrderBook {

    @NonNull
    private String code;

    //卖单桶//价格-->桶
    private final NavigableMap<Long, IOrderBucket> sellBuckets = new TreeMap<>();
    //买单桶//价格-->桶 降序排序
    private final NavigableMap<Long, IOrderBucket> buyBuckets= new TreeMap<>(Collections.reverseOrder());
    //存放所有委托的缓存，用于快速查找
    private final LongObjectHashMap<Order> oidMap = new LongObjectHashMap<>();

    @Override
    public CmdResultCode newOrder(RbCmd cmd) {

        //1.判断是否是重复的委托
        if(oidMap.containsKey(cmd.oid)){
            return CmdResultCode.DUPLICATE_ORDER_ID;
        }

        //2.如果不重复 就生成一个新的委托
        //2.1 预先撮合：因为单子进来时，可能就跟当前已经存在的单子就可以撮合
        //需要找到跟这个订单最匹配的orderBucket 并且找到最合适的单子
        //S 50 100 就要筛选 买单桶中价格>=50的桶
        //B 50 100 就要筛选 卖单桶中价格<=50的桶
        final NavigableMap<Long, IOrderBucket> subMatchBuckets =
                cmd.direction == OrderDirection.BUY ? sellBuckets : buyBuckets
                        .headMap(cmd.price, true);

        long tVolume = preMatch(cmd, subMatchBuckets);
        if (tVolume == cmd.volume) {
            //如果预撮合的量等于委托量，说明这个委托已经完全成交了
            return CmdResultCode.SUCCESS;
        }

        Order order = Order.builder()
                .mid(cmd.mid)
                .uid(cmd.uid)
                .oid(cmd.oid)
                .code(cmd.code)
                .direction(cmd.direction)
                .price(cmd.price)
                .volume(cmd.volume)
                .tradedVolume(0)
                .timestamp(cmd.timestamp)
                .build();

        if(tVolume == 0){
            //如果没有成交量， 我们就对外发送一个Event 通知单子已经进来了
            genMatchEvent(cmd, OrderStatus.ORDER_ED);
        }else{
            //如果有成交量，我们就对外发送一个Event 通知单子部分成交了
            genMatchEvent(cmd, OrderStatus.PART_TRADE);
        }
        //3.将委托放入对应的桶中
        final IOrderBucket bucket = (cmd.direction == OrderDirection.BUY ? buyBuckets : sellBuckets)
                .computeIfAbsent(cmd.price, p -> {
                    IOrderBucket b = IOrderBucket.create(IOrderBucket.OrderBucketImplType.ETHAN);
                    b.setPrice(p);
                    return b;
                });
        bucket.put(order);
        //4.将委托放入缓存中
        oidMap.put(cmd.oid,order);
        return null;
    }

    private long preMatch(RbCmd cmd, NavigableMap<Long, IOrderBucket> matchingBuckets) {
        long tVolume = 0;
        if(matchingBuckets.isEmpty()){
            return tVolume;
        }

        List<Long> emptyBuckets = Lists.newArrayList();
        for(IOrderBucket bucket : matchingBuckets.values()){
            tVolume += bucket.match(cmd.volume - tVolume,cmd,order ->{
                oidMap.remove(order.getOid());
            });

            if(bucket.getTotalVolume() == 0){
                //如果桶中没有委托了，则删除桶
                emptyBuckets.add(bucket.getPrice());
            }

            if(tVolume == cmd.volume){
                break;
            }
        }

        //删除空桶
        emptyBuckets.forEach(matchingBuckets::remove);

        return tVolume;
    }


    /**
     * 生成matchevent 放入RbCmd的列表中
     * @param cmd
     * @param orderStatus
     */
    private void genMatchEvent(RbCmd cmd, OrderStatus orderStatus) {
        long now = System.currentTimeMillis();
        MatchEvent matchEvent = new MatchEvent();
        matchEvent.timestamp = now;
        matchEvent.mid = cmd.mid;
        matchEvent.oid = cmd.oid;
        matchEvent.status = orderStatus;
        matchEvent.volume = 0;
        cmd.matchEventList.add(matchEvent);
    }

    @Override
    public CmdResultCode cancelOrder(RbCmd cmd) {
        //1.从缓存中撤掉委托
        Order order = oidMap.get(cmd.oid);
        if(order == null){
            return CmdResultCode.INVALID_ORDER_ID;
        }
        oidMap.remove(order.getOid());
        //2.从对应的桶中撤掉委托

        final NavigableMap<Long, IOrderBucket> buckets = order.getDirection() == OrderDirection.BUY ? buyBuckets : sellBuckets;
        IOrderBucket orderBucket = buckets.get(order.getPrice());
        orderBucket.remove(order.getOid());
        //如果桶中没有委托了，则删除桶
        if(orderBucket.getTotalVolume() == 0){
            buckets.remove(order.getPrice());
        }
        //3. 发送撤单事件
        MatchEvent cancelEvent = new MatchEvent();
        cancelEvent.timestamp = System.currentTimeMillis();
        cancelEvent.mid = order.getMid();
        cancelEvent.oid = order.getOid();
        cancelEvent.status = order.getTradedVolume() == 0 ? OrderStatus.CANCEL_ED : OrderStatus.PART_CANCEL;
        cancelEvent.volume = order.getTradedVolume() - order.getVolume();
        cmd.matchEventList.add(cancelEvent);
        return CmdResultCode.SUCCESS;
    }

    @Override
    public L1MarketData getL1MarketDataSnapshot() {
        return IOrderBook.super.getL1MarketDataSnapshot();
    }

    @Override
    public void fillCode(L1MarketData data) {
        data.code = code;
    }

    @Override
    public void fillSells(int sellSize, L1MarketData data) {
        if(sellSize == 0){
            data.sellSize = 0;
            return;
        }

        int i = 0;
        for(IOrderBucket bucket : sellBuckets.values()){
            data.sellPrices[i] = bucket.getPrice();
            data.sellVolumes[i] = bucket.getTotalVolume();
            i++;
            if(i == sellSize){
                break;
            }
        }
    }

    @Override
    public void fillBuys(int buySize, L1MarketData data) {
        if(buySize == 0){
            data.buySize = 0;
            return;
        }
        int i = 0;
        for(IOrderBucket bucket : buyBuckets.values()){
            data.buyPrices[i] = bucket.getPrice();
            data.buyVolumes[i] = bucket.getTotalVolume();
            i++;
            if(i == buySize){
                break;
            }
        }
    }

    @Override
    public int limitSellBucketSize(int l1Size) {
        return Math.min(l1Size,sellBuckets.size());
    }

    @Override
    public int limitBuyBucketSize(int l1Size) {
        return Math.min(l1Size,buyBuckets.size());
    }

}
