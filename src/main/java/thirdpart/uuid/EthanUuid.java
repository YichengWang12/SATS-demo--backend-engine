package thirdpart.uuid;

public class EthanUuid {
    private static EthanUuid ourInstance = new EthanUuid();

    public static EthanUuid getInstance(){
        return ourInstance;
    }

    private  EthanUuid(){

    }

    public void init(long centerId, long workerId){
        idWorker = new SnowflakeIdWorker(workerId,centerId);
    }

    private SnowflakeIdWorker idWorker;

    public long getUUID(){
        return idWorker.nextId();
    }
}
