package com.ethan.bean;

import com.alipay.sofa.common.profile.ArrayUtil;
import com.alipay.sofa.jraft.rhea.client.RheaKVStore;
import com.alipay.sofa.jraft.rhea.storage.KVEntry;
import com.alipay.sofa.jraft.rhea.util.Lists;
import com.alipay.sofa.jraft.util.Bits;
import com.sun.jdi.event.ExceptionEvent;
import core.EngineApi;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import thirdpart.bean.Cmdpack;
import thirdpart.codec.IByteCodec;
import thirdpart.order.OrderCmd;

import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;


@Log4j2
public class CmdPackQueue {
    private static CmdPackQueue ourInstance = new CmdPackQueue();

    private CmdPackQueue(){}

    public static CmdPackQueue getOurInstance(){
        return ourInstance;
    }
    ////////////////////////////////////////////////

    private BlockingDeque<Cmdpack> recvCache = new LinkedBlockingDeque<>();

    public void cache(Cmdpack pack){
        recvCache.offer(pack);
    }
    ////////////////////////////////////////////////

    private RheaKVStore orderKVStore;
    private IByteCodec codec;
    private EngineApi engineApi;
    public void init(RheaKVStore orderKVStore, IByteCodec codec, EngineApi engineApi){
        this.orderKVStore = orderKVStore;
        this.codec = codec;
        this.engineApi = engineApi;

        new Thread(()->{
            while (true){
                try {
                    Cmdpack cmds = recvCache.poll(10, TimeUnit.SECONDS);
                    if(cmds != null){
                        handle(cmds);
                    }
                }catch (Exception e){
                    log.error("msg pack recvCache error",e);
                }
            }
        }).start();
    }

    private long lastPackNo = -1;

    private void handle(Cmdpack cmdpack) throws Exception {
        log.info("receive: {}" ,cmdpack);
        //NACK
        long packNo = cmdpack.getPackNo();
        if(packNo == lastPackNo + 1){
            if(CollectionUtils.isEmpty((cmdpack.getOrderCmds()))){
                return;
            }
            for(OrderCmd cmd:cmdpack.getOrderCmds()){
                engineApi.submitCommand(cmd);
            }

        }else if(packNo <= lastPackNo){
            //duplicate package
            log.error("receive duplicate package: {}",packNo);
        }else {
            //missing package
            log.info("packageNo lost from {} to {}, begin query from sequencer",lastPackNo+1,packNo);
            //request missing package
            byte[] firstKey = new byte[8];
            Bits.putLong(firstKey,0,lastPackNo+1);
            byte[] lastKey = new byte[8];
            Bits.putLong(lastKey,0,packNo+1);

            final List<KVEntry> kvEntryList = orderKVStore.bScan(firstKey,lastKey);
            if(CollectionUtils.isNotEmpty(kvEntryList)){
                List<Cmdpack> collect = Lists.newArrayList();
                for(KVEntry entry : kvEntryList){
                    byte[] value = entry.getValue();
                    if(ArrayUtils.isNotEmpty(value)){
                        collect.add(codec.deseriallize(value,Cmdpack.class));
                    }
                }
                collect.sort((o1,o2) -> (int)(o1.getPackNo() - o2.getPackNo()));
                for(Cmdpack pack :collect){
                    if(CollectionUtils.isEmpty((pack.getOrderCmds()))){
                        continue;
                    }
                    for(OrderCmd cmd:pack.getOrderCmds()){
                        engineApi.submitCommand(cmd);
                    }
                }
            }
            // error from seq: jump number
            lastPackNo = packNo;
        }
    }
}


