package cn.boxadb.helper;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Read-only protocol probe: never sends an AUTH key, shell command or settings change. */
public final class AdbProbe {
    public static final int CNXN = 0x4e584e43;
    public static final int AUTH = 0x48545541;
    public enum State { ADB_AUTH, ADB_CONNECTED, CLOSED, NOT_ADB }
    public static final class Result {
        public final State state;
        public final String detail;
        Result(State state, String detail) { this.state = state; this.detail = detail; }
        public boolean isAdb() { return state == State.ADB_AUTH || state == State.ADB_CONNECTED; }
    }
    public static Result check(String host, int port) {
        Socket socket = new Socket();
        boolean connected = false;
        try {
            socket.connect(new InetSocketAddress(host, port), 1500);
            connected = true;
            socket.setSoTimeout(1800);
            byte[] payload = new byte[] { 'h','o','s','t',':',':',0 };
            ByteBuffer header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(CNXN).putInt(0x01000000).putInt(4096).putInt(payload.length)
                    .putInt(checksum(payload)).putInt(CNXN ^ 0xffffffff);
            OutputStream output = socket.getOutputStream();
            output.write(header.array()); output.write(payload); output.flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            byte[] raw = new byte[24]; input.readFully(raw);
            ByteBuffer reply = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int command = reply.getInt();
            int arg0 = reply.getInt(); reply.getInt();
            int length = reply.getInt(), sum = reply.getInt(), magic = reply.getInt();
            if (magic != (command ^ 0xffffffff) || length < 0 || length > 4096)
                return new Result(State.NOT_ADB, "响应不是有效的 ADB 数据包");
            byte[] body = new byte[length]; input.readFully(body);
            if (sum != checksum(body)) return new Result(State.NOT_ADB, "ADB 响应校验失败");
            if (command == AUTH && arg0 == 1 && length == 20)
                return new Result(State.ADB_AUTH, "已收到 ADB 授权握手");
            if (command == CNXN && length > 0)
                return new Result(State.ADB_CONNECTED, "已收到 ADB 连接握手");
            return new Result(State.NOT_ADB, "端口开放，但没有收到预期的 ADB 握手");
        } catch (Exception error) {
            return new Result(connected ? State.NOT_ADB : State.CLOSED,
                    connected ? "端口开放，但未确认 ADB 协议" : "连接被拒绝或超时");
        } finally { try { socket.close(); } catch (Exception ignored) { } }
    }
    private static int checksum(byte[] bytes) {
        int sum = 0; for (byte b : bytes) sum += b & 0xff; return sum;
    }
}
