package com.ethan.Handler.risk.match;

import com.ethan.Handler.risk.BaseHandler;
import com.ethan.bean.command.CmdResultCode;
import com.ethan.bean.command.RbCmd;
import com.ethan.bean.orderbook.IOrderBook;
import lombok.NonNull;

import java.util.HashMap;

public class MatchHandler extends BaseHandler {

    @NonNull
    private final HashMap<String, IOrderBook> orderBookMap;

    public MatchHandler(@NonNull HashMap<String, IOrderBook> orderBookMap) {
        this.orderBookMap = orderBookMap;
    }




    @Override
    public void onEvent(RbCmd cmd, long sequence, boolean endOfBatch) throws Exception {
        if(cmd.resultCode.getCode() < 0){
            return; // 风控未通过
        }
        cmd.resultCode = processCmd(cmd);
    }

    private CmdResultCode processCmd(RbCmd cmd) {
        switch(cmd.command){
            case NEW_ORDER :
                return orderBookMap.get(cmd.code).newOrder(cmd);
            case CANCEL_ORDER :
                return orderBookMap.get(cmd.code).cancelOrder(cmd);
            case HQ_PUB:
                orderBookMap.forEach((code, orderBook) -> {
                    cmd.marketDataMap.put(code, orderBook.getL1MarketDataSnapshot());
                });
            default:
                return CmdResultCode.SUCCESS;
        }
    }

}
