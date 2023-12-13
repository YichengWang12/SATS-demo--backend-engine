package core;

import com.ethan.Handler.BaseHandler;
import com.ethan.Handler.exception.DisruptorExceptionHandler;
import com.ethan.bean.command.RbCmd;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.ethan.bean.RbCmdFactory;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import thirdpart.order.CmdType;
import thirdpart.order.OrderCmd;

import java.util.Timer;
import java.util.TimerTask;

import static com.ethan.Handler.pub.L1PubHandler.HQ_PUB_RATE;


@Log4j2
public class EngineCore {
    @Getter
    private final Disruptor<RbCmd> disruptor;

    private static final int RING_BUFFER_SIZE = 1024;

    @Getter
    private final EngineApi api;

    public EngineCore(
            @NonNull final BaseHandler existingRiskHandler,
            @NonNull final BaseHandler matchHandler,
            @NonNull final BaseHandler pubHandler
            ){
        this.disruptor = new Disruptor<>(
            new RbCmdFactory(),
            RING_BUFFER_SIZE,
            new AffinityThreadFactory("aft_engine_core",AffinityStrategies.ANY),

            ProducerType.SINGLE,
            new BlockingWaitStrategy()
        );

        this.api = new EngineApi(disruptor.getRingBuffer());

        //1.全局异常处理器
        final DisruptorExceptionHandler<RbCmd> exceptionHandler = new DisruptorExceptionHandler<>(
                "main", (ex, seq) -> {
            log.error("exception thrown on seq={}", seq, ex);
        });
        disruptor.setDefaultExceptionHandler(exceptionHandler);
        //2.前置风控 -> 撮合 -> 发布行情
        disruptor.handleEventsWith(existingRiskHandler)
                .then(matchHandler)
                .then(pubHandler);
        //3.启动
        disruptor.start();
        log.info("engine core started");
        //4.定时发布行情任务
        new Timer().schedule(new HqPubTask(),1000,HQ_PUB_RATE);

    }

    private class HqPubTask extends TimerTask{
        @Override
        public void run() {
            api.submitCommand(OrderCmd.builder().type(CmdType.HQ_PUB).build());
        }
    }

}
