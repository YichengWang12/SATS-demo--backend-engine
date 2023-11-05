package thirdpart.codec;

import io.vertx.core.buffer.Buffer;
import thirdpart.bean.CommonMsg;

public interface IMsgCodec {

    //Tcp <-> commonMsg
    Buffer encodeToBuffer(CommonMsg msg);

    CommonMsg decodeFromBuffer(Buffer buffer);
}
