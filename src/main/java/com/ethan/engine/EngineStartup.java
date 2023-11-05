package com.ethan.engine;

import com.ethan.bean.EngineConfig;
import thirdpart.checksum.ByteCheckSum;
import thirdpart.codec.IByteCodecImpl;
import thirdpart.codec.IMsgCodecImpl;

public class EngineStartup {
    public static void main(String[] args) throws Exception {
        new EngineConfig(
                "engine.properties",
                new IByteCodecImpl(),
                new ByteCheckSum(),
                new IMsgCodecImpl()
        ).startup();
    }
}
