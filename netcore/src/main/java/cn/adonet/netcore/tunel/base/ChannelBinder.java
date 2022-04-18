package cn.adonet.netcore.tunel.base;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ChannelBinder<T extends SelectableChannel> {
    public static ReentrantLock lock = new ReentrantLock(true);
    public T localChannel;
    public IProxyChannel proxyChannel;
    private volatile boolean isClosed;

    public boolean isClosed() {
        return isClosed;
    }

    /**
     * 代理通道获取的内容
     * @param obj
     */
    public abstract void onProxyChannelRead(Object obj);

    /**
     * 代理通道准备好了
     */
    public abstract void onProxyChannelReady();

    public void close() {
        lock.lock();
        if (isClosed) {
            return;
        }
        try {
            if (localChannel != null) {
                localChannel.close();
            }
            localChannel = null;
        } catch (Exception e){
        }

        try {
            if (proxyChannel != null) {
                proxyChannel.close();
            }
            proxyChannel = null;
        } catch (Exception e){
        }
        isClosed = true;
        lock.unlock();
    }
}
