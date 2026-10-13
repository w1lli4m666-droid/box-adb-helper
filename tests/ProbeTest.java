package cn.boxadb.helper;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ProbeTest {
    private static void mock(final int cmd, final int arg0, final byte[] payload, final boolean badMagic,
                             AdbProbe.State expected) throws Exception {
        final ServerSocket server = new ServerSocket(0);
        Thread worker = new Thread(new Runnable() { public void run() {
            try (Socket client = server.accept()) {
                client.setSoTimeout(3000);
                DataInputStream in = new DataInputStream(client.getInputStream());
                byte[] head = new byte[24]; in.readFully(head);
                ByteBuffer request = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
                if (request.getInt() != AdbProbe.CNXN) throw new AssertionError("Expected CNXN");
                request.getInt(); request.getInt();
                int length = request.getInt(); byte[] body = new byte[length]; in.readFully(body);
                int checksum = 0; for (byte b : payload) checksum += b & 255;
                ByteBuffer reply = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
                reply.putInt(cmd).putInt(arg0).putInt(4096).putInt(payload.length).putInt(checksum)
                        .putInt(badMagic ? 0 : cmd ^ 0xffffffff);
                client.getOutputStream().write(reply.array()); client.getOutputStream().write(payload);
            } catch (Exception e) { throw new RuntimeException(e); }
        }});
        worker.start(); AdbProbe.Result result = AdbProbe.check("127.0.0.1", server.getLocalPort());
        worker.join(4000); server.close();
        if (result.state != expected) throw new AssertionError(expected + " != " + result.state);
        System.out.println("PASS " + expected + (badMagic ? " bad-magic" : ""));
    }
    public static void main(String[] args) throws Exception {
        mock(AdbProbe.AUTH, 1, new byte[20], false, AdbProbe.State.ADB_AUTH);
        mock(AdbProbe.CNXN, 0x01000000, "device::\0".getBytes("UTF-8"), false, AdbProbe.State.ADB_CONNECTED);
        mock(AdbProbe.CNXN, 0x01000000, new byte[1], true, AdbProbe.State.NOT_ADB);
        mock(AdbProbe.AUTH, 2, new byte[20], false, AdbProbe.State.NOT_ADB);
        ServerSocket closed = new ServerSocket(0); int port = closed.getLocalPort(); closed.close();
        if (AdbProbe.check("127.0.0.1", port).state != AdbProbe.State.CLOSED) throw new AssertionError("Closed port");
        System.out.println("PASS CLOSED\nAll 5 protocol cases passed.");
    }
}
