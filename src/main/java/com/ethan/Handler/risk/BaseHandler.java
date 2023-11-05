package com.ethan.Handler.risk;

import com.ethan.bean.command.RbCmd;
import com.lmax.disruptor.EventHandler;

public abstract class BaseHandler implements EventHandler<RbCmd> {
}
