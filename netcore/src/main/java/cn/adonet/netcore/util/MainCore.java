package cn.adonet.netcore.util;

import java.net.InetSocketAddress;

import cn.adonet.netcore.service.BaseVpnService;

public class MainCore {
    private static MainCore mainCore = new MainCore();

    private volatile InetSocketAddress inetSocketAddress;

    private MainCore() {
    }
    public static MainCore getInstance() {
        return mainCore;
    }

    private volatile BaseVpnService mVpnService;


    public BaseVpnService getVpnService() {
        return mVpnService;
    }

    public void setVpnService(BaseVpnService vpnService) {
        this.mVpnService = vpnService;
    }


    public void setInetSocketAddress(InetSocketAddress inetSocketAddress) {
        this.inetSocketAddress = inetSocketAddress;
    }

    public InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }


}
