package cn.adonet.netcore.tunel;

import android.text.TextUtils;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import cn.adonet.netcore.nat.NatSession;
import cn.adonet.netcore.nat.NatSessionManager;
import cn.adonet.netcore.util.DebugLog;


/**
 * Created by zengzheying on 15/12/30.
 */
public class RawTunnel extends Tunnel {
	private NatSession mSession;
	private IDataHandler mDataHandler;

	public RawTunnel(SocketChannel innerChannel, Selector selector) {
		super(innerChannel, selector);
	}

	public void setDataHandler(IDataHandler mDataHandler) {
		this.mDataHandler = mDataHandler;
	}

	@Override
	protected void onConnected(ByteBuffer buffer) throws Exception {
		DebugLog.d("raw tunnel connected");
	}

	@Override
	public NatSession getNatSession() {
		if (mSession == null) {
			mSession = NatSessionManager.getSession((short) mInnerChannel.socket().getPort());
		}
		return mSession;
	}


	@Override
	protected ByteBuffer beforeSend(SelectionKey key, ByteBuffer buffer) throws Exception {
		if (mDataHandler != null && !mDataHandler.isDispose()) {
			return mDataHandler.beforeSend(getNatSession(), buffer);
		}
		return buffer;
	}

	@Override
	protected ByteBuffer afterReceived(SelectionKey key, ByteBuffer buffer) throws Exception {
		if (mDataHandler != null && !mDataHandler.isDispose()) {
			return mDataHandler.afterReceived(getNatSession(), buffer);
		}
		return buffer;
	}

	@Override
	protected void onDispose() {

	}
}
