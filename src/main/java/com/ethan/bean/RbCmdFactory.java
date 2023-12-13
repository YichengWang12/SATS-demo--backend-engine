package com.ethan.bean;

import com.ethan.bean.command.CmdResultCode;
import com.ethan.bean.command.RbCmd;
import com.google.common.collect.Lists;
import com.lmax.disruptor.EventFactory;

import java.util.HashMap;

public class RbCmdFactory implements EventFactory<RbCmd> {
    @Override
    public RbCmd newInstance() {
        return RbCmd.builder()
                .resultCode(CmdResultCode.SUCCESS)
                .matchEventList(Lists.newArrayList())
                .marketDataMap(new HashMap<>())
                .build();
    }

}
