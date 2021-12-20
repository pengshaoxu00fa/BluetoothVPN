package cn.adonet.netcore.tunel;

import java.nio.ByteBuffer;

import cn.adonet.netcore.nat.NatSession;

public interface IDataHandler {
    int TYPE_CATCH = 1;
    int TYPE_INJECT = 2;

    int getType();
    //发送出去之前
    ByteBuffer beforeSend(NatSession session, ByteBuffer buffer);

    //接受消息之后
    ByteBuffer afterReceived(NatSession session, ByteBuffer buffer);

    //是否已经废弃
    boolean isDispose();

    //废弃掉
    void dispose();
}
