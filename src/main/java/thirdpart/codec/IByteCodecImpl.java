package thirdpart.codec;

import com.alipay.remoting.serialization.SerializerManager;

public class IByteCodecImpl implements IByteCodec {
    @Override
    public <T> byte[] seriallize(T obj) throws Exception {
        //Hessian2
        return SerializerManager.getSerializer(SerializerManager.Hessian2).serialize(obj);
    }

    @Override
    public <T> T deseriallize(byte[] data, Class<T> tClass) throws Exception {
        return SerializerManager.getSerializer(SerializerManager.Hessian2).deserialize(data,tClass.getName());

    }
}
