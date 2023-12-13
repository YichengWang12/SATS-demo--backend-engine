package com.ethan.Handler;

import com.ethan.bean.command.RbCmd;
import com.lmax.disruptor.EventHandler;

public abstract class BaseHandler implements EventHandler<RbCmd> {
}
