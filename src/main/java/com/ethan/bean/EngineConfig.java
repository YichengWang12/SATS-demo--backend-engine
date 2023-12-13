package com.ethan.bean;


import com.alipay.sofa.jraft.rhea.client.DefaultRheaKVStore;
import com.alipay.sofa.jraft.rhea.client.RheaKVStore;
import com.alipay.sofa.jraft.rhea.options.PlacementDriverOptions;
import com.alipay.sofa.jraft.rhea.options.RegionRouteTableOptions;
import com.alipay.sofa.jraft.rhea.options.RheaKVStoreOptions;
import com.alipay.sofa.jraft.rhea.options.configured.MultiRegionRouteTableOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.PlacementDriverOptionsConfigured;
import com.alipay.sofa.jraft.rhea.options.configured.RheaKVStoreOptionsConfigured;
import com.ethan.Handler.BaseHandler;
import com.ethan.Handler.pub.L1PubHandler;
import com.ethan.Handler.risk.ExistingRiskHandler;
import com.ethan.Handler.match.MatchHandler;
import com.ethan.bean.orderbook.GOrderBookImpl;
import com.ethan.bean.orderbook.IOrderBook;
import com.ethan.db.DbQuery;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import core.EngineApi;
//import io.netty.handler.codec.CodecException;
//import io.netty.util.collection.IntObjectHashMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
//import com.google.common.collect.Lists;

import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.dbutils.QueryRunner;
import org.eclipse.collections.impl.map.mutable.primitive.ShortObjectHashMap;
import thirdpart.bean.Cmdpack;
import thirdpart.bus.IBusSender;
import thirdpart.bus.MqttBusSender;
import thirdpart.checksum.ICheckSum;
import thirdpart.codec.IByteCodec;
import thirdpart.codec.IMsgCodec;
import thirdpart.hq.MatchData;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.*;


@Log4j2
@Getter
@RequiredArgsConstructor
@ToString
public class EngineConfig {
    private short id;

    private String orderRecvIp;
    private short orderRecvPort;

    private String sequrlList;

    private String pubIp;
    private short pubPort;

    @NonNull
    private String fileName;

    @NonNull
    private IByteCodec byteCodec;

    @NonNull
    private ICheckSum cs;

    @NonNull
    private IMsgCodec msgCodec;

    private final Vertx vertx = Vertx.vertx();

    public void startup() throws Exception{
        //1. initialize engine
        initConfig();
        //2. initialize DB connection
        initDB();
        //3. start engine
        startEngine();
        //4. initialize Pub connection
        initPub();
        //5. initialize seq(KV store) connection
        initSeqConnection();
    }
//////////////////////////////////

    @Getter
    private IBusSender busSender;
    private void initPub() {
        busSender = new MqttBusSender(pubIp,pubPort,msgCodec,vertx);
        busSender.startup();
    }
//////////////////////////////////
    private void startEngine() throws Exception {
        //1.前置风控处理器
        final BaseHandler riskHandler = new ExistingRiskHandler(
                db.queryAllBalance().keySet(),
                db.queryAllStockCode()
        );
        //2.撮合处理器 (order book) 撮合/提供行情查询
        HashMap<String,IOrderBook> orderBookMap = new HashMap<>();
        db.queryAllStockCode().forEach((code) -> {
            orderBookMap.put(code, new GOrderBookImpl(code));
        });

        final BaseHandler matchHandler = new MatchHandler(orderBookMap);


        //3.发布处理器
        ShortObjectHashMap<List<MatchData>> matcherEventMap = new ShortObjectHashMap<>();
        for(short id: db.queryAllMemberIds()){
            matcherEventMap.put(id, new ArrayList<>());
        }
        final BaseHandler pubHandler = new L1PubHandler(
                matcherEventMap,this
        );
    }

    /**
     * init db
     */
    @Getter
    private DbQuery db;

    private void initDB() {
        QueryRunner runner = new QueryRunner(new ComboPooledDataSource());
        db = new DbQuery(runner);
        
    }

    @Getter
    private EngineApi engineApi = new EngineApi();

    @Getter
    @ToString.Exclude
    private final RheaKVStore orderKVStore = new DefaultRheaKVStore();

    private void initSeqConnection() throws Exception{
        final List<RegionRouteTableOptions> regionRouteTableOptions
                = MultiRegionRouteTableOptionsConfigured
                .newConfigured()
                .withInitialServerList(-1L,sequrlList)
                .config();
        final PlacementDriverOptions pdOptions
                = PlacementDriverOptionsConfigured
                .newConfigured()
                .withFake(true)
                .withRegionRouteTableOptionsList(regionRouteTableOptions)
                .config();
        final RheaKVStoreOptions rheaKVStoreOptions
                = RheaKVStoreOptionsConfigured
                .newConfigured()
                .withPlacementDriverOptions(pdOptions)
                .config();

        orderKVStore.init(rheaKVStoreOptions);
        ////////////////////////////////////////////////////

        //order commands handler
        CmdPackQueue.getOurInstance().init(orderKVStore,byteCodec,engineApi);

        //group cast
        DatagramSocket socket = vertx.createDatagramSocket(new DatagramSocketOptions());
        socket.listen(orderRecvPort,"0.0.0.0",asyncRes->{
            if(asyncRes.succeeded()){
                socket.handler(pack -> {
                    Buffer udpData = pack.data();
                    if(udpData.length() > 0){
                        try {
                            Cmdpack cmdpack = byteCodec.deseriallize(udpData.getBytes(), Cmdpack.class);
                            CmdPackQueue.getOurInstance().cache(cmdpack);
                        }catch (Exception e){
                            log.error("decode package error",e);
                        }
                    }else{
                        log.error("recv empty udp package from client:{}",pack.sender().toString());
                    }
                });
                try {
                    socket.listenMulticastGroup(orderRecvIp,
                            mainInterface().getName(),null,asyncRes2->{
                                log.info("listen succeed: {}",asyncRes2.succeeded());
                            });
                }catch (Exception e){
                    log.error(e);
                }
            }else{
                log.error("Listen failed:",asyncRes.cause());
            }
        });
    }

    private static NetworkInterface mainInterface() throws Exception{
        final ArrayList<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

        final NetworkInterface networkInterface = interfaces.stream().filter(t ->{
            //1. no loop back
            //2. support multicast
            //3. no vm interface
            //4. has an IpV4 address
            try {
                final boolean isLoopback = t.isLoopback();
                final boolean supportMulticast = t.supportsMulticast();
                final boolean isVirtualBox = t.getDisplayName().contains("VirtualBox") || t.getDisplayName().contains("Host-only");
                final boolean hasIpv4 = t.getInterfaceAddresses().stream().anyMatch(ia -> ia.getAddress() instanceof Inet4Address);
                return !isLoopback && supportMulticast && !isVirtualBox && hasIpv4;
            }catch (Exception e){
                log.error("fine net interface error",e);
            }
            return false;
        }).sorted(Comparator.comparing(NetworkInterface::getName)).findFirst().orElse(null);
        return networkInterface;
    }

    private void initConfig() throws Exception{
        Properties properties = new Properties();
        properties.load(this.getClass().getResourceAsStream("/" + fileName));

        id = Short.parseShort(properties.getProperty("id"));

        orderRecvIp = properties.getProperty("orderrecvip");
        orderRecvPort = Short.parseShort(properties.getProperty("orderrecvport"));

        sequrlList = properties.getProperty("sequrllist");

        pubIp = properties.getProperty("pubip");
        pubPort = Short.parseShort(properties.getProperty("pubport"));

        log.info(this);

    }
}
