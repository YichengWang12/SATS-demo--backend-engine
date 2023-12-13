package com.ethan.Handler.pub;

import com.ethan.Handler.BaseHandler;
import com.ethan.bean.EngineConfig;
import com.ethan.bean.command.RbCmd;
import com.ethan.bean.orderbook.MatchEvent;
//import io.netty.util.collection.IntObjectHashMap;
import org.apache.commons.collections4.CollectionUtils;
import org.eclipse.collections.impl.map.mutable.primitive.ShortObjectHashMap;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.collections.api.tuple.primitive.ShortObjectPair;
import thirdpart.bean.CommonMsg;
import thirdpart.hq.L1MarketData;
import thirdpart.hq.MatchData;
import thirdpart.order.CmdType;

import java.util.List;
import java.util.Map;

import static thirdpart.bean.MsgConstants.*;

@Log4j2
@RequiredArgsConstructor
public class L1PubHandler extends BaseHandler {

    public static final int HQ_PUB_RATE = 5000;

    @NonNull
    //cache
    private final ShortObjectHashMap<List<MatchData>> matcherEventMap;

    @NonNull
    private EngineConfig config;

    @Override
    public void onEvent(RbCmd cmd, long sequence, boolean endOfBatch) throws Exception {
        final CmdType cmdType = cmd.command;
        if(cmdType == CmdType.NEW_ORDER|| cmdType == CmdType.CANCEL_ORDER){
            for(MatchEvent e: cmd.matchEventList){
                matcherEventMap.get(e.mid).add(e.copy());
            }
        }else if(cmdType == CmdType.HQ_PUB){
            //1.5档行情:给所有柜台发送
            pubMarketData(cmd.marketDataMap);
            //2.给某一个柜台发送 : match data
            pubMatcherData();
        log.info("L1PubHandler: {}", cmd);
        }
    }

    private void pubMatcherData() {
        if(matcherEventMap.size()==0){
            return;
        }
        log.info(matcherEventMap);
        try {
            for(ShortObjectPair<List<MatchData>>s: matcherEventMap.keyValuesView()){
                //s.one : mid
                //s.two : List<MatchData>
                if(CollectionUtils.isEmpty(s.getTwo())){
                    continue;
                }
                byte[] serialize = config.getByteCodec().seriallize(s.getTwo().toArray(new MatchData[0]));
                pubData(serialize, s.getOne(), MATCH_ORDER_DATA);
                //清空
                s.getTwo().clear();
            }
        }catch (Exception e){
            log.error("serialize match data error: {}", e);
        }
    }

    //需要表明 我往什么样的地址上发送 是五档行情
    public static final short HQ_ADDRESS = -1;
    private void pubMarketData(Map<String,L1MarketData> marketDataMap) {
        log.info(marketDataMap);
        byte[] serialize = null;
        try{
            serialize = config.getByteCodec().seriallize(marketDataMap.values().toArray(new L1MarketData[0]));
        }catch (Exception e){
            log.error("serialize market data error: {}", e);
        }

        if(serialize == null){
            return;
        }

        pubData(serialize, HQ_ADDRESS, MATCH_HQ_DATA);
    }

    private void pubData(byte[] serialize, short dst, short msgType) {
        //包装commonMsg ： 所有在总线上的数据都应该是commonMsg类型
        CommonMsg msg = new CommonMsg();
        msg.setBodyLength(serialize.length);
        msg.setChecksum(config.getCs().getCheckSum(serialize));
        msg.setMsgSrc(config.getId());
        msg.setMsgDst(dst);
        msg.setMsgType(msgType);
        msg.setStatus(NORMAL);
        msg.setBody(serialize);
        config.getBusSender().publish(msg);
    }

}

