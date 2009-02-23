package org.xtreemfs.interfaces;

import java.nio.ByteBuffer;
import java.util.List;
import org.xtreemfs.common.buffer.BufferPool;
import org.xtreemfs.common.buffer.ReusableBuffer;

public class ONCRPCResponseHeader implements Serializable {

    public static final int REPLY_STAT_MSG_ACCEPTED = 0;

    public static final int REPLY_STAT_MSG_DENIED = 1;

    public static final int ACCEPT_STAT_SUCCESS = 0;

    public static final int ACCEPT_STAT_PROG_UNAVAIL = 1;

    public static final int ACCEPT_STAT_PROG_MISMATCH = 2;

    public static final int ACCEPT_STAT_PROC_UNAVAIL = 3;

    public static final int ACCEPT_STAT_GARBAGE_ARGS = 0;

    public static final int ACCEPT_STAT_SYSTEM_ERR = 5;

    public ONCRPCResponseHeader() {
        this(0);
    }

    public ONCRPCResponseHeader(int xid) {
        this(xid, REPLY_STAT_MSG_ACCEPTED);
    }

    public ONCRPCResponseHeader(int xid, int reply_stat) {
        this(xid, reply_stat, ACCEPT_STAT_SUCCESS);
    }

    public ONCRPCResponseHeader(int xid, int reply_stat, int accept_stat) {
        this.xid = xid;
        this.reply_stat = reply_stat;
        this.accept_stat = accept_stat;
    }

    public int getXID() {
        return xid;
    }

    public int getAcceptStat() {
        return accept_stat;
    }

    public int getReplyStat() {
        return reply_stat;
    }

    // Serializable    
    public void serialize(List<ReusableBuffer> buffers) {
        ReusableBuffer buf = BufferPool.allocate(this.getSize());
        buf.putInt(xid);
        buf.putInt(reply_stat);
        buf.putInt(accept_stat);
        buf.position(0);
        buffers.add(buf);
    }

    public void deserialize(ReusableBuffer buf) {
        xid = buf.getInt();
        reply_stat = buf.getInt();
        assert reply_stat == REPLY_STAT_MSG_ACCEPTED;
        accept_stat = buf.getInt();
    }

    public int getSize() {
        return 3 * Integer.SIZE / 8;
    }
    private int xid;

    private int reply_stat;

    private int accept_stat;

}
