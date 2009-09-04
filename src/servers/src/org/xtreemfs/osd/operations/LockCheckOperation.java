/*  Copyright (c) 2009 Barcelona Supercomputing Center - Centro Nacional
    de Supercomputacion and Konrad-Zuse-Zentrum fuer Informationstechnik Berlin.

    This file is part of XtreemFS. XtreemFS is part of XtreemOS, a Linux-based
    Grid Operating System, see <http://www.xtreemos.eu> for more details.
    The XtreemOS project has been developed with the financial support of the
    European Commission's IST program under contract #FP6-033576.

    XtreemFS is free software: you can redistribute it and/or modify it under
    the terms of the GNU General Public License as published by the Free
    Software Foundation, either version 2 of the License, or (at your option)
    any later version.

    XtreemFS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with XtreemFS. If not, see <http://www.gnu.org/licenses/>.
 */
/*
 * AUTHORS: Bjoern Kolbeck (ZIB)
 */
package org.xtreemfs.osd.operations;


import org.xtreemfs.common.Capability;
import org.xtreemfs.common.buffer.ReusableBuffer;
import org.xtreemfs.common.uuids.ServiceUUID;
import org.xtreemfs.common.xloc.XLocations;
import org.xtreemfs.foundation.oncrpc.utils.XDRUnmarshaller;
import org.xtreemfs.interfaces.Lock;
import org.xtreemfs.interfaces.OSDInterface.xtreemfs_lock_checkRequest;
import org.xtreemfs.interfaces.OSDInterface.xtreemfs_lock_checkResponse;
import org.xtreemfs.interfaces.utils.ONCRPCException;
import org.xtreemfs.osd.OSDRequest;
import org.xtreemfs.osd.OSDRequestDispatcher;
import org.xtreemfs.osd.stages.PreprocStage.LockOperationCompleteCallback;

/**
 *
 * <br>15.06.2009
 */
public class LockCheckOperation extends OSDOperation {
    final int procId;

    final String sharedSecret;

    final ServiceUUID localUUID;

    public LockCheckOperation(OSDRequestDispatcher master) {
        super(master);
        procId = xtreemfs_lock_checkRequest.TAG;
        sharedSecret = master.getConfig().getCapabilitySecret();
        localUUID = master.getConfig().getUUID();
    }

    @Override
    public int getProcedureId() {
        return procId;
    }

    @Override
    public void startRequest(final OSDRequest rq) {
        final xtreemfs_lock_checkRequest args = (xtreemfs_lock_checkRequest) rq
                .getRequestArgs();

//        System.out.println("rq: " + args);

        master.getPreprocStage().checkLock(args.getClient_uuid(), args.getClient_pid(), args.getFile_id(),
                args.getOffset(), args.getLength(), args.getExclusive(), rq, new LockOperationCompleteCallback() {

            @Override
            public void parseComplete(Lock result, Exception error) {
                postAcquireLock(rq,args,result,error);
            }
        });
    }

    public void postAcquireLock(final OSDRequest rq, xtreemfs_lock_checkRequest args,
            Lock lock, Exception error) {
        if (error != null) {
            if (error instanceof ONCRPCException) {
                rq.sendException((ONCRPCException) error);
            } else {
                rq.sendInternalServerError(error);
            }
        } else {
            xtreemfs_lock_checkResponse resp = new xtreemfs_lock_checkResponse(lock);
            rq.sendSuccess(resp);
        }
    }


    @Override
    public yidl.Object parseRPCMessage(ReusableBuffer data, OSDRequest rq) throws Exception {
        xtreemfs_lock_checkRequest rpcrq = new xtreemfs_lock_checkRequest();
        rpcrq.unmarshal(new XDRUnmarshaller(data));

        rq.setFileId(rpcrq.getFile_id());
        rq.setCapability(new Capability(rpcrq.getFile_credentials().getXcap(), sharedSecret));
        rq.setLocationList(new XLocations(rpcrq.getFile_credentials().getXlocs(), localUUID));

        return rpcrq;
    }

    @Override
    public boolean requiresCapability() {
        return true;
    }

    @Override
    public void startInternalEvent(Object[] args) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}