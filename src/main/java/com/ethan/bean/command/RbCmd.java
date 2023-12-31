package com.ethan.bean.command;

import com.ethan.bean.orderbook.MatchEvent;
import lombok.Builder;
import lombok.ToString;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import thirdpart.hq.L1MarketData;
import thirdpart.order.CmdType;
import thirdpart.order.OrderDirection;
import thirdpart.order.OrderType;

import java.util.HashMap;
import java.util.List;

@Builder
@ToString
public class RbCmd {

    public long timestamp;

    public short mid;

    public long uid;

    public CmdType command;

    public String code;

    public OrderDirection direction;

    public long price;

    public long volume;

    public long oid;

    public OrderType orderType;

    // 保存撮合结果
    public List<MatchEvent> matchEventList;

    // 前置风控 --> 撮合 --> 发布
    public CmdResultCode resultCode;

    // 保存行情
    public HashMap<String,L1MarketData> marketDataMap;



}

