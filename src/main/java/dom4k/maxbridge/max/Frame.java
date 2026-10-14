package dom4k.maxbridge.max;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.jpountz.lz4.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Slf4j
public class Frame {
    private int ver = 10;
    private int cmd = 0;
    private int seq;
    private int opcode;
    private List<Integer> leadingInts = new ArrayList<>();
    private boolean isCompress = false;
    private byte[] data;

    public Frame(int seq, int opcode, byte[] data) {
        this.seq = seq;
        this.opcode = opcode;
        this.data = data;
    }

    public void write(OutputStream os) throws IOException {
        byte[] payload = data == null ? new byte[0] : data;

        ByteBuffer bb = ByteBuffer.allocate(10 + payload.length);
        bb.put((byte) ver);
        bb.put((byte) cmd);
        bb.putShort((short) seq);
        bb.putShort((short) opcode);
        bb.putInt(payload.length & 0x00FFFFFF);
        bb.put(payload);

        os.write(bb.array());
        os.flush();

        log.debug("Message wrote: {}", this);
    }

    public void read(InputStream is) throws IOException {
        byte[] raw = is.readNBytes(10);
        if (raw.length < 10) throw new EOFException();

        ByteBuffer bb = ByteBuffer.wrap(raw);
        this.ver = bb.get() & 0xFF;
        this.cmd = bb.get() & 0xFF;
        this.seq = bb.getShort() & 0xFFFF;
        this.opcode = bb.getShort() & 0xFFFF;

        int packetLength = bb.getInt();
        this.isCompress = (packetLength >>> 24) != 0;

        int length = packetLength & 0x00FFFFFF;
        byte[] packed = is.readNBytes(length);
        if (packed.length < length) throw new EOFException();
        raw = isCompress ? lz4decompress(packed) : packed;

        int offset = skipLeadingInts(raw);
        this.data = Arrays.copyOfRange(raw, offset, raw.length);

        log.debug("Message read: {}", this);
    }

    private int skipLeadingInts(byte[] payload) {
        int offset = 0;
        int count = 0;

        while (offset < payload.length && count < 2) {
            int b = Byte.toUnsignedInt(payload[offset]);

            if (b <= 0x7f || b >= 0xe0) offset += 1;
            else if (b == 0xd0) offset += 2;
            else if (b == 0xd1) offset += 3;
            else if (b == 0xd2) offset += 5;
            else break;
            count++;
        }
        return offset;
    }

    private static final LZ4SafeDecompressor decompressor = LZ4Factory.fastestInstance().safeDecompressor();

    private static byte[] lz4decompress(byte[] compressed) {
        return decompressor.decompress(compressed, 16777215);
    }

    @Override
    public String toString() {
        return String.format("Message<ver=%d cmd=%d seq=%d opcode=%d isCompress=%s length=%d>",
                ver, cmd, seq, opcode, isCompress, data == null ? 0 : data.length);
    }
}
