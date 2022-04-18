package cn.adonet.netcore.tunel.base;

public interface IProxyChannel {
    void connect();

    void write(Object obj);

    void close();

    boolean isOpen();

    ChannelBinder getBinder();

}
