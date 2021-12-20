package cn.adonet.netcore.tunel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by zengzheying on 15/12/31.
 */
public class RemoteTunnel extends Tunnel {

	public RemoteTunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
		super(serverAddress, selector);
	}

	@Override
	protected void onConnected(ByteBuffer buffer) throws Exception {
		onTunnelEstablished();
	}


	@Override
	protected ByteBuffer afterReceived(SelectionKey key, ByteBuffer buffer){
		return buffer;
	}

	@Override
	protected ByteBuffer beforeSend(SelectionKey key, ByteBuffer buffer){
		return buffer;
	}

	@Override
	protected void onDispose() {

	}


}
