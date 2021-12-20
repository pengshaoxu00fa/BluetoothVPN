package cn.adonet.netcore.util;

import java.net.InetSocketAddress;

import cn.adonet.netcore.service.BaseVpnService;
import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.tunel.IDataHandler;

public class MainCore {
    private static MainCore mainCore = new MainCore();

    private volatile InetSocketAddress inetSocketAddress;

    private MainCore() {
    }
    public static MainCore getInstance() {
        return mainCore;
    }

    private volatile BaseVpnService mVpnService;

    private volatile IDataHandler mDataHandler;


    public BaseVpnService getVpnService() {
        return mVpnService;
    }

    public void setVpnService(BaseVpnService vpnService) {
        this.mVpnService = vpnService;
    }

    public IDataHandler getDataHandler() {
        return mDataHandler;
    }

    public void setDataHandler(IDataHandler mDataHandler) {
        this.mDataHandler = mDataHandler;
    }


    public void setInetSocketAddress(InetSocketAddress inetSocketAddress) {
        this.inetSocketAddress = inetSocketAddress;
    }

    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }


}
