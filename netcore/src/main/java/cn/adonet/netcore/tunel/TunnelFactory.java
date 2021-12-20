package cn.adonet.netcore.tunel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.adonet.netcore.util.MainCore;


/**
 * Created by zengzheying on 15/12/30.
 */
public class TunnelFactory {

	public static Tunnel wrap(SocketChannel channel, Selector selector) {
		Tunnel tunnel = new RawTunnel(channel, selector);
		((RawTunnel)tunnel).setDataHandler(MainCore.getInstance().getDataHandler());
		return tunnel;
	}

	public static Tunnel createTunnelByConfig(InetSocketAddress destAddress, Selector selector) throws IOException {
		return new RemoteTunnel(destAddress, selector);
	}
}
