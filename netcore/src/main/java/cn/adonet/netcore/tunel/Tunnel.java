package cn.adonet.netcore.tunel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.adonet.netcore.nat.NatSession;
import cn.adonet.netcore.nat.NatSessionManager;
import cn.adonet.netcore.service.BaseVpnService;
import cn.adonet.netcore.util.DebugLog;
import cn.adonet.netcore.util.MainCore;


/**
 * Created by zengzheying on 15/12/29.
 */
public abstract class Tunnel {

	final static ByteBuffer GL_BUFFER = ByteBuffer.allocate(20000);
	protected InetSocketAddress mDestAddress;
	protected SocketChannel mInnerChannel; //自己的Channel
	private ByteBuffer mSendRemainBuffer; //发送数据缓存
	private Selector mSelector;
	/**
	 * 与外网的通信两个Tunnel负责，一个负责Apps与TCP代理服务器的通信，一个负责TCP代理服务器
	 * 与外网服务器的通信，Apps与外网服务器的数据交换靠这两个Tunnel来进行
	 */
	private Tunnel mBrotherTunnel;
	private volatile boolean mDisposed;
	private boolean isForVpn = false;

	public void setForVpn(boolean forVpn) {
		isForVpn = forVpn;
	}

	public Tunnel(SocketChannel innerChannel, Selector selector) {
		mInnerChannel = innerChannel;
		mSelector = selector;
	}

