package cn.adonet.netcore.protocol;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import cn.adonet.netcore.nat.NatSession;
import cn.adonet.netcore.nat.NatSessionManager;
import cn.adonet.netcore.tcpip.CommonMethods;
import cn.adonet.netcore.tunel.Tunnel;
import cn.adonet.netcore.tunel.TunnelFactory;
import cn.adonet.netcore.util.DebugLog;
import cn.adonet.netcore.util.MainCore;


/**
 * Created by zengzheying on 15/12/30.
 */
public class TCPProxyServer implements Runnable {

	private volatile boolean isStopped;
	private short mPort;

	private Selector mSelector;
	private ServerSocketChannel mServerSocketChannel;
	private Thread mServerThread;
	private boolean isForVpn = false;
	public ReentrantLock lock = new ReentrantLock(true);

	public TCPProxyServer(int port) throws IOException {
		mSelector = Selector.open();
		mServerSocketChannel = ServerSocketChannel.open();
		mServerSocketChannel.configureBlocking(false);
		mServerSocketChannel.socket().bind(new InetSocketAddress(port));
		mServerSocketChannel.register(mSelector, SelectionKey.OP_ACCEPT);
		this.mPort = (short) mServerSocketChannel.socket().getLocalPort();

		DebugLog.i("TcpProxy listen on %s:%d success.\n", mServerSocketChannel.socket().getInetAddress()
				.toString(), this.mPort & 0xFFFF);
	}

	public void setForVpn(boolean forVpn) {
		isForVpn = forVpn;
	}

	/**
	 * 启动TcpProxyServer线程
	 */
	public void start() {
		mServerThread = new Thread(this, "TcpProxyServerThread");
		mServerThread.start();
	}

	public boolean isStopped() {
		return isStopped;
	}

	public short getPort() {
		return mPort;
	}

	public void stop() {
		isStopped = true;
		lock.lock();
		if (mSelector != null) {
			try {
				mSelector.close();
				mSelector = null;
			} catch (Exception ex) {
				DebugLog.e("TcpProxyServer mSelector.close() catch an exception: %s", ex);
			}
		}

		if (mServerSocketChannel != null) {
			try {
				mServerSocketChannel.close();
				mServerSocketChannel = null;
			} catch (Exception ex) {
				DebugLog.e("TcpProxyServer mServerSocketChannel.close() catch an exception: %s", ex);
			}
		}
		lock.unlock();
		NatSessionManager.clearSession();
		mServerThread.interrupt();
	}

	@Override
	public void run() {
		isStopped = false;

		while (true) {
			try {
				mSelector.select();
			} catch (Exception e) {
				break;
			}
			lock.lock();
			if (mSelector != null) {
				Set<SelectionKey> keys = mSelector.selectedKeys();
				if (keys != null) {
					Iterator<SelectionKey> keyIterator = keys.iterator();
					while (keyIterator.hasNext() && !isStopped) {
						SelectionKey key = keyIterator.next();
						if (key.isValid()) {
							updateSessionLastTime(key);
							if (key.isReadable()) {
								((Tunnel) key.attachment()).onReadable(key);
							} else if (key.isWritable()) {
								((Tunnel) key.attachment()).onWritable(key);
							} else if (key.isConnectable()) {
								((Tunnel) key.attachment()).onConnectable();
							} else if (key.isAcceptable()) {
								onAccepted();
							}
						} else {
							//应该移除,是否应该移除通道？？？？
							DebugLog.w("%s is not valid", key);
						}
						keyIterator.remove();
					}
				}
			}
			lock.unlock();
		}
		this.stop();
		DebugLog.i("TcpServer thread exited.");
	}

	private void updateSessionLastTime(SelectionKey key) {
		Object obj = key.attachment();
		if (obj instanceof Tunnel) {
			Tunnel tunnel = (Tunnel) obj;
			if (tunnel.getNatSession() !=  null) {
				tunnel.getNatSession().lastActivityTime = System.currentTimeMillis();
			}
			Tunnel bother = tunnel.getBrotherTunnel();
			if (bother != null && bother.getNatSession() != null) {
				bother.getNatSession().lastActivityTime = System.currentTimeMillis();
			}
		}
	}

	//获取目的地址
	private InetSocketAddress getDestAddress(SocketChannel localChannel) {
		if (MainCore.getInstance().getInetSocketAddress() == null) {
			short portKey = (short)localChannel.socket().getPort();
			NatSession session = NatSessionManager.getSession(portKey);
			return new InetSocketAddress(CommonMethods.ipIntToInet4Address(session.remoteIP), session.remotePort & 0xFFFF);
		} else {
			return MainCore.getInstance().getInetSocketAddress();
		}
	}

	void onAccepted() {
		Tunnel localTunnel = null;
		try {
			SocketChannel localChannel = mServerSocketChannel.accept();
			localTunnel = TunnelFactory.wrap(localChannel, mSelector);

			InetSocketAddress destAddress = getDestAddress(localChannel);
			if (destAddress == null) {
				throw new IllegalArgumentException("没设置目的地");
			}
			Tunnel remoteTunnel = TunnelFactory.createTunnelByConfig(destAddress, mSelector);
			//关联兄弟
			remoteTunnel.setBrotherTunnel(localTunnel);
			localTunnel.setBrotherTunnel(remoteTunnel);
			remoteTunnel.setForVpn(isForVpn);
			remoteTunnel.connect(); //开始连接
		} catch (Exception ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}

			DebugLog.e("TcpProxyServer onAccepted catch an exception: %s", ex);
			if (localTunnel != null) {
				localTunnel.dispose();
			}
		}
	}
}
