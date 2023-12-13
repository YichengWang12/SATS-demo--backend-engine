package thirdpart.bus;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import thirdpart.bean.CommonMsg;
import lombok.extern.log4j.Log4j2;
import thirdpart.codec.IMsgCodec;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

@Log4j2
@RequiredArgsConstructor
public class MqttBusSender implements IBusSender{

    @NonNull
    private String ip;

    @NonNull
    private int port;

    @NonNull
    private IMsgCodec msgCodec;

    @NonNull
    private Vertx vertx;

    @Override
    public void startup() {
        //connect to bus
        mqttConnect();
    }

    private void mqttConnect() {
        MqttClient mqttClient = MqttClient.create(vertx);
        mqttClient.connect(port,ip,conn->{
            if(conn.succeeded()){
                log.info("connect to mqtt bus[ip:{},port:{}] success",ip,port);
                sender = mqttClient;
            }else{
                log.error("connect to mqtt bus[ip:{},port:{}] failed",ip,port);
                mqttConnect();
            }
        });
        mqttClient.closeHandler(close->{
            try {
                TimeUnit.SECONDS.sleep(5);
            }catch (Exception e){
                log.error(e);
            }
            mqttConnect();
        });
    }
    /////////////////////////////////////////
    private volatile MqttClient sender;

    @Override
    public void publish(CommonMsg commonMsg) {
        sender.publish(
                Short.toString(commonMsg.getMsgDst()),
                msgCodec.encodeToBuffer(commonMsg),
                MqttQoS.AT_LEAST_ONCE,
                false,//isDup
                false//isRetain
        );
    }
}