	public Tunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
		SocketChannel innerChannel = SocketChannel.open();
		innerChannel.configureBlocking(false);
		this.mInnerChannel = innerChannel;
		this.mSelector = selector;
		this.mDestAddress = serverAddress;
	}


	/**
	 * 方法调用次序：
	 * connect() -> onConnectable() -> onConnected()[子类实现]
	 * beginReceived() ->  onReadable() -> afterReceived()[子类实现]
	 */

	protected abstract void onConnected(ByteBuffer buffer) throws Exception;


	protected abstract ByteBuffer beforeSend(SelectionKey key, ByteBuffer buffer) throws Exception;

	protected abstract ByteBuffer afterReceived(SelectionKey key, ByteBuffer buffer) throws Exception;

	protected abstract void onDispose();

	public void setBrotherTunnel(Tunnel brotherTunnel) {
		this.mBrotherTunnel = brotherTunnel;
	}

	public NatSession getNatSession() {
		return null;
	}

	public Tunnel getBrotherTunnel() {
		return mBrotherTunnel;
	}


	public void connect() throws Exception {
		boolean isShouldConnect;
		if (!isForVpn) {
			isShouldConnect = true;
		} else {
			BaseVpnService service = MainCore.getInstance().getVpnService();
			if (service == null || !service.isRunning()) {
				dispose();
				return;
			}
			if (service.protect(mInnerChannel.socket())) {
				isShouldConnect = true;
			} else {
				isShouldConnect = false;
			}
		}
		if (isShouldConnect) {
			mInnerChannel.register(mSelector, SelectionKey.OP_CONNECT, this); //注册连接事件
			mInnerChannel.connect(mDestAddress);
			DebugLog.i("Connecting to %s", mDestAddress);
		} else {
			throw new Exception("VPN protect socket failed.");
		}
	}

	public void onConnectable() {
		try {
			if (mInnerChannel.finishConnect()) {
				onConnected(GL_BUFFER); //通知子类TCP已连接，子类可以根据协议实现握手等
				DebugLog.i("Connected to %s", mDestAddress);
			} else {
				DebugLog.e("Connect to %s failed.", mDestAddress);
				dispose();
			}
		} catch (Exception e) {
			if (DebugLog.IS_DEBUG) {
				e.printStackTrace();
			}
			DebugLog.e("Connect to %s failed: %s", mDestAddress, e);
			dispose();
		}
	}

	protected void beginReceived() throws Exception {
		if (mInnerChannel.isBlocking()) {
			mInnerChannel.configureBlocking(false);
		}
		mInnerChannel.register(mSelector, SelectionKey.OP_READ, this); //注册读事件
	}

	public void onReadable(SelectionKey key) {
		if (mInnerChannel == null) {
			dispose();
			return;
		}
		try {
			ByteBuffer buffer = GL_BUFFER;
			buffer.clear();
			int bytesRead = mInnerChannel.read(buffer);
			if (bytesRead != -1) {
				buffer.flip();
				ByteBuffer buf = afterReceived(key, buffer);
				if (buf != null) { //先让子类处理，例如解密数据
					sendToBrother(key, buf);
				}
			} else{
				dispose();
			}
		} catch (Exception ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			DebugLog.e("onReadable catch an exception: %s %s  %s %s", ex,getClass(), mDestAddress, mDestAddress);
			dispose();
		}
	}

	protected void sendToBrother(SelectionKey key, ByteBuffer buffer) throws Exception {
		if (mBrotherTunnel == null) {
			dispose();
			return;
		}
		if (buffer.hasRemaining()) { //将读到的数据，转发给兄弟
			ByteBuffer buf = mBrotherTunnel.beforeSend(key, buffer);//发送之前，先让子类处理，例如做加密等。
			if (buf != null) {
				if (!mBrotherTunnel.write(buf)) {
					key.cancel(); //兄弟吃不消，就取消读取事件, 会导致所以selector的相关时间都会被取消
					DebugLog.w("%s can not read more.\n", mDestAddress);
				}
			}
		}
	}

	private boolean write(ByteBuffer buffer) throws Exception {
		if (mInnerChannel == null) {
			return true;
		}
		int byteSent;
		int count = 0;
		while (buffer.hasRemaining() && count <= 100) {
			byteSent = mInnerChannel.write(buffer);
			count = (byteSent <= 0 ? (count + 1) : 0);
		}
		if (buffer.hasRemaining()) { //数据没有发送完毕
			//拷贝剩余数据
			if (mSendRemainBuffer == null) {
				mSendRemainBuffer = ByteBuffer.allocate(buffer.capacity());
			}
			//说明是发送重试
			if (buffer != mSendRemainBuffer) {
				mSendRemainBuffer.clear();
				mSendRemainBuffer.put(buffer);
				mSendRemainBuffer.flip();
				mInnerChannel.register(mSelector, SelectionKey.OP_WRITE, this); //注册写事件
			}
			return false;
		} else { //发送完毕了
			return true;
		}
	}


	public void onWritable(SelectionKey key) {
		if (mBrotherTunnel == null) {
			dispose();
			return;
		}
		try {
			if (this.write(mSendRemainBuffer)) { //如果剩余数据已经发送完毕
				beginReceived();
				mBrotherTunnel.beginReceived();
			}
		} catch (Exception ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			DebugLog.e("onWritable catch an exception: %s", ex);
			this.dispose();
		}
	}

	protected void onTunnelEstablished() throws Exception {
		this.beginReceived(); //开始接收数据
		mBrotherTunnel.beginReceived(); //兄弟也开始接收数据吧
	}

	public void dispose() {
		synchronized (NatSessionManager.class) {
			if (mDisposed) {
				return;
			}
			mDisposed = true;
			if (getNatSession() != null) {
				NatSessionManager.removeSession(getNatSession());
			}
		}
		try {
			mInnerChannel.close();
		} catch (Exception ex) {
			if (DebugLog.IS_DEBUG) {
				ex.printStackTrace(System.err);
			}
			DebugLog.e("InnerChannel close catch an exception: %s", ex);
		}
		mInnerChannel = null;
		mSendRemainBuffer = null;
		mSelector = null;
		mBrotherTunnel = null;
		onDispose();

		if (mBrotherTunnel != null) {
			mBrotherTunnel.dispose(); //把兄弟的资源也释放了
		}
	}


}
