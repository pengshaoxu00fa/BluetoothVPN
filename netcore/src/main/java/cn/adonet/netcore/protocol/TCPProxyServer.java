package cn.adonet.netcore.protocol;



import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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
import cn.adonet.netcore.tunel.base.ChannelBinder;
import cn.adonet.netcore.tunel.base.IProxyChannel;
import cn.adonet.netcore.util.DebugLog;
import cn.adonet.netcore.util.MainCore;


/**
 * Created by zengzheying on 15/12/30.
 */
public class TCPProxyServer implements Runnable {

	final static ByteBuffer GL_BUFFER = ByteBuffer.allocate(20000);
	private volatile boolean isStopped;
	private short mPort;

	private Selector mSelector;
	private ServerSocketChannel mServerSocketChannel;
	private Thread mServerThread;

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
		ChannelBinder.lock.lock();
		if (mSelector != null) {
			try {
				mSelector.close();
			} catch (Exception ex) {
				DebugLog.e("TcpProxyServer mSelector.close() catch an exception: %s", ex);
			}
			mSelector = null;
		}

		if (mServerSocketChannel != null) {
			try {
				mServerSocketChannel.close();
			} catch (Exception ex) {
				DebugLog.e("TcpProxyServer mServerSocketChannel.close() catch an exception: %s", ex);
			}
			mServerSocketChannel = null;
		}
		ChannelBinder.lock.unlock();
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
			ChannelBinder.lock.lock();
			if (mSelector != null) {
				Set<SelectionKey> keys = mSelector.selectedKeys();
				if (keys != null) {
					Iterator<SelectionKey> keyIterator = keys.iterator();
					while (keyIterator.hasNext() && !isStopped) {
						SelectionKey key = keyIterator.next();
						if (key.isValid()) {
							if (key.isReadable()) {
								//可以读取读取数据
								read((ChannelBinder<SocketChannel>) key.attachment());
							} else if (key.isWritable()) {
								//可以写入数据
								//write((ChannelBinder) key.attachment());
							} else if (key.isAcceptable()) {
								//接收到连接消息
								accepted();
							}
						} else {
							//应该移除,是否应该移除通道？？？？
							DebugLog.w("%s is not valid", key);
						}
						keyIterator.remove();
					}
				}
			}
			ChannelBinder.lock.unlock();
		}
		this.stop();
		DebugLog.i("TcpServer thread exited.");
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

	private void read(ChannelBinder<SocketChannel> binder){
		try {
			ByteBuffer buffer = GL_BUFFER;
			buffer.clear();
			int bytesRead = binder.localChannel.read(buffer);
			if (bytesRead != -1) {
				buffer.flip();
				while (buffer.hasRemaining()) { //将读到的数据，转发给兄弟
					binder.proxyChannel.write(buffer);
				}
			} else{
				binder.close();
			}
		} catch (Exception ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			DebugLog.e("onReadable catch an exception: %s %s  %s %s", ex,getClass());
			if (binder != null) {
				binder.close();
			}
		}

	}


	private boolean writeLocal(ChannelBinder<SocketChannel> binder, ByteBuffer buffer) throws Exception{
		while (buffer.hasRemaining()) {
			binder.localChannel.write(buffer);
		}
		return true;
	}

	private void accepted() {
		ChannelBinder binder = null;
		try {
			SocketChannel localChannel = mServerSocketChannel.accept();
			binder = new ChannelBinder<SocketChannel>(){
				@Override
				public void onProxyChannelReady() {
					try {
						this.localChannel.register(mSelector, SelectionKey.OP_READ, this);
					} catch (Exception e){
						this.close();
					}
				}

				@Override
				public void onProxyChannelRead(Object obj) {
					//回调
					try {
						writeLocal(this, (ByteBuffer)obj);
					} catch (Exception e){
						this.close();
					}
				}
			};
			binder.localChannel = localChannel;
			//创建本地代理通道
			binder.proxyChannel = null;
			binder.proxyChannel.connect();
		} catch (Exception ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			if (binder != null) {
				binder.close();
			}
		}
	}
}
