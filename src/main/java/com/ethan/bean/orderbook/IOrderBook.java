package com.ethan.bean.orderbook;

import com.ethan.bean.command.CmdResultCode;
import com.ethan.bean.command.RbCmd;
import thirdpart.hq.L1MarketData;

import static thirdpart.hq.L1MarketData.L1_SIZE;

public interface IOrderBook {
    //1.新增委托能力
    CmdResultCode newOrder(RbCmd cmd);
    //2.撤单
    CmdResultCode cancelOrder(RbCmd cmd);
    //3.行情快照
    default L1MarketData getL1MarketDataSnapshot(){
        final int buySize = limitBuyBucketSize(L1_SIZE);
        final int sellSize = limitSellBucketSize(L1_SIZE);
        final L1MarketData data = new L1MarketData(buySize, sellSize);

        fillBuys(buySize, data);
        fillSells(sellSize, data);
        fillCode(data);

        data.timestamp = System.currentTimeMillis();
        return data;

    }

    void fillCode(L1MarketData data);

    void fillSells(int sellSize, L1MarketData data);

    void fillBuys(int buySize, L1MarketData data);



    int limitSellBucketSize(int l1Size);

    int limitBuyBucketSize(int l1Size);


}
