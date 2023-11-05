package thirdpart.codec;

public interface IByteCodec {

    //1. obj -> byte[]
    <T> byte[] seriallize(T obj) throws Exception;
    //2. byte[] -> obj
    <T> T deseriallize(byte[] data, Class<T> tClass) throws Exception;
}
