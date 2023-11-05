package com.ethan.Handler.risk;

import com.ethan.bean.command.CmdResultCode;
import com.ethan.bean.command.RbCmd;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import thirdpart.order.CmdType;

@Log4j2
@RequiredArgsConstructor
public class ExistingRiskHandler extends BaseHandler{

    @NonNull
    private MutableLongSet uidSet;
    @NonNull
    private MutableSet<String> codeSet;

    //发布行情event
    //新委托
    //撤单
    //other : 权限控制 系统关机 等等
    @Override
    public void onEvent(RbCmd cmd, long sequence, boolean endOfBatch) throws Exception{

        //如果是行情发布 则不需要进行前置风控判断
        if(cmd.command == CmdType.HQ_PUB){
            return;
        }

        if(cmd.command == CmdType.NEW_ORDER || cmd.command == CmdType.CANCEL_ORDER){
            if(!uidSet.contains(cmd.uid)){
                log.error("illegal uid: [{}]", cmd.uid);
                cmd.resultCode = CmdResultCode.RISK_INVALID_USER;
                return;
            }

            if(!codeSet.contains(cmd.code)){
                log.error("illegal code: [{}]",cmd.code);
                cmd.resultCode = CmdResultCode.RISK_INVALID_CODE;
                return;
            }
        }
        //1.用户是否存在
        //2.股票是否合法
    }
}
